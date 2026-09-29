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
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    val localProfilePicPath = preferencesManager.userProfilePicFlow
    val uiState: StateFlow<HistoryUiState> = combine(
        repository.getAllWorkoutLogs(),
        repository.getActivePlan(),
    ) { logs, plan ->
        // The logs are the history. They no longer depend on a plan being present: each one carries
        // its own title and type, so regenerating the plan does not blank this screen.
        if (logs.isEmpty()) {
            return@combine HistoryUiState.Empty
        }

        // Map database entities
        val historyItems = logs
            .sortedByDescending { it.timestamp }
            .map { log ->
                // Only rows written before logs carried their own title and type need the plan
                // lookup, and for those the plan is a best guess (it may have been regenerated).
                val dayData = plan?.weeks?.find { it.weekNumber == log.weekNumber }?.days?.getOrNull(log.dayIndex)

                val workoutType = log.workoutType ?: dayData?.workoutType
                val isCardio = workoutType == "cardio" || workoutType == "run"
                val title = log.title?.takeIf { it.isNotBlank() } ?: dayData?.dayName

                val mappedMetrics = log.loggedExercises.map { entity ->
                    LoggedExerciseMetric(
                        name = entity.name,
                        sets = entity.sets,
                        reps = entity.reps,
                        weight = entity.weight,
                        rpe = entity.rpe,
                        notes = entity.notes,
                        setLogs = entity.setLogs,
                    )
                }

                // The names of the first exercises. Blank when there are none; the screen then
                // shows a localized "session completed" line, which is why no English lives here.
                val summary = if (mappedMetrics.isNotEmpty()) {
                    val exerciseNames = mappedMetrics.take(3).joinToString(", ") { it.name }
                    if (mappedMetrics.size > 3) "$exerciseNames..." else exerciseNames
                } else {
                    ""
                }

                HistoryItem(
                    logId = log.id,
                    formattedDate = formatDate(log.timestamp),
                    weekNumber = log.weekNumber,
                    dayNumber = log.dayIndex + 1,
                    title = title,
                    isCardio = isCardio,
                    summary = summary,
                    loggedMetrics = mappedMetrics,
                    notes = log.notes,
                    durationSec = log.durationSec,
                    // Only a log with per-set detail can be edited with the session screen; a legacy
                    // one has nothing to put in the table.
                    isEditable = !isCardio && mappedMetrics.any { it.setLogs.isNotEmpty() },
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
            initialValue = HistoryUiState.Loading,
        )

    // Helper function to format Unix timestamps
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
