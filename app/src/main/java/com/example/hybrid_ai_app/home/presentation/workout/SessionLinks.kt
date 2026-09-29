package com.example.hybrid_ai_app.home.presentation.workout

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A request, from outside the navigation graph, to open a workout session.
 *
 * The ongoing workout notification is tapped in the system shade, where there is no NavController
 * to call. `MainActivity` records the request here from the notification's intent, and the main
 * scaffold — the only place that owns the navigation controller for the session route — consumes it
 * once it exists. Holding it as state rather than acting on it at once is what makes it work from a
 * cold start too, when the scaffold is composed well after the intent arrives.
 */
object SessionLinks {

    private val _pending = MutableStateFlow<SessionDay?>(null)
    val pending: StateFlow<SessionDay?> = _pending.asStateFlow()

    fun open(day: SessionDay) {
        _pending.value = day
    }

    /** Marks the request as handled, so it does not fire again on the next recomposition. */
    fun consume() {
        _pending.value = null
    }

    const val ACTION_OPEN_SESSION = "com.hybridai.training.action.OPEN_WORKOUT_SESSION"
    const val EXTRA_WEEK = "extra_week_number"
    const val EXTRA_DAY = "extra_day_index"
}
