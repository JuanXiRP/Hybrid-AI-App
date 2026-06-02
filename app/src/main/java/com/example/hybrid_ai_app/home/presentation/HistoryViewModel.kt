package com.example.hybrid_ai_app.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// UI State definition for the History Screen
sealed interface HistoryUiState {
    object Loading : HistoryUiState
    object Empty : HistoryUiState
    data class Success(val items: List<HistoryItem>) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: WorkoutPlanRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val localProfilePicPath = preferencesManager.userProfilePicFlow
    val uiState: StateFlow<HistoryUiState> = combine(
        repository.getAllWorkoutLogs(),
        repository.getActivePlan()
    ) { logs, plan ->
        if (logs.isEmpty() || plan == null) {
            return@combine HistoryUiState.Empty
        }

        // Map database entities
        val historyItems = logs
            .sortedByDescending { it.timestamp }
            .map { log ->
                val weekData = plan.weeks.find { it.weekNumber == log.weekNumber }
                val dayData = weekData?.days?.getOrNull(log.dayIndex)

                val isCardio = dayData?.workoutType == "cardio"
                val title = dayData?.dayName ?: "Workout Session"

                val mappedMetrics = log.loggedExercises.map { entity ->
                    LoggedExerciseMetric(
                        name = entity.name,
                        sets = entity.sets,
                        reps = entity.reps,
                        weight = entity.weight,
                        rpe = entity.rpe
                    )
                }

                val summary = if (mappedMetrics.isNotEmpty()) {
                    val exerciseNames = mappedMetrics.take(3).joinToString(", ") { it.name }
                    if (mappedMetrics.size > 3) "$exerciseNames..." else exerciseNames
                } else {
                    if (isCardio) "Endurance session completed" else "Strength session completed"
                }

                HistoryItem(
                    logId = log.id,
                    formattedDate = formatDate(log.timestamp),
                    weekNumber = log.weekNumber,
                    dayNumber = log.dayIndex + 1,
                    title = title,
                    isCardio = isCardio,
                    summary = summary,
                    loggedMetrics = mappedMetrics
                )
            }

        HistoryUiState.Success(historyItems)

    }
        .catch { exception ->
            emit(HistoryUiState.Error(exception.message ?: "Error loading performance history"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HistoryUiState.Loading
        )

    // Helper function to format Unix timestamps
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}