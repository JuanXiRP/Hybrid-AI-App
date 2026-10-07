package com.example.hybrid_ai_app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.hybrid_ai_app.core.data.local.entity.ActiveRunEntity
import com.example.hybrid_ai_app.core.data.local.entity.ActiveRunPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ActiveRunDao {

    @Query("SELECT * FROM active_run WHERE id = 'active' LIMIT 1")
    abstract fun observe(): Flow<ActiveRunEntity?>

    @Query("SELECT * FROM active_run WHERE id = 'active' LIMIT 1")
    abstract suspend fun get(): ActiveRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: ActiveRunEntity)

    @Query("SELECT * FROM active_run_points ORDER BY id")
    abstract suspend fun points(): List<ActiveRunPointEntity>

    @Insert
    abstract suspend fun insertPoint(point: ActiveRunPointEntity)

    @Query("UPDATE active_run SET accumulatedMs = :accumulatedMs, resumedAt = NULL")
    abstract suspend fun pause(accumulatedMs: Long)

    @Query("UPDATE active_run SET resumedAt = :atMillis WHERE resumedAt IS NULL")
    abstract suspend fun resume(atMillis: Long)

    @Query("DELETE FROM active_run")
    abstract suspend fun deleteRun()

    @Query("DELETE FROM active_run_points")
    abstract suspend fun deletePoints()

    /** A new run never inherits the points of the one it replaces. */
    @Transaction
    open suspend fun start(entity: ActiveRunEntity) {
        deletePoints()
        upsert(entity)
    }

    @Transaction
    open suspend fun clear() {
        deleteRun()
        deletePoints()
    }
}
