package com.example.hybrid_ai_app.home.presentation.workout

import com.example.hybrid_ai_app.core.domain.model.ActiveRun
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import com.example.hybrid_ai_app.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A request, from outside the navigation graph, to open a workout in progress.
 *
 * The ongoing workout notifications are tapped in the system shade, where there is no
 * NavController to call. `MainActivity` records the request here from the notification's intent,
 * and the main scaffold — the only place that owns the navigation controller for the workout
 * routes — consumes it once it exists. Holding it as state rather than acting on it at once is what
 * makes it work from a cold start too, when the scaffold is composed well after the intent arrives.
 *
 * The request is the route itself, so a strength session, a run and an extra workout all travel
 * the same way.
 */
object SessionLinks {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun open(route: String) {
        _pending.value = route
    }

    /** Marks the request as handled, so it does not fire again on the next recomposition. */
    fun consume() {
        _pending.value = null
    }

    /** The screen a strength session in progress lives on. */
    fun routeFor(session: WorkoutSession): String = if (session.isExtra) {
        Screen.ExtraWorkout.createRoute(WorkoutSessionViewModel.KIND_STRENGTH)
    } else {
        Screen.WorkoutExecution.createRoute(session.weekNumber, session.dayIndex)
    }

    /** The screen a run in progress lives on. */
    fun routeFor(run: ActiveRun): String = if (run.isExtra) {
        Screen.ExtraWorkout.createRoute(WorkoutSessionViewModel.KIND_RUN)
    } else {
        Screen.WorkoutExecution.createRoute(run.weekNumber, run.dayIndex)
    }

    const val ACTION_OPEN_SESSION = "com.hybridai.training.action.OPEN_WORKOUT_SESSION"
    const val EXTRA_ROUTE = "extra_route"
}
