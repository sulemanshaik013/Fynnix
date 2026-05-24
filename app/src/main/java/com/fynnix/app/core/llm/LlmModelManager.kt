package com.fynnix.app.core.llm

import android.content.Context
import com.fynnix.app.core.data.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for where the on-device model file lives and whether it's usable.
 * "Ready" means [UserPreferences.isLlmModelReady] was set after a full, size-verified write
 * — a file that merely exists could be a partial download left behind by a killed process.
 */
@Singleton
class LlmModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
) {
    private val spec: LlmModelSpec get() = DEFAULT_LLM_MODEL
    private val modelsDir: File get() = File(context.filesDir, "models").apply { mkdirs() }

    fun modelFile(): File = File(modelsDir, spec.fileName)

    fun isDeviceCapable(): Boolean = LlmCapability.isSupported(context, spec)

    fun isReady(): Boolean = userPreferences.isLlmModelReady() && modelFile().exists()

    fun readyFlow(): Flow<Boolean> = userPreferences.llmModelReadyFlow()
        .map { it && modelFile().exists() }

    /** Called by [LlmDownloadWorker] once the file is fully written and its size matches
     *  what the server reported — never on a partial/interrupted write. */
    fun markReady() { userPreferences.setLlmModelReady(true) }

    fun deleteModel() {
        userPreferences.setLlmModelReady(false)
        runCatching { modelFile().delete() }
    }

    fun spec(): LlmModelSpec = spec
}
