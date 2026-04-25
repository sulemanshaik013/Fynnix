package com.fynnix.app.core.sms

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object SmsProcessingManager {

    private val _processingProgress = MutableStateFlow(ProcessingProgress())
    val processingProgress: StateFlow<ProcessingProgress> = _processingProgress.asStateFlow()

    fun startProcessing() {
        _processingProgress.value = ProcessingProgress(
            isProcessing = true,
            status = "Starting sync..."
        )
    }

    fun setStatus(status: String) {
        _processingProgress.update { it.copy(status = status) }
    }

    fun setTotalToScan(total: Int) {
        _processingProgress.update { it.copy(messagesRead = total) }
    }

    fun incScanned() {
        _processingProgress.update { it.copy(messagesScanned = it.messagesScanned + 1) }
    }

    fun incTransactional() {
        _processingProgress.update { it.copy(messagesTransactional = it.messagesTransactional + 1) }
    }

    fun incParsed() {
        _processingProgress.update { it.copy(messagesParsed = it.messagesParsed + 1) }
    }

    fun incDuplicate() {
        _processingProgress.update { it.copy(messagesDuplicate = it.messagesDuplicate + 1) }
    }

    fun incSaved() {
        _processingProgress.update { it.copy(messagesSaved = it.messagesSaved + 1) }
    }

    fun completeProcessing() {
        _processingProgress.update {
            it.copy(isProcessing = false, status = "Sync complete!")
        }
    }

    fun reset() {
        _processingProgress.value = ProcessingProgress()
    }
}

data class ProcessingProgress(
    val isProcessing: Boolean = false,
    val status: String = "Idle",
    val messagesRead: Int = 0,         // total SMS in scan window
    val messagesScanned: Int = 0,      // examined so far
    val messagesTransactional: Int = 0, // classified as transactional
    val messagesParsed: Int = 0,       // parser produced result
    val messagesDuplicate: Int = 0,    // already in DB (skipped)
    val messagesSaved: Int = 0,        // newly inserted
) {
    val progress: Float
        get() = if (messagesRead > 0) messagesScanned.toFloat() / messagesRead else 0f
}
