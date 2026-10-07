package com.example.hybrid_ai_app.core.domain.model

private const val DEFAULT_WARMUP_MIN = 10
private const val DEFAULT_COOLDOWN_MIN = 5
private const val DEFAULT_CONTINUOUS_MIN = 30
private const val DEFAULT_INTERVAL_SEC = 60

// Minutes are written as 30 min or 30' and seconds as 45 s or 45 sec; the patterns avoid
// backslashes so the file survives being written through a shell.
private val MINUTES = Regex("([0-9]+) *(?:min|')", RegexOption.IGNORE_CASE)
private val SECONDS = Regex("([0-9]+) *(?:sec|s)(?![a-z])", RegexOption.IGNORE_CASE)

/**
 * A starting point for the setup screen, read from the plan's free-text run instruction. The plan
 * does not carry a structure, so this is only a guess the athlete adjusts: intervals when the plan
 * names two or more sets, otherwise one continuous run whose minutes are read from the text.
 */
fun defaultRunStructure(name: String, sets: String, reps: String): RunStructure {
    val repeats = sets.trim().toIntOrNull()?.takeIf { it >= 2 }
    val main = if (repeats != null) {
        RunMainBlock.Intervals(
            repeats = repeats.coerceIn(RunLimits.MIN_REPEATS, RunLimits.MAX_REPEATS),
            workSec = intervalSeconds(reps).coerceIn(RunLimits.MIN_WORK_SEC, RunLimits.MAX_WORK_SEC),
            restSec = DEFAULT_INTERVAL_SEC,
        )
    } else {
        val minutes = minutesIn(reps) ?: minutesIn(name) ?: DEFAULT_CONTINUOUS_MIN
        RunMainBlock.Continuous(minutes.coerceIn(RunLimits.MIN_CONTINUOUS_MIN, RunLimits.MAX_CONTINUOUS_MIN) * 60)
    }
    return RunStructure(warmupSec = DEFAULT_WARMUP_MIN * 60, main = main, cooldownSec = DEFAULT_COOLDOWN_MIN * 60)
}

private fun minutesIn(text: String): Int? = MINUTES.find(text)?.groupValues?.get(1)?.toIntOrNull()

private fun intervalSeconds(reps: String): Int = minutesIn(reps)?.times(60)
    ?: SECONDS.find(reps)?.groupValues?.get(1)?.toIntOrNull()
    ?: DEFAULT_INTERVAL_SEC
