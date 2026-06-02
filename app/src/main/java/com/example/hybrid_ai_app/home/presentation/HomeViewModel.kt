package com.example.hybrid_ai_app.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.core.data.local.entity.LoggedExerciseEntity
import com.example.hybrid_ai_app.core.data.local.entity.UserProgressEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import com.example.hybrid_ai_app.core.data.remote.dto.WeekDto
import com.example.hybrid_ai_app.core.data.remote.dto.DayDto
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
        val currentDayIndex: Int
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: WorkoutPlanRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val localProfilePicPath = preferencesManager.userProfilePicFlow
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
                                currentDayIndex = dayIdx
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
            initialValue = HomeUiState.Loading
        )

    fun logCurrentWorkoutAsCompleted(metrics: List<LoggedExerciseEntity> = emptyList()) {
        val currentState = uiState.value
        if (currentState is HomeUiState.Success) {
            viewModelScope.launch {
                val log = WorkoutLogEntity(
                    weekNumber = currentState.currentWeekNumber,
                    dayIndex = currentState.currentDayIndex,
                    timestamp = System.currentTimeMillis(),
                    isCompleted = true,
                    loggedExercises = metrics
                )

                val isLastDayOfWeek = currentState.currentDayIndex == 6
                val nextWeek = if (isLastDayOfWeek) currentState.currentWeekNumber + 1 else currentState.currentWeekNumber
                val nextDay = if (isLastDayOfWeek) 0 else currentState.currentDayIndex + 1

                val updatedProgress = UserProgressEntity(
                    userId = "active_plan",
                    currentWeekNumber = nextWeek,
                    currentDayIndex = nextDay
                )

                // Pass context to the repository to route the network request
                val workoutType = currentState.currentDay?.workoutType ?: "rest"
                val dayName = currentState.currentDay?.dayName ?: "Workout"

                repository.completeWorkout(log, updatedProgress, workoutType, dayName)
            }
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