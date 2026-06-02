package com.example.hybrid_ai_app.coach.presentation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.coach.data.CoachRepository
import com.example.hybrid_ai_app.coach.data.presentation.ChatMessage
import com.example.hybrid_ai_app.coach.data.presentation.MessageSender
import com.example.hybrid_ai_app.core.data.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val coachRepository: CoachRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val messages = mutableStateListOf<ChatMessage>()
    val localProfilePicPath = preferencesManager.userProfilePicFlow

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Initialize UI with a friendly greeting
        messages.add(
            ChatMessage(
                id = "welcome",
                text = "¡Hola! Soy tu Asistente Personal de entrenamiento. ¿Tienes alguna duda con los RPE o necesitas adaptar algún ejercicio?",
                sender = MessageSender.COACH
            )
        )
    }

    fun sendUserMessage(text: String) {
        if (text.isBlank()) return

        //Append user message to UI immediately
        val userMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            text = text,
            sender = MessageSender.USER
        )
        messages.add(userMessage)

        //Trigger asynchronous call to backend proxy
        viewModelScope.launch {
            _isLoading.value = true
            val response = coachRepository.sendMessage(text)
            _isLoading.value = false

            if (!response.isNullOrBlank()) {
                messages.add(
                    ChatMessage(
                        id = System.currentTimeMillis().toString(),
                        text = response,
                        sender = MessageSender.COACH
                    )
                )
            } else {
                messages.add(
                    ChatMessage(
                        id = System.currentTimeMillis().toString(),
                        text = "Lo siento, he tenido un problema de conexión. ¿Podrías repetir eso?",
                        sender = MessageSender.COACH
                    )
                )
            }
        }
    }
}