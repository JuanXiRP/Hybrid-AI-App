package com.example.hybrid_ai_app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.hybrid_ai_app.core.data.local.entity.ActiveWorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveWorkoutDao {

    @Query("SELECT * FROM active_workout WHERE id = 'active' LIMIT 1")
    fun observe(): Flow<ActiveWorkoutEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ActiveWorkoutEntity)

    @Query("DELETE FROM active_workout")
    suspend fun clear()
}
