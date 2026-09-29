package com.example.hybrid_ai_app.home.data.repository

import com.example.hybrid_ai_app.core.data.local.dao.ActiveWorkoutDao
import com.example.hybrid_ai_app.core.data.local.entity.ActiveWorkoutEntity
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import com.example.hybrid_ai_app.home.domain.repository.ActiveWorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ActiveWorkoutRepositoryImpl @Inject constructor(
    private val dao: ActiveWorkoutDao,
) : ActiveWorkoutRepository {

    // Defaults are encoded so a session written today still reads the same if a default changes
    // later; unknown keys are ignored so a downgrade does not throw the session away.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun observe(): Flow<WorkoutSession?> = dao.observe().map { row ->
        // An undecodable row (a schema this build cannot read) is treated as "no session" rather
        // than crashing every screen that observes it.
        row?.let { runCatching { json.decodeFromString<WorkoutSession>(it.sessionJson) }.getOrNull() }
    }

    override suspend fun save(session: WorkoutSession) {
        dao.upsert(
            ActiveWorkoutEntity(
                weekNumber = session.weekNumber,
                dayIndex = session.dayIndex,
                sessionJson = json.encodeToString(session),
            ),
        )
    }

    override suspend fun clear() {
        dao.clear()
    }
}
