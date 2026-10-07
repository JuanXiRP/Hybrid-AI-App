package com.example.hybrid_ai_app.home.presentation.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.core.data.EntitlementManager
import com.example.hybrid_ai_app.core.data.local.entity.LoggedSetEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.di.ApplicationScope
import com.example.hybrid_ai_app.core.domain.model.CatalogExercise
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import com.example.hybrid_ai_app.core.domain.repository.ExerciseCatalogRepository
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import com.example.hybrid_ai_app.core.util.IdProvider
import com.example.hybrid_ai_app.core.util.TimeProvider
import com.example.hybrid_ai_app.home.data.mapper.toWorkoutLog
import com.example.hybrid_ai_app.home.data.mapper.toWorkoutSession
import com.example.hybrid_ai_app.home.domain.model.SessionExercise
import com.example.hybrid_ai_app.home.domain.model.SessionSet
import com.example.hybrid_ai_app.home.domain.model.SetType
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import com.example.hybrid_ai_app.home.domain.repository.ActiveWorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Which day of the plan a session was requested for. An extra session names the plan's "today"
 * instead, and is matched by being extra rather than by its day.
 */
data class SessionDay(val weekNumber: Int, val dayIndex: Int, val isExtra: Boolean = false) {
    fun matches(session: WorkoutSession): Boolean = if (isExtra) {
        session.isExtra
    } else {
        !session.isExtra && session.weekNumber == weekNumber && session.dayIndex == dayIndex
    }
}

/** Why a session could not be shown. The screen turns each into localized copy. */
enum class WorkoutSessionError {
    /** The route names a plan day that is not in the cached plan. */
    NO_PLAN_DAY,

    /** Edit mode, for a log that no longer exists. */
    LOG_NOT_FOUND,

    /** The trial ended; nothing may be started or written. */
    READ_ONLY,
}

sealed interface WorkoutSessionUiState {
    data object Loading : WorkoutSessionUiState

    /**
     * @param previousByExercise what the athlete did last time, keyed by [SessionExercise.id].
     * @param catalogByExercise the catalog entry of each exercise that resolves to one, keyed by
     * [SessionExercise.id]; an exercise absent here has no thumbnail, info sheet or alternatives.
     */
    data class Active(
        val session: WorkoutSession,
        val previousByExercise: Map<String, List<LoggedSetEntity>> = emptyMap(),
        val catalogByExercise: Map<String, CatalogExercise> = emptyMap(),
        val isEditMode: Boolean = false,
    ) : WorkoutSessionUiState

    /** A different day's session is already in progress; the athlete chooses which one continues. */
    data class Conflict(
        val existing: WorkoutSession,
        val requested: SessionDay,
    ) : WorkoutSessionUiState

    data class Error(val reason: WorkoutSessionError) : WorkoutSessionUiState
}

/** One-shot signals the screen reacts to once. */
sealed interface WorkoutSessionEvent {
    /** The session was saved, or discarded, and the screen should close. */
    data object Finished : WorkoutSessionEvent

    data class ShowPaywall(val reason: PremiumRequiredReason) : WorkoutSessionEvent

    /** Finishing with unticked sets: ask whether to complete them or drop them. */
    data class ConfirmIncomplete(val count: Int) : WorkoutSessionEvent

    /** The local write failed; nothing was saved and the session is still there. */
    data object SaveFailed : WorkoutSessionEvent
}

/** The three fields of a set an athlete types into. */
enum class SetField { WEIGHT, REPS, ACTUAL_RPE }

/**
 * Drives the strength workout screen, in two modes:
 *  - **live** (`weekNumber` + `dayIndex` in the route, or `kind` = [KIND_STRENGTH] for an extra
 *    session added on top of the plan): a session that is persisted to Room after every change, so
 *    it survives process death and feeds the minimized bar and the foreground service. An extra
 *    session starts empty and is stamped with the plan's "today".
 *  - **edit** (`logId`): a past workout reopened. Its state lives here and in [SavedStateHandle],
 *    never in the active-session table, so it cannot clobber a live session.
 *
 * Every write-capable action first checks the entitlement, exactly as `HomeViewModel` does: once
 * the trial has ended the screen shows the paywall and changes nothing.
 */
@HiltViewModel
class WorkoutSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plans: WorkoutPlanRepository,
    private val activeWorkout: ActiveWorkoutRepository,
    private val catalog: ExerciseCatalogRepository,
    private val entitlementManager: EntitlementManager,
    private val time: TimeProvider,
    private val ids: IdProvider,
    // Saves must outlive this ViewModel: leaving the screen right after a keystroke has to still
    // persist that keystroke, and viewModelScope is cancelled first.
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val savedState = savedStateHandle
    private val requestedDay: SessionDay? = savedStateHandle.get<Int>(KEY_WEEK)?.let { week ->
        savedStateHandle.get<Int>(KEY_DAY)?.let { day -> SessionDay(week, day) }
    }
    private val editLogId: Long? = savedStateHandle.get<Long>(KEY_LOG_ID)
    private val isExtraRoute: Boolean = savedStateHandle.get<String>(KEY_KIND) == KIND_STRENGTH

    val isEditMode: Boolean = editLogId != null

    private val _uiState = MutableStateFlow<WorkoutSessionUiState>(WorkoutSessionUiState.Loading)
    val uiState: StateFlow<WorkoutSessionUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<WorkoutSessionEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutSessionEvent> = eventChannel.receiveAsFlow()

    /** The stored log being edited, needed to write it back under its own row and client id. */
    private var editingLog: WorkoutLogEntity? = null

    /**
     * The latest session waiting to be written. Conflated: only the newest state matters, and a
     * burst of keystrokes must not queue a burst of writes.
     */
    private var saveQueue = Channel<WorkoutSession>(Channel.CONFLATED)
    private var saveJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    override fun onCleared() {
        // Let the writer drain what is queued, on the application scope, then stop.
        saveQueue.close()
    }

    // ------------------------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------------------------

    private suspend fun load() {
        when {
            editLogId != null -> loadForEdit(editLogId)
            isExtraRoute -> loadLive(extraDay())
            requestedDay != null -> loadLive(requestedDay)
            else -> _uiState.value = WorkoutSessionUiState.Error(WorkoutSessionError.NO_PLAN_DAY)
        }
    }

    /** An extra session is stamped with the plan's "today": the week and day the athlete is on. */
    private suspend fun extraDay(): SessionDay {
        val progress = plans.getUserProgress().first()
        return SessionDay(
            weekNumber = progress?.currentWeekNumber ?: 1,
            dayIndex = progress?.currentDayIndex ?: 0,
            isExtra = true,
        )
    }

    private suspend fun loadLive(day: SessionDay) {
        val existing = activeWorkout.observe().first()

        when {
            existing == null -> startNew(day)
            day.matches(existing) -> show(existing)
            else -> _uiState.value = WorkoutSessionUiState.Conflict(existing, day)
        }
    }

    private suspend fun startNew(day: SessionDay) {
        // Starting writes a row, and a lapsed trial may not write.
        if (entitlementManager.entitlement.value.isReadOnly) {
            _uiState.value = WorkoutSessionUiState.Error(WorkoutSessionError.READ_ONLY)
            eventChannel.send(WorkoutSessionEvent.ShowPaywall(PremiumRequiredReason.TRIAL_EXPIRED))
            return
        }

        if (day.isExtra) {
            // Empty on purpose: the athlete adds exercises from the catalog. The title stays blank
            // and the screen names it, so no language is baked into the stored session.
            show(
                WorkoutSession(
                    clientId = ids.newId(),
                    weekNumber = day.weekNumber,
                    dayIndex = day.dayIndex,
                    title = "",
                    startedAt = time.nowMillis(),
                    exercises = emptyList(),
                    isExtra = true,
                ),
            )
            return
        }

        val plan = plans.getActivePlan().first()
        val planDay = plan?.weeks?.find { it.weekNumber == day.weekNumber }?.days?.getOrNull(day.dayIndex)
        if (planDay == null) {
            _uiState.value = WorkoutSessionUiState.Error(WorkoutSessionError.NO_PLAN_DAY)
            return
        }

        show(
            planDay.toWorkoutSession(
                clientId = ids.newId(),
                weekNumber = day.weekNumber,
                dayIndex = day.dayIndex,
                startedAt = time.nowMillis(),
                newId = ids::newId,
            ),
        )
    }

    private suspend fun loadForEdit(logId: Long) {
        val log = plans.getWorkoutLog(logId)
        if (log == null) {
            _uiState.value = WorkoutSessionUiState.Error(WorkoutSessionError.LOG_NOT_FOUND)
            return
        }
        editingLog = log

        // A process kill mid-edit restores the athlete's unsaved changes from the saved state.
        val restored = savedState.get<String>(KEY_EDIT_SESSION)?.let {
            runCatching { editJson.decodeFromString<WorkoutSession>(it) }.getOrNull()
        }
        show(restored ?: log.toWorkoutSession(ids::newId))
    }

    /** Puts a session on screen and starts persisting it, then fills in what needs a lookup. */
    private suspend fun show(session: WorkoutSession) {
        _uiState.value = WorkoutSessionUiState.Active(session = session, isEditMode = isEditMode)
        persist(session)
        lookUpExtras(session.exercises)
    }

    private fun lookUpExtras(exercises: List<SessionExercise>) {
        val before = editingLog?.let { it.startedAt ?: it.timestamp } ?: Long.MAX_VALUE
        viewModelScope.launch {
            exercises.forEach { exercise ->
                val previous = plans.getLastPerformance(exercise.exerciseId, exercise.name, before)
                val entry = catalog.resolve(exercise.exerciseId, exercise.name)
                updateActive { active ->
                    active.copy(
                        previousByExercise = active.previousByExercise + (exercise.id to previous),
                        catalogByExercise = if (entry != null) {
                            active.catalogByExercise + (exercise.id to entry)
                        } else {
                            active.catalogByExercise - exercise.id
                        },
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // Conflict
    // ------------------------------------------------------------------------------------

    /**
     * Settles a [WorkoutSessionUiState.Conflict]: continue the session already in progress, or drop
     * it and start the one that was asked for.
     */
    fun resolveConflict(resume: Boolean) {
        val conflict = _uiState.value as? WorkoutSessionUiState.Conflict ?: return

        viewModelScope.launch {
            if (resume) {
                show(conflict.existing)
                return@launch
            }
            if (!allowWrite()) return@launch
            activeWorkout.clear()
            startNew(conflict.requested)
        }
    }

    // ------------------------------------------------------------------------------------
    // Sets
    // ------------------------------------------------------------------------------------

    fun updateSet(exerciseId: String, setId: String, field: SetField, value: String) = change {
        withSet(exerciseId, setId) { set ->
            when (field) {
                SetField.WEIGHT -> set.copy(weight = value)
                SetField.REPS -> set.copy(reps = value)
                SetField.ACTUAL_RPE -> set.copy(actualRpe = value)
            }
        }
    }

    /**
     * Ticks or unticks a set. Ticking starts the exercise's rest timer (live mode only, and only
     * if the exercise has a rest time above zero); unticking leaves a running rest alone.
     */
    fun toggleSetCompleted(exerciseId: String, setId: String) = change {
        val exercise = exercises.find { it.id == exerciseId }
        val set = exercise?.sets?.find { it.id == setId } ?: return@change this
        val completing = !set.completed

        val toggled = withSet(exerciseId, setId) { it.copy(completed = completing) }
        if (completing && !isEditMode && exercise.restSeconds > 0) {
            toggled.copy(
                restEndsAt = time.nowMillis() + exercise.restSeconds * MILLIS_PER_SECOND,
                restTotalSec = exercise.restSeconds,
            )
        } else {
            toggled
        }
    }

    /** A new set copying the last one's targets (never its typed values), at the end. */
    fun addSet(exerciseId: String) = change {
        withExercise(exerciseId) { exercise ->
            val last = exercise.sets.lastOrNull()
            exercise.copy(
                sets = exercise.sets + SessionSet(
                    id = ids.newId(),
                    targetReps = last?.targetReps.orEmpty(),
                    targetRpe = last?.targetRpe.orEmpty(),
                ),
            )
        }
    }

    fun removeSet(exerciseId: String, setId: String) = change {
        withExercise(exerciseId) { exercise -> exercise.copy(sets = exercise.sets.filterNot { it.id == setId }) }
    }

    fun setType(exerciseId: String, setId: String, type: SetType) = change {
        withSet(exerciseId, setId) { it.copy(type = type) }
    }

    /** Makes the chosen set a warm-up, and every set before it, since warm-ups come first. */
    fun convertToWarmupUpTo(exerciseId: String, setId: String) = change {
        withExercise(exerciseId) { exercise ->
            val cutoff = exercise.sets.indexOfFirst { it.id == setId }
            if (cutoff < 0) {
                exercise
            } else {
                exercise.copy(
                    sets = exercise.sets.mapIndexed { index, set ->
                        if (index <= cutoff) set.copy(type = SetType.WARMUP) else set
                    },
                )
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // Exercises
    // ------------------------------------------------------------------------------------

    /** Appends a catalog exercise with three empty sets and no target RPE. */
    fun addExercise(catalogId: String) {
        if (!allowWrite()) return
        viewModelScope.launch {
            val entry = catalog.getById(catalogId) ?: return@launch
            val exercise = SessionExercise(
                id = ids.newId(),
                exerciseId = entry.id,
                name = entry.name,
                sets = List(NEW_EXERCISE_SET_COUNT) { SessionSet(id = ids.newId()) },
            )
            applyChange { copy(exercises = exercises + exercise) }
            lookUpExtras(listOf(exercise))
        }
    }

    /**
     * Swaps an exercise for another (the machine is busy). The set count, types and targets stay;
     * what was typed and ticked does not, because it belonged to the exercise being replaced.
     */
    fun replaceExercise(exerciseId: String, catalogId: String) {
        if (!allowWrite()) return
        viewModelScope.launch {
            val entry = catalog.getById(catalogId) ?: return@launch
            var replaced: SessionExercise? = null
            applyChange {
                withExercise(exerciseId) { exercise ->
                    exercise.copy(
                        exerciseId = entry.id,
                        name = entry.name,
                        sets = exercise.sets.map {
                            it.copy(weight = "", reps = "", actualRpe = "", completed = false)
                        },
                    ).also { replaced = it }
                }
            }
            replaced?.let { lookUpExtras(listOf(it)) }
        }
    }

    fun removeExercise(exerciseId: String) = change {
        copy(exercises = exercises.filterNot { it.id == exerciseId })
    }

    fun moveExercise(from: Int, to: Int) = change {
        if (from !in exercises.indices || to !in exercises.indices || from == to) {
            this
        } else {
            val reordered = exercises.toMutableList()
            reordered.add(to, reordered.removeAt(from))
            copy(exercises = reordered)
        }
    }

    fun setExerciseNotes(exerciseId: String, notes: String) = change {
        withExercise(exerciseId) { it.copy(notes = notes) }
    }

    /** How long the rest lasts after this exercise's sets, for this session only. */
    fun setExerciseRest(exerciseId: String, seconds: Int) = change {
        withExercise(exerciseId) { it.copy(restSeconds = seconds.coerceIn(0, MAX_REST_SECONDS)) }
    }

    /**
     * Substitutes for an exercise on screen, most similar first (same muscle, a different machine).
     * Empty when the exercise has no catalog entry, which is also what disables the menu item.
     */
    suspend fun alternativesFor(exerciseId: String): List<CatalogExercise> {
        val entry = (_uiState.value as? WorkoutSessionUiState.Active)?.catalogByExercise?.get(exerciseId)
            ?: return emptyList()
        return catalog.alternativesFor(entry.id)
    }

    /** The add-exercise sheet's search: strength-style exercises by name and primary muscle. */
    suspend fun searchExercises(query: String, muscle: String?): List<CatalogExercise> = catalog.search(query, muscle)

    /** The muscles the add-exercise sheet can filter by. */
    suspend fun muscles(): List<String> = catalog.muscles()

    // ------------------------------------------------------------------------------------
    // Rest timer
    // ------------------------------------------------------------------------------------

    /** Moves the end of a running rest by [deltaSec]; a rest that is not running is left alone. */
    fun adjustRest(deltaSec: Int) = change {
        val endsAt = restEndsAt
        val now = time.nowMillis()
        if (isEditMode || endsAt == null || endsAt <= now) return@change this

        val newEnd = endsAt + deltaSec * MILLIS_PER_SECOND
        if (newEnd <= now) {
            copy(restEndsAt = null, restTotalSec = null)
        } else {
            copy(
                restEndsAt = newEnd,
                restTotalSec = ((restTotalSec ?: 0) + deltaSec).coerceAtLeast(1),
            )
        }
    }

    fun skipRest() = change {
        if (restEndsAt == null) this else copy(restEndsAt = null, restTotalSec = null)
    }

    /** A rest that belongs to no set, from the timer button. */
    fun startManualRest(seconds: Int) = change {
        if (isEditMode || seconds <= 0) {
            this
        } else {
            copy(restEndsAt = time.nowMillis() + seconds * MILLIS_PER_SECOND, restTotalSec = seconds)
        }
    }

    // ------------------------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------------------------

    fun setWorkoutNotes(notes: String) = change { copy(notes = notes) }

    /** Edit mode only: the date and time the workout started. */
    fun setStartedAt(millis: Long) = change { if (isEditMode) copy(startedAt = millis) else this }

    /**
     * The Finish button. With unticked sets it asks first ([WorkoutSessionEvent.ConfirmIncomplete]);
     * with none it finishes straight away.
     */
    fun requestFinish() {
        val session = activeSession() ?: return
        val incomplete = session.exercises.sumOf { exercise -> exercise.sets.count { !it.completed } }
        if (incomplete > 0) {
            eventChannel.trySend(WorkoutSessionEvent.ConfirmIncomplete(incomplete))
        } else {
            finish(discardIncomplete = false)
        }
    }

    /**
     * Saves the session as a finished workout.
     *
     * @param discardIncomplete keep only the sets that were ticked; otherwise every set counts.
     */
    fun finish(discardIncomplete: Boolean) {
        val session = activeSession() ?: return
        if (!allowWrite()) return

        viewModelScope.launch {
            // No queued snapshot may land after the log is written and the session is cleared, or
            // it would resurrect the session that was just finished.
            stopSaving()

            val result = if (isEditMode) {
                saveEdit(session, discardIncomplete)
            } else {
                plans.completeSession(session, discardIncomplete, finishedAt = time.nowMillis())
            }

            result
                .onSuccess { eventChannel.send(WorkoutSessionEvent.Finished) }
                .onFailure {
                    // The session is intact: keep it, and start persisting again.
                    resumeSaving()
                    persist(session)
                    eventChannel.send(WorkoutSessionEvent.SaveFailed)
                }
        }
    }

    private suspend fun saveEdit(session: WorkoutSession, discardIncomplete: Boolean): Result<Unit> {
        val original = editingLog ?: return Result.failure(IllegalStateException("No log is being edited"))
        // The workout keeps its length; moving the start moves the finish with it.
        val durationSec = original.durationSec ?: 0
        val log = session.toWorkoutLog(
            finishedAt = session.startedAt + durationSec * MILLIS_PER_SECOND,
            completeAll = !discardIncomplete,
            id = original.id,
        )
        return plans.updateWorkoutLog(log)
    }

    /**
     * Throws the workout away. In edit mode it simply leaves, since the stored log is untouched.
     *
     * Deliberately allowed after the trial has ended: it removes a draft rather than writing
     * anything the backend would refuse, and without it a session left over from before the trial
     * lapsed would stay in the minimized bar with no way to dismiss it.
     */
    fun discard() {
        viewModelScope.launch {
            stopSaving()
            if (!isEditMode) activeWorkout.clear()
            eventChannel.send(WorkoutSessionEvent.Finished)
        }
    }

    // ------------------------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------------------------

    private fun activeSession(): WorkoutSession? = (_uiState.value as? WorkoutSessionUiState.Active)?.session

    private fun updateActive(transform: (WorkoutSessionUiState.Active) -> WorkoutSessionUiState.Active) {
        _uiState.update { state -> if (state is WorkoutSessionUiState.Active) transform(state) else state }
    }

    /** Whether writing is allowed; when it is not, tells the screen to show the paywall. */
    private fun allowWrite(): Boolean {
        if (!entitlementManager.entitlement.value.isReadOnly) return true
        eventChannel.trySend(WorkoutSessionEvent.ShowPaywall(PremiumRequiredReason.TRIAL_EXPIRED))
        return false
    }

    /** Applies [transform] to the session, if a write is allowed, and persists the result. */
    private fun change(transform: WorkoutSession.() -> WorkoutSession) {
        if (!allowWrite()) return
        applyChange(transform)
    }

    private fun applyChange(transform: WorkoutSession.() -> WorkoutSession) {
        var updated: WorkoutSession? = null
        updateActive { active ->
            val next = active.session.transform()
            // An action that changes nothing (skipping a rest that is not running, say) neither
            // republishes the state nor costs a write.
            updated = next.takeIf { it != active.session }
            if (updated == null) active else active.copy(session = next)
        }
        updated?.let(::persist)
    }

    private fun persist(session: WorkoutSession) {
        if (isEditMode) {
            savedState[KEY_EDIT_SESSION] = editJson.encodeToString(session)
            return
        }
        ensureSaving()
        saveQueue.trySend(session)
    }

    private fun ensureSaving() {
        if (saveJob?.isActive == true) return
        val queue = saveQueue
        saveJob = appScope.launch {
            for (snapshot in queue) activeWorkout.save(snapshot)
        }
    }

    /** Lets the queued write finish, then stops the writer. */
    private suspend fun stopSaving() {
        saveQueue.close()
        saveJob?.join()
    }

    private fun resumeSaving() {
        saveQueue = Channel(Channel.CONFLATED)
        saveJob = null
    }

    private fun WorkoutSession.withExercise(
        exerciseId: String,
        transform: (SessionExercise) -> SessionExercise,
    ): WorkoutSession = copy(exercises = exercises.map { if (it.id == exerciseId) transform(it) else it })

    private fun WorkoutSession.withSet(
        exerciseId: String,
        setId: String,
        transform: (SessionSet) -> SessionSet,
    ): WorkoutSession = withExercise(exerciseId) { exercise ->
        exercise.copy(sets = exercise.sets.map { if (it.id == setId) transform(it) else it })
    }

    companion object {
        const val KEY_WEEK = "weekNumber"
        const val KEY_DAY = "dayIndex"
        const val KEY_LOG_ID = "logId"
        const val KEY_KIND = "kind"
        const val KIND_STRENGTH = "strength"
        const val KIND_RUN = "run"
        private const val KEY_EDIT_SESSION = "editSession"

        /** The rest times offered per exercise, in seconds; 0 turns the automatic rest off. */
        val REST_CHOICES_SECONDS: List<Int> = listOf(0, 30, 60, 90, 120, 150, 180, 240, 300)

        private const val MAX_REST_SECONDS = 3600
        private const val NEW_EXERCISE_SET_COUNT = 3
        private const val MILLIS_PER_SECOND = 1000L

        private val editJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
