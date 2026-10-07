package com.example.hybrid_ai_app.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 to v4: logs learn their identity and what they were, and the session in progress gets a table.
 *
 * Every statement here has to produce exactly what Room would generate from the entities, or the
 * schema validation at first open fails: column types and nullability for `workout_logs`, the
 * unique index name `index_workout_logs_clientId`, and the `active_workout` table. The exported
 * schema in `app/schemas/` is the reference to compare against.
 *
 * Two details worth knowing:
 *  - `ALTER TABLE ... ADD COLUMN ... NOT NULL` needs a default in SQLite. The defaults below
 *    (`''` and `0`) exist only to satisfy that; Room ignores a database-side default when the
 *    entity declares none, and `clientId` is backfilled straight after.
 *  - The backfilled `clientId` must be UUID-shaped, because the backend's
 *    `PUT /api/workouts/strength/:clientId` rejects anything else. `randomblob` is evaluated per
 *    row, so every existing log gets its own. The unique index is created only after the backfill.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_logs ADD COLUMN clientId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE workout_logs ADD COLUMN title TEXT")
        db.execSQL("ALTER TABLE workout_logs ADD COLUMN workoutType TEXT")
        db.execSQL("ALTER TABLE workout_logs ADD COLUMN startedAt INTEGER")
        db.execSQL("ALTER TABLE workout_logs ADD COLUMN durationSec INTEGER")
        db.execSQL("ALTER TABLE workout_logs ADD COLUMN notes TEXT")
        db.execSQL("ALTER TABLE workout_logs ADD COLUMN syncPending INTEGER NOT NULL DEFAULT 0")

        db.execSQL(
            """
            UPDATE workout_logs SET clientId = lower(
                hex(randomblob(4)) || '-' ||
                hex(randomblob(2)) || '-4' ||
                substr(hex(randomblob(2)), 2) || '-' ||
                substr('89ab', 1 + (abs(random()) % 4), 1) ||
                substr(hex(randomblob(2)), 2) || '-' ||
                hex(randomblob(6))
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_workout_logs_clientId ON workout_logs (clientId)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS active_workout (" +
                "id TEXT NOT NULL, " +
                "weekNumber INTEGER NOT NULL, " +
                "dayIndex INTEGER NOT NULL, " +
                "sessionJson TEXT NOT NULL, " +
                "PRIMARY KEY(id))",
        )
    }
}

/**
 * v4 to v5: logs can be extra sessions, and the run in progress gets two tables.
 *
 * As with [MIGRATION_3_4], every statement mirrors what Room generates from the entities (compare
 * with `app/schemas/.../5.json`). `isExtra` gets a database default only because SQLite needs one
 * to add a NOT NULL column; every existing log is a planned one, which is exactly what 0 says.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_logs ADD COLUMN isExtra INTEGER NOT NULL DEFAULT 0")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS active_run (" +
                "id TEXT NOT NULL, " +
                "clientId TEXT NOT NULL, " +
                "weekNumber INTEGER NOT NULL, " +
                "dayIndex INTEGER NOT NULL, " +
                "isExtra INTEGER NOT NULL, " +
                "title TEXT NOT NULL, " +
                "structureJson TEXT, " +
                "startedAt INTEGER NOT NULL, " +
                "accumulatedMs INTEGER NOT NULL, " +
                "resumedAt INTEGER, " +
                "PRIMARY KEY(id))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS active_run_points (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "lat REAL NOT NULL, " +
                "lng REAL NOT NULL)",
        )
    }
}
