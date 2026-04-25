package com.fynnix.app.features.settings.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fynnix.app.core.backup.CsvIo
import com.fynnix.app.core.data.UserPreferences
import com.fynnix.app.core.sms.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Days options surfaced in the UI. `Int.MAX_VALUE` represents "all time". */
data class ScanPeriodOption(val label: String, val days: Int)

@HiltViewModel
class DataManagementViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    private val prefs: UserPreferences,
    private val csv: CsvIo,
) : ViewModel() {

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
                .onSuccess { _status.value = "Imported $it transactions" }
                .onFailure { _status.value = "Import failed: ${it.message}" }
        }
    }

    fun clearStatus() { _status.value = null }

    private fun labelFor(days: Int): String =
        periodOptions.firstOrNull { it.days == days }?.label ?: "${days} days"
}
