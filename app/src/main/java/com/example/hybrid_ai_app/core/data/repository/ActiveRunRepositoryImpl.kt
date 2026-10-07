package com.example.hybrid_ai_app.core.data.repository

import com.example.hybrid_ai_app.core.data.local.dao.ActiveRunDao
import com.example.hybrid_ai_app.core.data.local.entity.ActiveRunEntity
import com.example.hybrid_ai_app.core.data.local.entity.ActiveRunPointEntity
import com.example.hybrid_ai_app.core.domain.model.ActiveRun
import com.example.hybrid_ai_app.core.domain.model.RunPoint
import com.example.hybrid_ai_app.core.domain.model.RunStructure
import com.example.hybrid_ai_app.core.domain.repository.ActiveRunRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ActiveRunRepositoryImpl @Inject constructor(
    private val dao: ActiveRunDao,
) : ActiveRunRepository {

    // Same choices as the strength session's store: defaults written out so a later default change
    // does not reinterpret a stored run, unknown keys ignored so a downgrade does not lose it.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Room re-emits on any write to the table; the service reacts to every emission, so an
    // identical row is filtered out here.
    override fun observe(): Flow<ActiveRun?> = dao.observe()
        .map { it?.toDomain() }
        .distinctUntilChanged()

    override suspend fun get(): ActiveRun? = dao.get()?.toDomain()

    override suspend fun start(run: ActiveRun) {
        dao.start(
            ActiveRunEntity(
                clientId = run.clientId,
                weekNumber = run.weekNumber,
                dayIndex = run.dayIndex,
                isExtra = run.isExtra,
                title = run.title,
                structureJson = run.structure?.let { json.encodeToString(it) },
                startedAt = run.startedAt,
                accumulatedMs = run.accumulatedMs,
                resumedAt = run.resumedAt,
            ),
        )
    }

    override suspend fun points(): List<RunPoint> = dao.points().map { RunPoint(it.lat, it.lng) }

    override suspend fun addPoint(point: RunPoint) {
        dao.insertPoint(ActiveRunPointEntity(lat = point.lat, lng = point.lng))
    }

    override suspend fun pause(accumulatedMs: Long) {
        dao.pause(accumulatedMs.coerceAtLeast(0))
    }

    override suspend fun resume(atMillis: Long) {
        dao.resume(atMillis)
    }

    override suspend fun clear() {
        dao.clear()
    }

    private fun ActiveRunEntity.toDomain(): ActiveRun = ActiveRun(
        clientId = clientId,
        weekNumber = weekNumber,
        dayIndex = dayIndex,
        isExtra = isExtra,
        title = title,
        // A structure this build cannot read degrades to a free run: the clock and the GPS path
        // matter more than the phases, and dropping the whole run would lose both.
        structure = structureJson?.let { runCatching { json.decodeFromString<RunStructure>(it) }.getOrNull() },
        startedAt = startedAt,
        accumulatedMs = accumulatedMs,
        resumedAt = resumedAt,
    )
}
