package com.example.hybrid_ai_app.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.core.data.EntitlementManager
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.core.data.local.entity.LoggedExerciseEntity
import com.example.hybrid_ai_app.core.data.local.entity.UserProgressEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import com.example.hybrid_ai_app.core.data.remote.dto.DayDto
import com.example.hybrid_ai_app.core.data.remote.dto.WeekDto
import com.example.hybrid_ai_app.core.domain.model.CompletedRun
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import com.example.hybrid_ai_app.core.domain.model.RunPoint
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import com.example.hybrid_ai_app.core.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface HomeUiState {
    object Loading : HomeUiState
    object Empty : HomeUiState
    data class Success(
        val plan: WorkoutPlanEntity,
        val currentWeek: WeekDto,
        val currentDay: DayDto?,
        val weeklyCompletion: List<Boolean>,
        val currentWeekNumber: Int,
        val currentDayIndex: Int,
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

/** The outcome of finishing a tracked run, which the run screen waits for before navigating. */
sealed interface RunEvent {
    data object Saved : RunEvent
    data object SaveFailed : RunEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: WorkoutPlanRepository,
    private val entitlementManager: EntitlementManager,
    private val preferencesManager: PreferencesManager,
    private val time: TimeProvider,
) : ViewModel() {

    val localProfilePicPath = preferencesManager.userProfilePicFlow

    /** Drives the trial countdown banner. */
    val entitlement = entitlementManager.entitlement

    /** Non-null while the paywall bottom sheet should be shown. */
    private val _premiumPrompt = MutableStateFlow<PremiumRequiredReason?>(null)
    val premiumPrompt: StateFlow<PremiumRequiredReason?> = _premiumPrompt.asStateFlow()

    fun dismissPremiumPrompt() {
        _premiumPrompt.value = null
    }

    private val runEventChannel = Channel<RunEvent>(Channel.BUFFERED)
    val runEvents: Flow<RunEvent> = runEventChannel.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = repository.getActivePlan()
        .flatMapLatest { plan ->
            if (plan == null) {
                flowOf(HomeUiState.Empty)
            } else {
                repository.getUserProgress().flatMapLatest { progress ->
                    val weekNum = progress?.currentWeekNumber ?: 1

                    repository.getLogsForWeek(weekNum).map { logs ->
                        // Safe extraction using the real DTO types embedded in your Entity
                        val weekData = plan.weeks.find { it.weekNumber == weekNum } ?: plan.weeks.firstOrNull()
                        val dayIdx = progress?.currentDayIndex ?: 0
                        val dayData = weekData?.days?.getOrNull(dayIdx)

                        val completionList = MutableList(7) { false }
                        logs.forEach { log ->
                            if (log.dayIndex in 0..6) completionList[log.dayIndex] = log.isCompleted
                        }

                        if (weekData != null) {
                            HomeUiState.Success(
                                plan = plan,
                                currentWeek = weekData,
                                currentDay = dayData,
                                weeklyCompletion = completionList,
                                currentWeekNumber = weekNum,
                                currentDayIndex = dayIdx,
                            )
                        } else {
                            HomeUiState.Empty
                        }
                    }
                }
            }
        }
        .catch { exception -> emit(HomeUiState.Error(exception.message ?: "SSOT mapping error")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading,
        )

    fun logCurrentWorkoutAsCompleted(metrics: List<LoggedExerciseEntity> = emptyList()) {
        // Read-only after the trial expires. The client must enforce this, not just the backend:
        // logging writes to Room first and syncs in a detached, fire-and-forget coroutine, so a
        // 402 would be swallowed into a log line and the user would believe the session saved.
        // Shared by all three call sites (HomeScreen's two buttons and WorkoutExecutionScreen).
        if (entitlementManager.entitlement.value.isReadOnly) {
            _premiumPrompt.value = PremiumRequiredReason.TRIAL_EXPIRED
            return
        }

        val currentState = uiState.value
        if (currentState is HomeUiState.Success) {
            viewModelScope.launch {
                val log = WorkoutLogEntity(
                    weekNumber = currentState.currentWeekNumber,
                    dayIndex = currentState.currentDayIndex,
                    timestamp = System.currentTimeMillis(),
                    isCompleted = true,
                    loggedExercises = metrics,
                    // The idempotency key the backend upserts on. The title and type are stamped
                    // by the repository from the day this log is for.
                    clientId = UUID.randomUUID().toString(),
                )

                val isLastDayOfWeek = currentState.currentDayIndex == 6
                val nextWeek = if (isLastDayOfWeek) currentState.currentWeekNumber + 1 else currentState.currentWeekNumber
                val nextDay = if (isLastDayOfWeek) 0 else currentState.currentDayIndex + 1

                val updatedProgress = UserProgressEntity(
                    userId = "active_plan",
                    currentWeekNumber = nextWeek,
                    currentDayIndex = nextDay,
                )

                // Pass context to the repository to route the network request
                val workoutType = currentState.currentDay?.workoutType ?: "rest"
                val dayName = currentState.currentDay?.dayName ?: "Workout"

                repository.completeWorkout(log, updatedProgress, workoutType, dayName)
            }
        }
    }

    /**
     * Saves a tracked run against the day the screen was opened for. Unlike
     * [logCurrentWorkoutAsCompleted] it never uses the progress pointer: the quick-start sheet
     * opens any pending day, and logging against the pointer filed runs under other days.
     */
    fun finishRun(
        weekNumber: Int,
        dayIndex: Int,
        durationSec: Long,
        distanceKm: Double,
        path: List<RunPoint>,
    ) {
        // Same read-only guard as above: the screen stays put, so the paywall sheet stays visible.
        if (entitlementManager.entitlement.value.isReadOnly) {
            _premiumPrompt.value = PremiumRequiredReason.TRIAL_EXPIRED
            return
        }

        val day = (uiState.value as? HomeUiState.Success)?.plan?.weeks
            ?.find { it.weekNumber == weekNumber }?.days?.getOrNull(dayIndex)
        if (day == null) {
            runEventChannel.trySend(RunEvent.SaveFailed)
            return
        }

        viewModelScope.launch {
            val run = CompletedRun(
                weekNumber = weekNumber,
                dayIndex = dayIndex,
                title = day.dayName,
                // A run has no weight to log; the day's own instruction is what gets recorded.
                instruction = day.exercises.map { exercise ->
                    LoggedExerciseEntity(
                        name = exercise.name,
                        sets = exercise.sets,
                        reps = exercise.reps,
                        weight = "",
                        rpe = exercise.rpe,
                    )
                },
                finishedAt = time.nowMillis(),
                durationSec = durationSec,
                distanceKm = distanceKm,
                path = path,
            )

            repository.completeRun(run)
                .onSuccess { runEventChannel.send(RunEvent.Saved) }
                .onFailure { runEventChannel.send(RunEvent.SaveFailed) }
        }
    }

    fun toggleWorkoutCompletion(weekNumber: Int, dayIndex: Int) {
        viewModelScope.launch {
            try {
                // Update the local database
                repository.toggleDayStatus(weekNumber, dayIndex)
            } catch (e: Exception) {
                // Handle error (e.g., emit an error state)
            }
        }
    }
}
