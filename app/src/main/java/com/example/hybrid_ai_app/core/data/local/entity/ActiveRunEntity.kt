package com.example.hybrid_ai_app.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The run in progress, if any. One row at most, so the id is a constant.
 *
 * The structure is stored as JSON ([structureJson], null for a free run) because nothing queries
 * into it. The points live in their own table ([ActiveRunPointEntity]) so a fix every few seconds
 * is one small insert, not a rewrite of the whole path.
 */
@Entity(tableName = "active_run")
data class ActiveRunEntity(
    @PrimaryKey val id: String = ACTIVE_RUN_ID,
    val clientId: String,
    val weekNumber: Int,
    val dayIndex: Int,
    val isExtra: Boolean,
    val title: String,
    val structureJson: String?,
    val startedAt: Long,
    val accumulatedMs: Long,
    // Wall-clock moment the clock last resumed; null while paused.
    val resumedAt: Long?,
) {
    companion object {
        const val ACTIVE_RUN_ID = "active"
    }
}

/** One GPS fix of the run in progress. The autoincrement id is the order the fixes arrived in. */
@Entity(tableName = "active_run_points")
data class ActiveRunPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lat: Double,
    val lng: Double,
)
