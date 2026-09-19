package com.example.hybrid_ai_app.coach.presentation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.coach.data.ChatMessageDto
import com.example.hybrid_ai_app.coach.data.CoachRepository
import com.example.hybrid_ai_app.coach.data.PlanContextFormatter
import com.example.hybrid_ai_app.coach.data.presentation.ChatMessage
import com.example.hybrid_ai_app.coach.data.presentation.MessageSender
import com.example.hybrid_ai_app.core.data.EntitlementManager
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import com.example.hybrid_ai_app.core.domain.model.Entitlement
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredException
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val coachRepository: CoachRepository,
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val entitlementManager: EntitlementManager,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    val messages = mutableStateListOf<ChatMessage>()
    val localProfilePicPath = preferencesManager.userProfilePicFlow

    /** Drives the "1 of 2 messages today" chip and whether the input is enabled. */
    val entitlement: StateFlow<Entitlement> = entitlementManager.entitlement

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Non-null while the paywall bottom sheet should be shown. */
    private val _premiumPrompt = MutableStateFlow<PremiumRequiredReason?>(null)
    val premiumPrompt: StateFlow<PremiumRequiredReason?> = _premiumPrompt.asStateFlow()

    // Latest active plan, kept in sync so we can attach it as context on every message
    private var activePlan: WorkoutPlanEntity? = null

    init {
        viewModelScope.launch {
            workoutPlanRepository.getActivePlan().collect { plan -> activePlan = plan }
        }

        // The quota resets at UTC midnight and can be spent from another device, so refresh on
        // entry rather than trusting whatever the cache holds.
        viewModelScope.launch { entitlementManager.refresh() }

        // Greet immediately so the screen is never blank, then restore the real conversation
        // underneath it once the network answers.
        messages.add(
            ChatMessage(
                id = WELCOME_ID,
                text = "¡Hola! Soy tu Asistente Personal de entrenamiento. ¿Tienes alguna duda con los RPE o necesitas adaptar algún ejercicio?",
                sender = MessageSender.COACH,
            ),
        )

        // The backend owns the transcript, so the conversation survives leaving this screen — but
        // the ViewModel dies with the nav back-stack entry, so it has to ask for it again on every
        // entry. A failure (offline, or a backend that predates the endpoint) is not worth showing:
        // the greeting already on screen is exactly the old behaviour.
        viewModelScope.launch {
            val restored = coachRepository.loadHistory().getOrDefault(emptyList())
            if (restored.isEmpty()) return@launch

            // A restored conversation replaces the greeting, which only makes sense as the opening
            // line of a new one.
            messages.removeAll { it.id == WELCOME_ID }
            // Inserted at the front rather than appended: the user may already have typed while
            // this was in flight, and their turn belongs after the transcript, not before it.
            messages.addAll(
                0,
                restored.mapIndexed { index, turn ->
                    ChatMessage(
                        id = "$HISTORY_ID_PREFIX$index",
                        text = turn.content,
                        sender = if (turn.role == "user") MessageSender.USER else MessageSender.COACH,
                    )
                },
            )
        }
    }

    private companion object {
        const val WELCOME_ID = "welcome"
        const val HISTORY_ID_PREFIX = "history-"
    }

    fun dismissPremiumPrompt() {
        _premiumPrompt.value = null
    }

    /** Opens the paywall sheet with the copy that matches why the user is blocked. */
    fun showPremiumPrompt() {
        val current = entitlement.value
        _premiumPrompt.value = if (current.isReadOnly) {
            PremiumRequiredReason.TRIAL_EXPIRED
        } else {
            PremiumRequiredReason.CHAT_QUOTA_EXCEEDED
        }
    }

    fun sendUserMessage(text: String) {
        if (text.isBlank()) return

        // Cheap local guard so we do not spend a round-trip on a request the server will reject.
        // The server still enforces the quota; this only saves latency.
        if (!entitlement.value.canSendChatMessage) {
            showPremiumPrompt()
            return
        }

        // Snapshot the prior conversation BEFORE adding the new turn (skip the canned greeting).
        // The server ignores this field — it rebuilds the window from its own transcript — but it
        // stays on the wire until both sides ship the removal together.
        val history = messages
            .filter { it.id != WELCOME_ID }
            .map { msg ->
                ChatMessageDto(
                    role = if (msg.sender == MessageSender.USER) "user" else "model",
                    content = msg.text,
                )
            }

        // Append user message to UI immediately
        val userMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            text = text,
            sender = MessageSender.USER,
        )
        messages.add(userMessage)

        // Trigger asynchronous call to backend proxy
        viewModelScope.launch {
            _isLoading.value = true
            val planContext = PlanContextFormatter.format(activePlan)
            val result = coachRepository.sendMessage(text, planContext, history)
            _isLoading.value = false

            result
                .onSuccess { reply ->
                    messages.add(
                        ChatMessage(
                            id = System.currentTimeMillis().toString(),
                            text = reply,
                            sender = MessageSender.COACH,
                        ),
                    )
                    // The message just sent counts against today's quota.
                    entitlementManager.refresh()
                }
                .onFailure { error ->
                    if (error is PremiumRequiredException) {
                        // The server rejected the message, so it was never delivered: take the
                        // optimistic bubble back out rather than leaving a phantom turn.
                        messages.remove(userMessage)
                        // Our cached counter was stale — the server is authoritative.
                        entitlementManager.refresh()
                        _premiumPrompt.value = error.reason
                    } else {
                        messages.add(
                            ChatMessage(
                                id = System.currentTimeMillis().toString(),
                                text = "Lo siento, he tenido un problema de conexión. ¿Podrías repetir eso?",
                                sender = MessageSender.COACH,
                            ),
                        )
                    }
                }
        }
    }
}
