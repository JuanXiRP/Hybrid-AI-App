package com.example.hybrid_ai_app.home.presentation.workout

/**
 * Whether the session screen is on top of a resumed activity right now.
 *
 * The rest timer's end-of-rest cue must fire exactly once: the screen plays it itself while it is
 * showing, and the foreground service plays it when it is not (the workout was minimized, or the
 * app is in the background). The service reads this flag to decide. It is process-wide state
 * because the two sides share nothing else, and it is a plain volatile flag because a stale read
 * costs at most one missed or doubled cue.
 */
object SessionScreenPresence {
    @Volatile
    var isResumed: Boolean = false
}
