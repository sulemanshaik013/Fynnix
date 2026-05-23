package com.fynnix.app.features.settings.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fynnix.app.core.backup.BackupRestoreUseCase
import com.fynnix.app.core.backup.CsvIo
import com.fynnix.app.core.backup.RestoreResult
import com.fynnix.app.core.data.UserPreferences
import com.fynnix.app.core.sms.SyncWorker
import com.fynnix.app.core.utils.AppConstants
import com.fynnix.app.features.transactions.data.FailedTransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Days options surfaced in the UI. `Int.MAX_VALUE` represents "all time". */
data class ScanPeriodOption(val label: String, val days: Int)

@HiltViewModel
class DataManagementViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    private val prefs: UserPreferences,
    private val csv: CsvIo,
    private val failedTransactionDao: FailedTransactionDao,
    private val backupRestore: BackupRestoreUseCase,
) : ViewModel() {

    val failedSmsCount = failedTransactionDao.countFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val periodOptions = listOf(
        ScanPeriodOption("1 Month", 30),
        ScanPeriodOption("2 Months", 60),
        ScanPeriodOption("3 Months", 90),
        ScanPeriodOption("6 Months", 180),
        ScanPeriodOption("12 Months", 365),
        ScanPeriodOption("24 Months", 730),
        ScanPeriodOption("36 Months", 1095),
        ScanPeriodOption("All time", Int.MAX_VALUE),
    )

    private val _selectedDays = MutableStateFlow(prefs.getSyncDays())
    val selectedDays: StateFlow<Int> = _selectedDays

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy

    private val _restoreWrongPassword = MutableStateFlow(false)
    val restoreWrongPassword: StateFlow<Boolean> = _restoreWrongPassword

    /** Set once a restore has actually replaced the on-disk database — the UI must force
     *  a process restart at this point, since Room's connection to it is now closed for
     *  good and nothing in this process can safely touch the database again. */
    private val _restoreCompleted = MutableStateFlow(false)
    val restoreCompleted: StateFlow<Boolean> = _restoreCompleted

    val backupPasswordMinLength = AppConstants.BACKUP_PASSWORD_MIN_LENGTH

    fun selectPeriod(days: Int) {
        prefs.setSyncDays(days)
        prefs.setLastSyncTimestamp(0L) // force a fresh full scan
        _selectedDays.value = days
        SyncWorker.scheduleManualSync(app, days)
        _status.value = "Re-scanning the last ${labelFor(days)}…"
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            runCatching { csv.exportTo(uri) }
                .onSuccess { _status.value = "Exported $it transactions" }
                .onFailure { _status.value = "Export failed: ${it.message}" }
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            runCatching { csv.importFrom(uri) }
                .onSuccess { result ->
                    _status.value = buildString {
                        append("Imported ${result.imported} transaction${if (result.imported == 1) "" else "s"}")
                        if (result.skippedExisting > 0) append(", skipped ${result.skippedExisting} already in your history")
                        if (result.invalid > 0) append(", ${result.invalid} row${if (result.invalid == 1) "" else "s"} could not be read")
                    }
                }
                .onFailure { _status.value = "Import failed: ${it.message}" }
        }
    }

    fun backup(destination: Uri, password: String) {
        viewModelScope.launch {
            _backupBusy.value = true
            backupRestore.backup(destination, password)
                .onSuccess { _status.value = "Backup saved" }
                .onFailure { _status.value = "Backup failed: ${it.message}" }
            _backupBusy.value = false
        }
    }

    /**
     * Validates the password against [source] before doing anything destructive — a wrong
     * password must never get anywhere near [restore], which starts tearing down the live
     * database as soon as it's called.
     */
    fun restore(source: Uri, password: String) {
        viewModelScope.launch {
            _backupBusy.value = true
            _restoreWrongPassword.value = false
            if (!backupRestore.validatePassword(source, password)) {
                _restoreWrongPassword.value = true
                _backupBusy.value = false
                return@launch
            }
            when (val result = backupRestore.restore(source, password)) {
                is RestoreResult.RestartRequired -> _restoreCompleted.value = true
                is RestoreResult.Failed -> _status.value = "Restore failed: ${result.message}"
            }
            _backupBusy.value = false
        }
    }

    fun clearRestoreWrongPassword() { _restoreWrongPassword.value = false }

    fun clearStatus() { _status.value = null }

    private fun labelFor(days: Int): String =
        periodOptions.firstOrNull { it.days == days }?.label ?: "${days} days"
}
