package com.fynnix.app.core.llm

/**
 * Describes the single on-device model Fynnix ships against. A direct HTTPS URL (rather
 * than the Kaggle Models API) is required here — Kaggle-hosted MediaPipe models need an
 * authenticated API call to resolve a download link, which a background WorkManager job
 * cannot do without embedding user credentials. Most of Hugging Face's `litert-community`
 * MediaPipe-ready mirrors are gated behind an account/token (`gated: "auto"`), which the
 * same problem rules out — `Qwen2.5-1.5B-Instruct` is the one in that org confirmed
 * ungated (`gated: false`), so a plain unauthenticated GET resolves it.
 */
data class LlmModelSpec(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val fileName: String,
    val approxSizeBytes: Long,
    val minDeviceRamGb: Int,
)

val DEFAULT_LLM_MODEL = LlmModelSpec(
    id = "qwen2.5-1.5b-it-q8-ekv1280",
    displayName = "Qwen 2.5 (1.5B, on-device)",
    downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_seq128_q8_ekv1280.task",
    fileName = "qwen2.5-1.5b-it-q8-ekv1280.task",
    approxSizeBytes = 1_567_364_648L,
    minDeviceRamGb = 4,
)

/** Where [LlmModelManager] currently is with respect to [DEFAULT_LLM_MODEL]. */
sealed interface LlmModelState {
    data object NotDownloaded : LlmModelState
    data class Downloading(val progressPercent: Int) : LlmModelState
    data object Ready : LlmModelState
    data class Failed(val message: String) : LlmModelState
}
