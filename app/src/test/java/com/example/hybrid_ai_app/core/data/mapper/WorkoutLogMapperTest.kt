package com.example.hybrid_ai_app.core.data.mapper

import com.example.hybrid_ai_app.testing.FIXED_TIMESTAMP
import com.example.hybrid_ai_app.testing.loggedExerciseEntity
import com.example.hybrid_ai_app.testing.loggedSetEntity
import com.example.hybrid_ai_app.testing.workoutLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A stored log as the body the backend accepts. The backend rejects the WHOLE log for one
 * out-of-range number, so most of what is pinned here is the defensive clamping between what an
 * athlete can type and what the schema allows.
 */
class WorkoutLogMapperTest {

    private fun dto(vararg exercises: com.example.hybrid_ai_app.core.data.local.entity.LoggedExerciseEntity) = workoutLogEntity(loggedExercises = exercises.toList()).toStrengthDto().exercises

    // ------------------------------------------------------------------------------------
    // Session level
    // ------------------------------------------------------------------------------------

    @Test
    fun `the session fields map across, with instants written as ISO-8601`() {
        // Arrange
        val log = workoutLogEntity(
            title = "Upper Body",
            weekNumber = 2,
            dayIndex = 0,
            timestamp = FIXED_TIMESTAMP + 3_600_000L,
            startedAt = FIXED_TIMESTAMP,
            durationSec = 3600,
            notes = "Felt strong",
        )

        // Act
        val dto = log.toStrengthDto()

        // Assert
        assertEquals("Upper Body", dto.routineType)
        assertEquals(log.clientId, dto.clientId)
        assertEquals(2, dto.weekNumber)
        assertEquals(0, dto.dayIndex)
        assertEquals("2026-09-18T11:00:00Z", dto.date)
        assertEquals("2026-09-18T10:00:00Z", dto.startedAt)
        assertEquals(3600L, dto.durationSec)
        assertEquals("Felt strong", dto.notes)
    }

    @Test
    fun `a log with no title is sent under a generic routine type, which the backend requires`() {
        // Act
        val dto = workoutLogEntity(title = null).toStrengthDto()

        // Assert
        assertEquals("Workout", dto.routineType)
    }

    @Test
    fun `absent optional session fields stay absent`() {
        // Act
        val dto = workoutLogEntity(startedAt = null, durationSec = null, notes = null).toStrengthDto()

        // Assert
        assertNull(dto.startedAt)
        assertNull(dto.durationSec)
        assertNull(dto.notes)
    }

    @Test
    fun `session notes are cut to the backend's limit`() {
        // Act
        val dto = workoutLogEntity(notes = "x".repeat(2500)).toStrengthDto()

        // Assert
        assertEquals(2000, dto.notes!!.length)
    }

    @Test
    fun `exercise notes are cut to the backend's limit`() {
        // Act
        val exercise = dto(loggedExerciseEntity(notes = "y".repeat(1500), setLogs = listOf(loggedSetEntity()))).single()

        // Assert
        assertEquals(1000, exercise.notes!!.length)
    }

    @Test
    fun `a negative duration is never sent`() {
        // Act
        val dto = workoutLogEntity(durationSec = -30).toStrengthDto()

        // Assert
        assertEquals(0L, dto.durationSec)
    }

    // ------------------------------------------------------------------------------------
    // Per-set exercises
    // ------------------------------------------------------------------------------------

    @Test
    fun `the legacy fields are derived from the working sets`() {
        // Arrange
        val exercise = loggedExerciseEntity(
            name = "Bench Press",
            exerciseId = "Barbell_Bench_Press",
            setLogs = listOf(
                loggedSetEntity(type = "warmup", weight = "40", reps = "10", targetRpe = "", actualRpe = ""),
                loggedSetEntity(weight = "80", reps = "5", targetRpe = "8", actualRpe = "8"),
                loggedSetEntity(weight = "85", reps = "3", targetRpe = "9", actualRpe = "9"),
            ),
        )

        // Act
        val sent = dto(exercise).single()

        // Assert
        assertEquals("Bench Press", sent.exerciseName)
        assertEquals("Barbell_Bench_Press", sent.exerciseId)
        assertEquals("two working sets, the warm-up excluded", 2, sent.sets)
        assertEquals("the reps of the last working set", 3, sent.reps)
        assertEquals(0.0, sent.targetWeight, 0.0)
        assertEquals("the heaviest working set", 85.0, sent.actualWeight!!, 0.0)
        assertEquals("the first working set's target", 8, sent.targetRpe)
        assertEquals("the rounded mean of the actual rpe", 9, sent.actualRpe)
    }

    @Test
    fun `an exercise of only warm-ups reports at least one set`() {
        // Arrange
        val exercise = loggedExerciseEntity(
            setLogs = listOf(loggedSetEntity(type = "warmup", weight = "40", reps = "10")),
        )

        // Act
        val sent = dto(exercise).single()

        // Assert
        assertEquals(1, sent.sets)
        assertEquals(1, sent.reps)
        assertNull(sent.actualWeight)
        assertNull(sent.actualRpe)
    }

    @Test
    fun `with no target rpe anywhere the target defaults to eight and no actual is invented`() {
        // Arrange
        val exercise = loggedExerciseEntity(
            setLogs = listOf(loggedSetEntity(weight = "80", reps = "5", targetRpe = "", actualRpe = "")),
        )

        // Act
        val sent = dto(exercise).single()

        // Assert
        assertEquals(8, sent.targetRpe)
        assertNull("an rpe the athlete never entered is not made up", sent.actualRpe)
    }

    @Test
    fun `the target rpe comes from the first working set that has one`() {
        // Arrange
        val exercise = loggedExerciseEntity(
            setLogs = listOf(
                loggedSetEntity(targetRpe = "", actualRpe = ""),
                loggedSetEntity(targetRpe = "9", actualRpe = ""),
            ),
        )

        // Act
        val sent = dto(exercise).single()

        // Assert
        assertEquals(9, sent.targetRpe)
    }

    @Test
    fun `per-set values are sent as numbers, and blanks as absent`() {
        // Arrange
        val exercise = loggedExerciseEntity(
            setLogs = listOf(
                loggedSetEntity(weight = "82,5", reps = "5", targetReps = "5", targetRpe = "8", actualRpe = "9"),
                loggedSetEntity(weight = "", reps = "", targetReps = "", targetRpe = "", actualRpe = ""),
            ),
        )

        // Act
        val sets = dto(exercise).single().setLogs!!

        // Assert
        assertEquals(82.5, sets[0].weight!!, 0.0)
        assertEquals(5, sets[0].reps)
        assertEquals("5", sets[0].targetReps)
        assertEquals(8, sets[0].targetRpe)
        assertEquals(9, sets[0].actualRpe)
        assertNull(sets[1].weight)
        assertNull(sets[1].reps)
        assertNull(sets[1].targetReps)
        assertNull(sets[1].targetRpe)
        assertNull(sets[1].actualRpe)
    }

    @Test
    fun `values the backend would reject are dropped rather than sent`() {
        // Arrange — an rpe of 11, a negative weight and fractional reps, none of which the schema
        // accepts, and any one of which would fail the whole log with a 400
        val exercise = loggedExerciseEntity(
            setLogs = listOf(loggedSetEntity(weight = "-20", reps = "5.5", targetRpe = "12", actualRpe = "11")),
        )

        // Act
        val set = dto(exercise).single().setLogs!!.single()

        // Assert
        assertNull(set.weight)
        assertNull(set.reps)
        assertNull(set.targetRpe)
        assertNull(set.actualRpe)
    }

    @Test
    fun `a set type this build does not know is sent as normal`() {
        // Arrange
        val exercise = loggedExerciseEntity(setLogs = listOf(loggedSetEntity(type = "cluster")))

        // Act
        val set = dto(exercise).single().setLogs!!.single()

        // Assert
        assertEquals("normal", set.type)
    }

    @Test
    fun `every set is sent, warm-ups included`() {
        // Arrange
        val exercise = loggedExerciseEntity(
            setLogs = listOf(loggedSetEntity(type = "warmup"), loggedSetEntity(type = "drop"), loggedSetEntity(type = "failure")),
        )

        // Act
        val sets = dto(exercise).single().setLogs!!

        // Assert
        assertEquals(listOf("warmup", "drop", "failure"), sets.map { it.type })
    }

    // ------------------------------------------------------------------------------------
    // Legacy exercises (no per-set detail)
    // ------------------------------------------------------------------------------------

    @Test
    fun `a legacy exercise sends no per-set list at all`() {
        // Act
        val sent = dto(loggedExerciseEntity(setLogs = emptyList())).single()

        // Assert
        assertNull(sent.setLogs)
    }

    @Test
    fun `a legacy exercise sends its rpe as the target only`() {
        // Act
        val sent = dto(loggedExerciseEntity(rpe = "7")).single()

        // Assert
        assertEquals(7, sent.targetRpe)
        assertNull(sent.actualRpe)
    }

    @Test
    fun `a legacy planned range reads as its first number`() {
        // Act
        val sent = dto(loggedExerciseEntity(rpe = "8-10")).single()

        // Assert
        assertEquals(8, sent.targetRpe)
    }

    @Test
    fun `legacy values that are not numbers fall back to safe defaults`() {
        // Act
        val sent = dto(
            loggedExerciseEntity(sets = "three", reps = "60s", weight = "bodyweight", rpe = "hard"),
        ).single()

        // Assert
        assertEquals(1, sent.sets)
        assertEquals(1, sent.reps)
        assertEquals(0.0, sent.actualWeight!!, 0.0)
        assertEquals(8, sent.targetRpe)
    }

    @Test
    fun `a legacy exercise keeps its catalog id and notes`() {
        // Act
        val sent = dto(loggedExerciseEntity(exerciseId = "Barbell_Squat", notes = "Belt on")).single()

        // Assert
        assertEquals("Barbell_Squat", sent.exerciseId)
        assertEquals("Belt on", sent.notes)
    }

    @Test
    fun `a log with no exercises sends an empty list`() {
        // Act
        val dto = workoutLogEntity(loggedExercises = emptyList()).toStrengthDto()

        // Assert
        assertTrue(dto.exercises.isEmpty())
    }
}
