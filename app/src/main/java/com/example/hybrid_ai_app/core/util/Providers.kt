package com.example.hybrid_ai_app.core.util

/**
 * The current time, behind an interface so a test decides what "now" is.
 *
 * A session's stopwatch, its rest countdown and the moment it finishes are all arithmetic on the
 * clock; reading `System.currentTimeMillis()` directly makes every one of them untestable without
 * sleeping.
 */
fun interface TimeProvider {
    fun nowMillis(): Long
}

/** Source of fresh identifiers (session ids, set ids), injectable for the same reason. */
fun interface IdProvider {
    fun newId(): String
}
