package com.fynnix.app.core.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.fynnix.app.core.database.AppDatabase
import com.fynnix.app.core.security.SecureKeyVault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RestoreResult {
    /** The restored file replaced the live database on disk — the process must now be
     *  killed and relaunched, since Room cannot hot-swap a closed database connection. */
    object RestartRequired : RestoreResult
    data class Failed(val message: String) : RestoreResult
}

/**
 * Exports the live database to a file re-encrypted with a user-chosen password (independent
 * of the Android Keystore key that normally protects it), and restores from one.
 *
 * The live DB's real passphrase is Keystore-derived and device-bound — a backup encrypted
 * with it would be unopenable after a factory reset or on a new device, which is exactly the
 * scenario a backup exists to survive. Re-keying to a password only the user holds is what
 * makes the backup actually portable; it's also why that password can't be recovered by
 * Fynnix if forgotten — nothing about it is ever stored.
 */
@Singleton
class BackupRestoreUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val keyVault: SecureKeyVault,
) {
    /**
     * Re-encrypts a full copy of the live DB with [password] into a temp file, then streams
     * it to [destination]. Runs against the already-open Room connection via SQLCipher's
     * `sqlcipher_export`, so the live DB is never closed or interrupted.
     */
    suspend fun backup(destination: Uri, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "fynnix-backup-export.db")
        runCatching {
            tempFile.delete()
            val db = appDatabase.openHelper.writableDatabase
            db.execSQL("ATTACH DATABASE ? AS backupdb KEY ?", arrayOf(tempFile.absolutePath, password))
            try {
                // sqlcipher_export() is invoked as a SELECT, not a statement — execSQL()
                // rejects anything that returns rows, so this must go through query().
                db.query("SELECT sqlcipher_export('backupdb')").use { it.moveToFirst() }
            } finally {
                db.execSQL("DETACH DATABASE backupdb")
            }

            context.contentResolver.openOutputStream(destination, "wt")?.use { out ->
                tempFile.inputStream().use { it.copyTo(out) }
            } ?: error("Could not open the chosen file for writing")
            Unit
        }.onFailure {
            Log.e(TAG, "Backup failed", it)
        }.also {
            tempFile.delete()
        }
    }

    /**
     * Opens [source] read-only with [password] to confirm it's a genuine, correctly-keyed
     * backup before [restore] is allowed to touch the live database at all.
     */
    suspend fun validatePassword(source: Uri, password: String): Boolean = withContext(Dispatchers.IO) {
        val stagedFile = stageToCache(source, "fynnix-restore-check.db") ?: return@withContext false
        try {
            canOpenWithPassword(stagedFile, password)
        } finally {
            stagedFile.delete()
        }
    }

    private fun canOpenWithPassword(file: File, password: String): Boolean = try {
        SQLiteDatabase.openDatabase(file.absolutePath, password, null, SQLiteDatabase.OPEN_READONLY).close()
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Replaces the live database file with [source]'s contents, re-keyed back to the app's
     * own Keystore passphrase. The current live DB is snapshotted first and restored on any
     * failure — a bad restore must never leave the app with no usable database at all.
     *
     * Re-checks the password itself before touching anything live, even though callers are
     * expected to have already called [validatePassword] — the live DB gets closed as the
     * very next step, which the caller can't undo, so this can't be a contract enforced by
     * caller discipline alone.
     */
    suspend fun restore(source: Uri, password: String): RestoreResult = withContext(Dispatchers.IO) {
        val restoreFile = stageToCache(source, "fynnix-restore-import.db")
            ?: return@withContext RestoreResult.Failed("Could not read the selected file")

        if (!canOpenWithPassword(restoreFile, password)) {
            restoreFile.delete()
            return@withContext RestoreResult.Failed("Incorrect password")
        }

        val liveDbFile = context.getDatabasePath(AppDatabase.DB_NAME)
        val safetySnapshot = File(context.filesDir, "fynnix-pre-restore-safety.db")

        try {
            snapshotLiveDb(liveDbFile, safetySnapshot)

            // Room must release its connection before the underlying file can be deleted
            // and replaced — a held connection would keep the old file (or its -wal/-shm
            // siblings) locked, and the app can't safely resume using this AppDatabase
            // instance afterward regardless, so closing it is not optional here.
            appDatabase.close()
            deleteDbFiles(liveDbFile)

            val passphrase = keyVault.getOrCreateDbPassphrase()
            try {
                // Pre-create the live DB via the raw-byte-key overload — the same mechanism
                // SupportFactory uses elsewhere — rather than having ATTACH create it with a
                // literal raw-key expression; SQLCipher's ATTACH does not reliably create a
                // brand-new file when the KEY clause is a raw key rather than a text
                // passphrase, even though opening an *existing* raw-keyed file works fine.
                val liveDb = SQLiteDatabase.openOrCreateDatabase(liveDbFile.absolutePath, passphrase, null)
                try {
                    liveDb.execSQL(
                        "ATTACH DATABASE ? AS restoresrc KEY ?",
                        arrayOf(restoreFile.absolutePath, password),
                    )
                    try {
                        // Pulls data in (target 'main', source 'restoresrc') rather than the
                        // single-arg push-out form, since the empty just-created live DB must
                        // be the target here, not the source.
                        liveDb.rawQuery("SELECT sqlcipher_export('main', 'restoresrc')", null).use { it.moveToFirst() }
                    } finally {
                        liveDb.execSQL("DETACH DATABASE restoresrc")
                    }
                } finally {
                    liveDb.close()
                }
            } finally {
                java.util.Arrays.fill(passphrase, 0)
            }

            safetySnapshot.delete()
            RestoreResult.RestartRequired
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed, rolling back to pre-restore snapshot", e)
            runCatching {
                deleteDbFiles(liveDbFile)
                if (safetySnapshot.exists()) copyFile(safetySnapshot, liveDbFile)
            }.onFailure { Log.e(TAG, "Rollback after failed restore also failed", it) }
            RestoreResult.Failed(e.message ?: "Restore failed")
        } finally {
            restoreFile.delete()
        }
    }

    private fun stageToCache(source: Uri, fileName: String): File? {
        val staged = File(context.cacheDir, fileName)
        staged.delete()
        return try {
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(staged).use { output -> input.copyTo(output) }
            } ?: return null
            staged
        } catch (e: Exception) {
            Log.e(TAG, "Could not stage file for backup/restore", e)
            null
        }
    }

    private fun snapshotLiveDb(liveDbFile: File, destination: File) {
        destination.delete()
        if (liveDbFile.exists()) copyFile(liveDbFile, destination)
    }

    private fun copyFile(from: File, to: File) {
        from.inputStream().use { input -> FileOutputStream(to).use { output -> input.copyTo(output) } }
    }

    /** Deletes the DB file and its WAL-mode siblings — leaving any of them behind would let
     *  a stale -wal file silently reintroduce old data on the next open. */
    private fun deleteDbFiles(liveDbFile: File) {
        liveDbFile.delete()
        File(liveDbFile.path + "-wal").delete()
        File(liveDbFile.path + "-shm").delete()
        File(liveDbFile.path + "-journal").delete()
    }

    private companion object {
        const val TAG = "BackupRestoreUseCase"
    }
}
