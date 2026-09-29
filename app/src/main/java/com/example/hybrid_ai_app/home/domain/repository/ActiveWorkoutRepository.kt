package com.example.hybrid_ai_app.home.domain.repository

import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

/** The one workout session that may be in progress at a time. */
interface ActiveWorkoutRepository {

    /** The session in progress, or null. Emits again on every [save] and [clear]. */
    fun observe(): Flow<WorkoutSession?>

    suspend fun save(session: WorkoutSession)

    suspend fun clear()
}
