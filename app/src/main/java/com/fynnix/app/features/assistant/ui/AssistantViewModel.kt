package com.fynnix.app.features.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fynnix.app.features.assistant.AssistantIntentRouter
import com.fynnix.app.features.assistant.AssistantQueryExecutor
import com.fynnix.app.features.assistant.AssistantResponder
import com.fynnix.app.features.assistant.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Chat history is in-memory only, cleared on process death — v1 is a stateless "ask a
 * question, get an answer" tool, not a saved conversation, so there's nothing here worth
 * the complexity of a persisted table yet.
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val router: AssistantIntentRouter,
    private val executor: AssistantQueryExecutor,
    private val responder: AssistantResponder,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isThinking.value) return

        appendMessage(trimmed, isUser = true)
        _isThinking.value = true

        viewModelScope.launch {
            val intent = router.route(trimmed)
            val facts = executor.execute(intent)
            val answer = responder.respond(facts)
            appendMessage(answer, isUser = false)
            _isThinking.value = false
        }
    }

    private fun appendMessage(text: String, isUser: Boolean) {
        _messages.update {
            it + ChatMessage(
                id = "${System.currentTimeMillis()}-${it.size}",
                text = text,
                isUser = isUser,
                timestamp = System.currentTimeMillis(),
            )
        }
    }
}
