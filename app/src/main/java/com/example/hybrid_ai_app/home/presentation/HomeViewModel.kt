package com.example.hybrid_ai_app.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.core.data.local.entity.UserProgressEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import com.example.hybrid_ai_app.core.data.remote.dto.WeekDto // 🟢 Explicit import
import com.example.hybrid_ai_app.core.data.remote.dto.DayDto  // 🟢 Explicit import
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
        val currentWeek: WeekDto, // 🟢 Strongly typed using your network/db DTO
        val currentDay: DayDto?,  // 🟢 Strongly typed using your network/db DTO
        val weeklyCompletion: List<Boolean>,
        val currentWeekNumber: Int,
        val currentDayIndex: Int
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: WorkoutPlanRepository
) : ViewModel() {

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

    fun logCurrentWorkoutAsCompleted() {
        val currentState = uiState.value
        if (currentState is HomeUiState.Success) {
            viewModelScope.launch {
                val log = WorkoutLogEntity(
                    weekNumber = currentState.currentWeekNumber,
                    dayIndex = currentState.currentDayIndex,
                    isCompleted = true
                )

                val isLastDayOfWeek = currentState.currentDayIndex == 6
                val nextWeek = if (isLastDayOfWeek) currentState.currentWeekNumber + 1 else currentState.currentWeekNumber
                val nextDay = if (isLastDayOfWeek) 0 else currentState.currentDayIndex + 1

                val updatedProgress = UserProgressEntity(
                    userId = "active_plan", // Matches your PrimaryKey static fallback in WorkoutPlanEntity
                    currentWeekNumber = nextWeek,
                    currentDayIndex = nextDay
                )

                repository.completeWorkout(log, updatedProgress)
            }
        }
    }
}