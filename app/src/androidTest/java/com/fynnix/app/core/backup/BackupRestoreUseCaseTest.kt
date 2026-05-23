package com.fynnix.app.core.backup

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fynnix.app.core.database.ALL_MIGRATIONS
import com.fynnix.app.core.database.AppDatabase
import com.fynnix.app.core.security.SecureKeyVault
import com.fynnix.app.core.utils.TransactionSource
import com.fynnix.app.core.utils.TransactionType
import com.fynnix.app.features.categories.data.CategoryEntity
import com.fynnix.app.features.transactions.data.TransactionEntity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * Exercises the real backup/restore round trip against the app's actual "fynnix.db" file and
 * the same Hilt-provided [AppDatabase] singleton the app itself uses — not a second Room
 * instance manually pointed at the same file. [com.fynnix.app.FynnixApp.onCreate] already
 * opens the real singleton at process startup (via its DB seeder), so a second competing
 * connection to the same file corrupts state; going through the real singleton via
 * [BackupTestEntryPoint] is both more faithful to production usage and avoids that entirely.
 */
// Order matters: backupThenRestoreRoundTrip closes the real Hilt AppDatabase singleton for
// the rest of this process (restore() requires that, matching production "must restart"
// behavior) — it must run last, or every test after it would see an already-closed db.
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class BackupRestoreUseCaseTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val entryPoint = EntryPointAccessors.fromApplication(context, BackupTestEntryPoint::class.java)
    private val keyVault = entryPoint.secureKeyVault()
    private val backupFile = File(context.cacheDir, "test-export.db")

    /** Reopens the live DB the same way the app does after a restart — used only to verify
     *  post-restore state, once [BackupRestoreUseCase.restore] has already closed the real
     *  Hilt singleton for good. */
    private fun reopenLiveDbForVerification(): AppDatabase {
        SQLiteDatabase.loadLibs(context)
        val passphrase = keyVault.getOrCreateDbPassphrase()
        val factory = SupportFactory(passphrase)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .openHelperFactory(factory)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    private fun cleanupBackupFile() {
        backupFile.delete()
    }

    @Test
    fun zBackupThenRestoreRoundTrip() = runBlocking {
        val db = entryPoint.appDatabase()
        val useCase = entryPoint.backupRestoreUseCase()

        // 1. Seed the live DB with a row that must survive the round trip.
        val catId = db.categoryDao().insert(CategoryEntity(name = "Test Cat A", icon = "help", colorHex = "#000000", isDefault = false, sortOrder = 0))
        db.transactionDao().insertIgnore(
            TransactionEntity(
                id = "keep-me", amount = 100.0, type = TransactionType.DEBIT, merchant = "Keep Me",
                categoryId = catId, dateTime = System.currentTimeMillis(), source = TransactionSource.MANUAL,
            )
        )

        // 2. Back it up with a chosen password.
        val backupUri = Uri.fromFile(backupFile)
        val backupResult = useCase.backup(backupUri, "correct horse battery")
        assertTrue("backup() should succeed: ${backupResult.exceptionOrNull()}", backupResult.isSuccess)
        assertTrue("exported file should exist", backupFile.exists())

        // 3. Add a second row AFTER the backup — this one must NOT survive the restore,
        // proving restore actually replaces data rather than merging or no-op'ing.
        db.transactionDao().insertIgnore(
            TransactionEntity(
                id = "drop-me", amount = 50.0, type = TransactionType.DEBIT, merchant = "Drop Me",
                categoryId = catId, dateTime = System.currentTimeMillis(), source = TransactionSource.MANUAL,
            )
        )
        assertNotNull(db.transactionDao().getById("drop-me"))

        // 4. Wrong password must be rejected without touching the live DB.
        assertFalse(useCase.validatePassword(backupUri, "wrong password"))
        assertTrue(useCase.validatePassword(backupUri, "correct horse battery"))

        // 5. Restore — this closes the real singleton `db` internally.
        val restoreResult = useCase.restore(backupUri, "correct horse battery")
        assertTrue("restore() should require a restart on success: $restoreResult", restoreResult is RestoreResult.RestartRequired)

        // 6. A fresh AppDatabase instance, opened the same way the app does on next launch,
        // must see the pre-backup state — not what was added afterward.
        val reopened = reopenLiveDbForVerification()
        assertNotNull("row present at backup time must survive restore", reopened.transactionDao().getById("keep-me"))
        assertNull("row added after backup must not survive restore", reopened.transactionDao().getById("drop-me"))
        reopened.close()
        cleanupBackupFile()
    }

    @Test
    fun aRestoreWithWrongPasswordLeavesLiveDataUntouched() = runBlocking {
        val db = entryPoint.appDatabase()
        val useCase = entryPoint.backupRestoreUseCase()

        val catId = db.categoryDao().insert(CategoryEntity(name = "Test Cat B", icon = "help", colorHex = "#000000", isDefault = false, sortOrder = 0))
        db.transactionDao().insertIgnore(
            TransactionEntity(
                id = "untouched", amount = 10.0, type = TransactionType.DEBIT, merchant = "Untouched",
                categoryId = catId, dateTime = System.currentTimeMillis(), source = TransactionSource.MANUAL,
            )
        )
        val backupUri = Uri.fromFile(backupFile)
        assertTrue(useCase.backup(backupUri, "right-password").isSuccess)

        assertFalse(useCase.validatePassword(backupUri, "totally-wrong"))
        // restore() re-checks the password itself before touching the live DB at all, even
        // though a well-behaved caller would have already called validatePassword() — so
        // calling it directly with a bad password must still fail closed without requiring
        // a restart, unlike every other failure path past that point.
        val result = useCase.restore(backupUri, "totally-wrong")
        assertTrue(result is RestoreResult.Failed)

        assertNotNull(
            "live DB must remain open and untouched after a wrong-password restore attempt",
            db.transactionDao().getById("untouched"),
        )
        cleanupBackupFile()
    }
}
