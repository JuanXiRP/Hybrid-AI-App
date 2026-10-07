package com.example.hybrid_ai_app.home.presentation.run

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.core.data.EntitlementManager
import com.example.hybrid_ai_app.core.data.local.entity.LoggedExerciseEntity
import com.example.hybrid_ai_app.core.domain.model.ActiveRun
import com.example.hybrid_ai_app.core.domain.model.CompletedRun
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import com.example.hybrid_ai_app.core.domain.model.RunStructure
import com.example.hybrid_ai_app.core.domain.model.distanceKm
import com.example.hybrid_ai_app.core.domain.repository.ActiveRunRepository
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import com.example.hybrid_ai_app.core.util.IdProvider
import com.example.hybrid_ai_app.core.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RunSessionUiState {
    data object Loading : RunSessionUiState

    /** No run is in progress: the setup screen comes first. */
    data object Setup : RunSessionUiState

    /** The run this screen is for is in progress, restored as stored. */
    data class Running(val run: ActiveRun) : RunSessionUiState

    /** A run for another day (or an extra one) is in progress; the athlete chooses which continues. */
    data class Conflict(val existing: ActiveRun) : RunSessionUiState
}

/** One-shot signals the run screen reacts to once. */
sealed interface RunSessionEvent {
    /** The run was saved or discarded, and the screen should close. */
    data object Finished : RunSessionEvent

    /** The local write failed; the run is still in progress. */
    data object SaveFailed : RunSessionEvent

    data class ShowPaywall(val reason: PremiumRequiredReason) : RunSessionEvent

    /** Continue the other run: open its own screen. */
    data class OpenRun(val run: ActiveRun) : RunSessionEvent
}

/**
 * Owns the run of one screen: a plan day (`workout_execution/{weekNumber}/{dayIndex}`) or an extra
 * run (`extra_workout/run`).
 *
 * The run itself is stored ([ActiveRunRepository]), never held here, which is what makes the screen
 * safe to leave and to recreate: arriving at a screen whose run is already in progress shows it as it
 * is instead of starting it again. This ViewModel only writes the run's state (start, pause, resume,
 * discard, finish); `LocationTrackingService` observes the same row and does the tracking.
 *
 * Starting and finishing check the entitlement, as every other write does: once the trial has
 * ended the screen shows the paywall instead.
 */
@HiltViewModel
class RunSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val activeRun: ActiveRunRepository,
    private val plans: WorkoutPlanRepository,
    private val entitlementManager: EntitlementManager,
    private val time: TimeProvider,
    private val ids: IdProvider,
) : ViewModel() {

    private val isExtra: Boolean = savedStateHandle.get<String>(KEY_KIND) == KIND_RUN
    private val weekNumber: Int = savedStateHandle.get<Int>(KEY_WEEK) ?: 0
    private val dayIndex: Int = savedStateHandle.get<Int>(KEY_DAY) ?: 0

    /**
     * True from Finish or Discard until the screen closes. Without it, the moment the run is
     * cleared the screen would flash back to the setup before it navigates away.
     */
    private val closing = MutableStateFlow(false)

    private val eventChannel = Channel<RunSessionEvent>(Channel.BUFFERED)
    val events: Flow<RunSessionEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<RunSessionUiState> = combine(activeRun.observe(), closing) { run, isClosing ->
        when {
            isClosing -> RunSessionUiState.Loading
            run == null -> RunSessionUiState.Setup
            run.isFor(weekNumber, dayIndex, isExtra) -> RunSessionUiState.Running(run)
            else -> RunSessionUiState.Conflict(run)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RunSessionUiState.Loading,
    )

    /**
     * Starts this screen's run with [structure] (null for a free run). An extra run is stamped
     * with the plan's "today", the week and day the athlete is on.
     */
    fun start(structure: RunStructure?) {
        if (!allowWrite()) return
        viewModelScope.launch {
            val now = time.nowMillis()
            val (week, day, title) = if (isExtra) {
                val progress = plans.getUserProgress().first()
                Triple(progress?.currentWeekNumber ?: 1, progress?.currentDayIndex ?: 0, "")
            } else {
                Triple(weekNumber, dayIndex, planDayName().orEmpty())
            }
            activeRun.start(
                ActiveRun(
                    clientId = ids.newId(),
                    weekNumber = week,
                    dayIndex = day,
                    isExtra = isExtra,
                    title = title,
                    structure = structure,
                    startedAt = now,
                    accumulatedMs = 0,
                    resumedAt = now,
                ),
            )
        }
    }

    fun pause() {
        val run = running() ?: return
        if (run.isPaused) return
        viewModelScope.launch { activeRun.pause(run.elapsedMs(time.nowMillis())) }
    }

    fun resume() {
        val run = running() ?: return
        if (!run.isPaused) return
        viewModelScope.launch { activeRun.resume(time.nowMillis()) }
    }

    /**
     * Saves the run as a finished workout, with the time and the path as stored, and clears it. The
     * screen closes only once the local write has finished.
     */
    fun finish() {
        val run = running() ?: return
        if (!allowWrite()) return
        closing.value = true

        viewModelScope.launch {
            val now = time.nowMillis()
            val path = activeRun.points()
            val completed = CompletedRun(
                clientId = run.clientId,
                weekNumber = run.weekNumber,
                dayIndex = run.dayIndex,
                title = run.title,
                instruction = if (run.isExtra) emptyList() else planInstruction(),
                finishedAt = now,
                durationSec = run.elapsedMs(now) / MILLIS_PER_SECOND,
                distanceKm = path.distanceKm(),
                path = path,
                isExtra = run.isExtra,
            )

            plans.completeRun(completed)
                .onSuccess { eventChannel.send(RunSessionEvent.Finished) }
                .onFailure {
                    closing.value = false
                    eventChannel.send(RunSessionEvent.SaveFailed)
                }
        }
    }

    /**
     * Throws the run away. Allowed after the trial has ended, like discarding a strength session:
     * it removes a draft, and without it a run started before the trial lapsed could never be
     * dismissed.
     */
    fun discard() {
        closing.value = true
        viewModelScope.launch {
            activeRun.clear()
            eventChannel.send(RunSessionEvent.Finished)
        }
    }

    /** Settles a [RunSessionUiState.Conflict]: go to the run in progress, or drop it for this one. */
    fun resolveConflict(resume: Boolean) {
        val conflict = uiState.value as? RunSessionUiState.Conflict ?: return
        viewModelScope.launch {
            if (resume) {
                eventChannel.send(RunSessionEvent.OpenRun(conflict.existing))
            } else {
                activeRun.clear()
            }
        }
    }

    private fun running(): ActiveRun? = (uiState.value as? RunSessionUiState.Running)?.run

    private suspend fun planDay() = plans.getActivePlan().first()
        ?.weeks?.find { it.weekNumber == weekNumber }
        ?.days?.getOrNull(dayIndex)

    private suspend fun planDayName(): String? = planDay()?.dayName

    /** The day's planned run, recorded as the log's exercises: a run has no weight to log. */
    private suspend fun planInstruction(): List<LoggedExerciseEntity> = planDay()?.exercises.orEmpty().map { exercise ->
        LoggedExerciseEntity(
            name = exercise.name,
            sets = exercise.sets,
            reps = exercise.reps,
            weight = "",
            rpe = exercise.rpe,
        )
    }

    /** Whether writing is allowed; when it is not, tells the screen to show the paywall. */
    private fun allowWrite(): Boolean {
        if (!entitlementManager.entitlement.value.isReadOnly) return true
        eventChannel.trySend(RunSessionEvent.ShowPaywall(PremiumRequiredReason.TRIAL_EXPIRED))
        return false
    }

    companion object {
        const val KEY_WEEK = "weekNumber"
        const val KEY_DAY = "dayIndex"
        const val KEY_KIND = "kind"
        const val KIND_RUN = "run"
        private const val MILLIS_PER_SECOND = 1000L
    }
}
