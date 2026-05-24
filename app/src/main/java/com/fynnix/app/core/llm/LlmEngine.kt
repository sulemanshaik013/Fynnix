package com.fynnix.app.core.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around MediaPipe's on-device LLM runtime. Every caller (chat routing, chat
 * phrasing, parser-fallback extraction) needs an independent, stateless single-turn
 * completion — never a running conversation — so [generate] opens a fresh
 * [LlmInferenceSession] per call against one lazily-created, reused [LlmInference] engine
 * (the engine itself is what actually holds the multi-GB model in memory, so it is only
 * created once and kept for the process lifetime).
 *
 * [LlmInference] is not thread-safe for concurrent generateResponse calls, so every call is
 * serialized through [mutex] rather than left to race — a second caller waits instead of
 * corrupting the first's in-flight generation.
 */
@Singleton
class LlmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: LlmModelManager,
) {
    private val mutex = Mutex()
    private var engine: LlmInference? = null

    /** Returns null if the model isn't downloaded/ready, or if inference fails for any
     *  reason — callers must treat null as "fall back to the deterministic path," never
     *  surface a raw exception to the user. */
    suspend fun generate(prompt: String, temperature: Float = DEFAULT_TEMPERATURE): String? =
        withContext(Dispatchers.Default) {
            if (!modelManager.isReady()) return@withContext null
            mutex.withLock {
                try {
                    val llm = engineOrCreate() ?: return@withLock null
                    val sessionOptions = LlmInferenceSessionOptions.builder()
                        .setTemperature(temperature)
                        .setTopK(DEFAULT_TOP_K)
                        .build()
                    LlmInferenceSession.createFromOptions(llm, sessionOptions).use { session ->
                        session.addQueryChunk(prompt)
                        session.generateResponse()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "LLM generation failed", e)
                    null
                }
            }
        }

    private fun engineOrCreate(): LlmInference? {
        engine?.let { return it }
        return try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelManager.modelFile().absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .build()
            LlmInference.createFromOptions(context, options).also { engine = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LLM engine", e)
            null
        }
    }

    /** Releases the loaded model from memory — call when the user disables/deletes the
     *  model, not on every screen exit, since reloading a multi-GB model is expensive. */
    fun release() {
        engine?.close()
        engine = null
    }

    private companion object {
        const val TAG = "LlmEngine"
        const val MAX_TOKENS = 1024
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_TEMPERATURE = 0.2f
    }
}
