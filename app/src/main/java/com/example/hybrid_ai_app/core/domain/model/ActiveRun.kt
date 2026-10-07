package com.example.hybrid_ai_app.core.domain.model

/**
 * The run in progress, if any: what was started, and the anchors its clock is rebuilt from.
 *
 * It is stored (see `ActiveRunRepository`) rather than held in memory, so a run survives the
 * screen being left, the activity being recreated and the process being killed. The clock is two
 * numbers instead of a ticking counter: the time banked before the last resume ([accumulatedMs])
 * and the wall-clock moment of that resume ([resumedAt], null while paused). Whoever reads it adds
 * the time since [resumedAt], so a run that was not observed for an hour still reads an hour on.
 *
 * @param clientId the run's identity, reused as the finished log's `clientId`.
 * @param weekNumber the plan week the run was opened for, or the plan's "today" for an extra.
 * @param dayIndex the plan day, counting rest days, under the same rule as [weekNumber].
 * @param isExtra a run added on top of the plan rather than opened from one of its days.
 * @param title the plan day's name; blank for an extra, which the screen names itself.
 * @param structure the guided structure; null for a free run (GPS and a stopwatch only).
 */
data class ActiveRun(
    val clientId: String,
    val weekNumber: Int,
    val dayIndex: Int,
    val isExtra: Boolean,
    val title: String,
    val structure: RunStructure?,
    val startedAt: Long,
    val accumulatedMs: Long,
    val resumedAt: Long?,
) {
    val isPaused: Boolean get() = resumedAt == null

    /** How long the run has been running at [nowMillis], pauses excluded. */
    fun elapsedMs(nowMillis: Long): Long = accumulatedMs + (resumedAt?.let { (nowMillis - it).coerceAtLeast(0) } ?: 0)

    /** Whether this is the run a screen opened for [weekNumber] / [dayIndex] (or for an extra) is asking for. */
    fun isFor(weekNumber: Int, dayIndex: Int, isExtra: Boolean): Boolean = if (isExtra) {
        this.isExtra
    } else {
        !this.isExtra && this.weekNumber == weekNumber && this.dayIndex == dayIndex
    }
}
