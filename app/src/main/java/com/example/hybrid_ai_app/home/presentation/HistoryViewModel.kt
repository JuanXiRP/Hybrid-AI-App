package com.example.hybrid_ai_app.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

sealed interface HistoryUiState {
    object Loading : HistoryUiState
    object Empty : HistoryUiState
    data class Success(val items: List<HistoryItem>) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

// UI-optimized data model representing a factual historical execution
data class HistoryItem(
    val logId: Long,
    val formattedDate: String,
    val weekNumber: Int,
    val dayNumber: Int,
    val title: String,
    val isCardio: Boolean,
    val summary: String
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: WorkoutPlanRepository
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = repository.getActivePlan()
        .combine(repository.getAllWorkoutLogs()) { plan, logs ->
            if (plan == null || logs.isEmpty()) {
                HistoryUiState.Empty
            } else {
                val dateFormatter = SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault())

                val mappedItems = logs.map { log ->
                    // Cross-reference Room log references against Gemini matrix metadata
                    val weekData = plan.weeks.find { it.weekNumber == log.weekNumber }
                    val dayData = weekData?.days?.getOrNull(log.dayIndex)

                    val title = dayData?.dayName ?: "Training Session"
                    val isCardio = title.contains("Endurance", ignoreCase = true) ||
                            title.contains("Intervals", ignoreCase = true)
                    val exerciseCount = dayData?.exercises?.size ?: 0

                    HistoryItem(
                        logId = log.id,
                        formattedDate = dateFormatter.format(Date(log.timestamp)),
                        weekNumber = log.weekNumber,
                        dayNumber = log.dayIndex + 1,
                        title = title,
                        isCardio = isCardio,
                        summary = if (exerciseCount == 0) "Recovery Protocol Completed" else "$exerciseCount Exercises Logged"
                    )
                }
                HistoryUiState.Success(mappedItems)
            }
        }
        .catch { exception -> emit(HistoryUiState.Error(exception.message ?: "Failed to process history")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HistoryUiState.Loading
        )
}