package com.example.hybrid_ai_app.home.presentation.run

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.core.domain.model.RunLimits
import com.example.hybrid_ai_app.core.domain.model.defaultRunStructure
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the guided-run setup of one plan day, or of an extra run. The day comes from the route
 * (`workout_execution/{weekNumber}/{dayIndex}`); an extra run (`extra_workout/run`) has no day and
 * starts as a free run. The values the athlete has set are kept in the
 * [SavedStateHandle] so they survive the process being killed while the screen is open. Every
 * setter clamps to [RunLimits], so the structure handed to the run is always valid.
 *
 * The run screen reads the final structure from [uiState] when the athlete taps Start.
 */
@HiltViewModel
class RunSetupViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val plans: WorkoutPlanRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RunSetupUiState>(RunSetupUiState.Loading)
    val uiState: StateFlow<RunSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (savedStateHandle.get<String>(KEY_KIND) == KIND_RUN) {
            val config = savedStateHandle.get<IntArray>(KEY_CONFIG)?.let(RunSetupConfig::fromInts)
                ?: RunSetupConfig.from(defaultRunStructure(name = "", sets = "", reps = "")).copy(mode = RunMainMode.FREE)
            _uiState.value = RunSetupUiState.Success(dayName = "", instruction = null, config = config, isExtra = true)
            return
        }

        val week = savedStateHandle.get<Int>(KEY_WEEK)
        val dayIndex = savedStateHandle.get<Int>(KEY_DAY)
        val plan = try {
            plans.getActivePlan().first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val day = plan
            ?.weeks?.find { it.weekNumber == week }
            ?.days?.getOrNull(dayIndex ?: -1)
        if (day == null) {
            _uiState.value = RunSetupUiState.Error
            return
        }

        val instruction = day.exercises.firstOrNull()
        val config = savedStateHandle.get<IntArray>(KEY_CONFIG)?.let(RunSetupConfig::fromInts)
            ?: RunSetupConfig.from(
                defaultRunStructure(
                    name = instruction?.name.orEmpty(),
                    sets = instruction?.sets.orEmpty(),
                    reps = instruction?.reps.orEmpty(),
                ),
            )
        _uiState.value = RunSetupUiState.Success(day.dayName, instruction, config)
    }

    fun setWarmupMin(minutes: Int) = update { it.copy(warmupMin = minutes.coerceIn(0, RunLimits.MAX_WARMUP_MIN)) }

    fun setCooldownMin(minutes: Int) = update { it.copy(cooldownMin = minutes.coerceIn(0, RunLimits.MAX_COOLDOWN_MIN)) }

    fun setMode(mode: RunMainMode) = update { it.copy(mode = mode) }

    fun setContinuousMin(minutes: Int) = update {
        it.copy(continuousMin = minutes.coerceIn(RunLimits.MIN_CONTINUOUS_MIN, RunLimits.MAX_CONTINUOUS_MIN))
    }

    fun setRepeats(repeats: Int) = update { it.copy(repeats = repeats.coerceIn(RunLimits.MIN_REPEATS, RunLimits.MAX_REPEATS)) }

    fun setWorkSec(seconds: Int) = update { it.copy(workSec = seconds.coerceIn(RunLimits.MIN_WORK_SEC, RunLimits.MAX_WORK_SEC)) }

    fun setRestSec(seconds: Int) = update { it.copy(restSec = seconds.coerceIn(0, RunLimits.MAX_REST_SEC)) }

    private inline fun update(change: (RunSetupConfig) -> RunSetupConfig) {
        val state = _uiState.value as? RunSetupUiState.Success ?: return
        val config = change(state.config)
        savedStateHandle[KEY_CONFIG] = config.toInts()
        _uiState.value = state.copy(config = config)
    }

    companion object {
        const val KEY_WEEK = "weekNumber"
        const val KEY_DAY = "dayIndex"
        const val KEY_KIND = "kind"
        const val KIND_RUN = "run"
        private const val KEY_CONFIG = "run_setup_config"
    }
}
