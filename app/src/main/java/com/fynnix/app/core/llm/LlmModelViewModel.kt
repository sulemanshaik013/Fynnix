package com.fynnix.app.core.llm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.fynnix.app.core.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Shared between the chat screen's download gate and the Settings AI section — both
 *  need the same model-state and download-progress view of the same singleton manager. */
@HiltViewModel
class LlmModelViewModel @Inject constructor(
    application: Application,
    private val modelManager: LlmModelManager,
    private val llmEngine: LlmEngine,
    private val userPreferences: UserPreferences,
) : AndroidViewModel(application) {

    val spec = modelManager.spec()
    val isDeviceCapable = modelManager.isDeviceCapable()

    val state = combine(
        modelManager.readyFlow(),
        LlmDownloadWorker.progressFlow(application),
    ) { ready, workInfos ->
        val active = workInfos.firstOrNull { !it.state.isFinished }
        when {
            ready -> LlmModelState.Ready
            active != null -> {
                val percent = active.progress.getInt(LlmDownloadWorker.KEY_PROGRESS_PERCENT, 0)
                LlmModelState.Downloading(percent)
            }
            workInfos.any { it.state == WorkInfo.State.FAILED } -> LlmModelState.Failed("Download failed — check your connection and try again.")
            else -> LlmModelState.NotDownloaded
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LlmModelState.NotDownloaded)

    val parserFallbackEnabled = combine(
        userPreferences.llmParserFallbackEnabledFlow(),
        modelManager.readyFlow(),
    ) { enabled, ready -> enabled && ready }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun startDownload(requireUnmetered: Boolean = true) {
        LlmDownloadWorker.enqueue(getApplication(), requireUnmetered)
    }

    fun cancelDownload() {
        LlmDownloadWorker.cancel(getApplication())
    }

    fun deleteModel() {
        llmEngine.release()
        modelManager.deleteModel()
    }

    fun setParserFallbackEnabled(enabled: Boolean) {
        userPreferences.setLlmParserFallbackEnabled(enabled)
    }
}
