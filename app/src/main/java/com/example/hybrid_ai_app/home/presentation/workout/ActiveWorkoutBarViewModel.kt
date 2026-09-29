package com.example.hybrid_ai_app.home.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import com.example.hybrid_ai_app.home.domain.repository.ActiveWorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Feeds the minimized-workout bar shown above the navigation bar: the session in progress, or null.
 *
 * It only observes. The session belongs to the workout screen, which is the one writer; the bar is
 * a way back to it, so it needs no actions of its own.
 */
@HiltViewModel
class ActiveWorkoutBarViewModel @Inject constructor(
    activeWorkout: ActiveWorkoutRepository,
) : ViewModel() {

    val session: StateFlow<WorkoutSession?> = activeWorkout.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )
}
