package com.example.hybrid_ai_app.home.data.mapper

import com.example.hybrid_ai_app.core.data.local.entity.LoggedSetEntity
import com.example.hybrid_ai_app.home.domain.model.SetType
import com.example.hybrid_ai_app.testing.FIXED_TIMESTAMP
import com.example.hybrid_ai_app.testing.TestIds
import com.example.hybrid_ai_app.testing.dayDto
import com.example.hybrid_ai_app.testing.exerciseDto
import com.example.hybrid_ai_app.testing.loggedExerciseEntity
import com.example.hybrid_ai_app.testing.loggedSetEntity
import com.example.hybrid_ai_app.testing.sessionExercise
import com.example.hybrid_ai_app.testing.sessionSet
import com.example.hybrid_ai_app.testing.workoutLogEntity
import com.example.hybrid_ai_app.testing.workoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three translations around a session: plan day to session, session to stored log, and stored
 * log back to a session for editing — plus the parsers that stand between what an athlete typed and
 * what the backend accepts.
 */
class WorkoutSessionMapperTest {

    /** Deterministic ids, so assertions can name the ones the mapper handed out. */
    private class Ids {
        private var next = 0
        fun next(): String = "id-${next++}"
    }

    // ------------------------------------------------------------------------------------
    // Plan day -> session
    // ------------------------------------------------------------------------------------

    @Test
    fun `a plan day becomes a session for that exact week and day`() {
        // Arrange
        val day = dayDto(dayName = "Lower Body", exercises = listOf(exerciseDto(name = "Back Squat")))
        val clientId = TestIds.uniqueClientId()

        // Act
        val session = day.toWorkoutSession(
            clientId = clientId,
            weekNumber = 3,
            dayIndex = 4,
            startedAt = FIXED_TIMESTAMP,
            newId = Ids()::next,
        )

        // Assert
        assertEquals(clientId, session.clientId)
        assertEquals(3, session.weekNumber)
        assertEquals(4, session.dayIndex)
        assertEquals("Lower Body", session.title)
        assertEquals(FIXED_TIMESTAMP, session.startedAt)
        assertEquals("Back Squat", session.exercises.single().name)
        assertNull("no rest is running yet", session.restEndsAt)
    }

    @Test
    fun `the plan's set count becomes that many empty rows`() {
        // Arrange
        val day = dayDto(exercises = listOf(exerciseDto(sets = "4", reps = "6", rpe = "8")))

        // Act
        val exercise = day.toWorkoutSession("c", 1, 0, FIXED_TIMESTAMP, Ids()::next).exercises.single()

        // Assert
        assertEquals(4, exercise.sets.size)
        exercise.sets.forEach { set ->
            assertEquals("6", set.targetReps)
            assertEquals("8", set.targetRpe)
            assertEquals("nothing the plan says is copied into what was done", "", set.weight)
            assertEquals("", set.reps)
            assertEquals("", set.actualRpe)
            assertFalse(set.completed)
            assertEquals(SetType.NORMAL, set.type)
        }
    }

    @Test
    fun `a range like 8-10 for reps is kept as the target text`() {
        // Arrange
        val day = dayDto(exercises = listOf(exerciseDto(sets = "3", reps = "8-10", rpe = "7-8")))

        // Act
        val set = day.toWorkoutSession("c", 1, 0, FIXED_TIMESTAMP, Ids()::next).exercises.single().sets.first()

        // Assert
        assertEquals("8-10", set.targetReps)
        assertEquals("7-8", set.targetRpe)
    }

    @Test
    fun `a dash from an import, meaning not stated, becomes a blank target`() {
        // Arrange
        val day = dayDto(exercises = listOf(exerciseDto(sets = "3", reps = "-", rpe = "-")))

        // Act
        val set = day.toWorkoutSession("c", 1, 0, FIXED_TIMESTAMP, Ids()::next).exercises.single().sets.first()

        // Assert
        assertEquals("", set.targetReps)
        assertEquals("", set.targetRpe)
    }

    @Test
    fun `a set count that is not a plain number falls back to three`() {
        // Arrange — "3-4", "-" and free text are all things a plan can hold
        val days = listOf("3-4", "-", "many", "").map { sets ->
            dayDto(exercises = listOf(exerciseDto(sets = sets)))
        }

        // Act
        val counts = days.map {
            it.toWorkoutSession("c", 1, 0, FIXED_TIMESTAMP, Ids()::next).exercises.single().sets.size
        }

        // Assert
        assertEquals(listOf(3, 3, 3, 3), counts)
    }

    @Test
    fun `the set count is kept between one and ten`() {
        // Arrange
        val days = listOf("0", "-2", "25").map { sets -> dayDto(exercises = listOf(exerciseDto(sets = sets))) }

        // Act
        val counts = days.map {
            it.toWorkoutSession("c", 1, 0, FIXED_TIMESTAMP, Ids()::next).exercises.single().sets.size
        }

        // Assert
        assertEquals(listOf(1, 1, 10), counts)
    }

    @Test
    fun `a catalog id is carried over, and a missing one stays missing`() {
        // Arrange
        val exerciseId = TestIds.uniqueExerciseId()
        val day = dayDto(
            exercises = listOf(
                exerciseDto(name = "Squat", exerciseId = exerciseId),
                exerciseDto(name = "Imported thing", exerciseId = null),
            ),
        )

        // Act
        val exercises = day.toWorkoutSession("c", 1, 0, FIXED_TIMESTAMP, Ids()::next).exercises

        // Assert
        assertEquals(exerciseId, exercises[0].exerciseId)
        assertNull(exercises[1].exerciseId)
    }

    @Test
    fun `every exercise and set gets a distinct id, and the default rest is two minutes`() {
        // Arrange
        val day = dayDto(exercises = listOf(exerciseDto(sets = "2"), exerciseDto(sets = "2")))

        // Act
        val session = day.toWorkoutSession("c", 1, 0, FIXED_TIMESTAMP, Ids()::next)

        // Assert
        val ids = session.exercises.flatMap { exercise -> listOf(exercise.id) + exercise.sets.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(session.exercises.all { it.restSeconds == 120 })
    }

    // ------------------------------------------------------------------------------------
    // Session -> stored log
    // ------------------------------------------------------------------------------------

    @Test
    fun `the log carries the session's identity, attribution and timing`() {
        // Arrange
        val session = workoutSession(
            clientId = TestIds.uniqueClientId(),
            weekNumber = 2,
            dayIndex = 5,
            title = "Pull Day",
            startedAt = FIXED_TIMESTAMP,
            notes = "  Slept badly  ",
        )

        // Act
        val log = session.toWorkoutLog(finishedAt = FIXED_TIMESTAMP + 2_700_000L, completeAll = true)

        // Assert
        assertEquals(session.clientId, log.clientId)
        assertEquals(2, log.weekNumber)
        assertEquals(5, log.dayIndex)
        assertEquals("Pull Day", log.title)
        assertEquals("strength", log.workoutType)
        assertEquals(FIXED_TIMESTAMP, log.startedAt)
        assertEquals(FIXED_TIMESTAMP + 2_700_000L, log.timestamp)
        assertEquals(2700L, log.durationSec)
        assertEquals("Slept badly", log.notes)
        assertTrue(log.isCompleted)
        assertTrue("stays pending until the backend confirms", log.syncPending)
        assertEquals("a new log has no row yet", 0L, log.id)
    }

    @Test
    fun `editing keeps the row id`() {
        // Act
        val log = workoutSession().toWorkoutLog(finishedAt = FIXED_TIMESTAMP, completeAll = true, id = 42)

        // Assert
        assertEquals(42L, log.id)
    }

    @Test
    fun `an extra session stays extra through its log and back`() {
        // Arrange
        val session = workoutSession(isExtra = true)

        // Act
        val log = session.toWorkoutLog(finishedAt = FIXED_TIMESTAMP, completeAll = true)
        val reopened = log.toWorkoutSession { "id" }

        // Assert
        assertTrue(log.isExtra)
        assertTrue(reopened.isExtra)
    }

    @Test
    fun `blank notes are stored as null rather than an empty string`() {
        // Act
        val log = workoutSession(notes = "   ").toWorkoutLog(finishedAt = FIXED_TIMESTAMP, completeAll = true)

        // Assert
        assertNull(log.notes)
    }

    @Test
    fun `a duration that would be negative is clamped to zero`() {
        // Arrange — a clock that went backwards, or an edit that moved the start past the end
        val session = workoutSession(startedAt = FIXED_TIMESTAMP)

        // Act
        val log = session.toWorkoutLog(finishedAt = FIXED_TIMESTAMP - 5_000L, completeAll = true)

        // Assert
        assertEquals(0L, log.durationSec)
    }

    @Test
    fun `only ticked sets are kept unless every set is to be completed`() {
        // Arrange
        val session = workoutSession(
            exercises = listOf(
                sessionExercise(
                    sets = listOf(
                        sessionSet(weight = "80", reps = "5", completed = true),
                        sessionSet(weight = "80", reps = "4", completed = false),
                    ),
                ),
            ),
        )

        // Act
        val ticked = session.toWorkoutLog(finishedAt = FIXED_TIMESTAMP, completeAll = false)
        val all = session.toWorkoutLog(finishedAt = FIXED_TIMESTAMP, completeAll = true)

        // Assert
        assertEquals(1, ticked.loggedExercises.single().setLogs.size)
        assertEquals(2, all.loggedExercises.single().setLogs.size)
    }

    @Test
    fun `an exercise with no sets left is dropped from the log`() {
        // Arrange
        val session = workoutSession(
            exercises = listOf(
                sessionExercise(name = "Done", sets = listOf(sessionSet(completed = true))),
                sessionExercise(name = "Skipped", sets = listOf(sessionSet(completed = false))),
            ),
        )

        // Act
        val log = session.toWorkoutLog(finishedAt = FIXED_TIMESTAMP, completeAll = false)

        // Assert
        assertEquals(listOf("Done"), log.loggedExercises.map { it.name })
    }

    @Test
    fun `every stored set is marked completed and keeps what was typed`() {
        // Arrange
        val session = workoutSession(
            exercises = listOf(
                sessionExercise(
                    sets = listOf(
                        sessionSet(
                            type = SetType.DROP,
                            targetReps = "8-10",
                            targetRpe = "8",
                            weight = "62.5",
                            reps = "9",
                            actualRpe = "9",
                            completed = false,
                        ),
                    ),
                ),
            ),
        )

        // Act
        val set = session.toWorkoutLog(finishedAt = FIXED_TIMESTAMP, completeAll = true)
            .loggedExercises.single().setLogs.single()

        // Assert
        assertEquals(
            LoggedSetEntity(
                type = "drop",
                weight = "62.5",
                reps = "9",
                targetReps = "8-10",
                targetRpe = "8",
                actualRpe = "9",
                completed = true,
            ),
            set,
        )
    }

    @Test
    fun `the legacy summary is derived from the working sets, not the warm-ups`() {
        // Arrange
        val session = workoutSession(
            exercises = listOf(
                sessionExercise(
                    exerciseId = "Barbell_Bench_Press",
                    name = "Bench Press",
                    notes = " Paused ",
                    sets = listOf(
                        sessionSet(type = SetType.WARMUP, weight = "40", reps = "10", completed = true),
                        sessionSet(weight = "80", reps = "5", actualRpe = "8", completed = true),
                        sessionSet(weight = "85", reps = "3", actualRpe = "9", completed = true),
                    ),
                ),
            ),
        )

        // Act
        val exercise = session.toWorkoutLog(finishedAt = FIXED_TIMESTAMP, completeAll = false).loggedExercises.single()

        // Assert
        assertEquals("Bench Press", exercise.name)
        assertEquals("Barbell_Bench_Press", exercise.exerciseId)
        assertEquals("Paused", exercise.notes)
        assertEquals("the warm-up is not counted", "2", exercise.sets)
        assertEquals("the last working set", "3", exercise.reps)
        assertEquals("the heaviest working set", "85", exercise.weight)
        assertEquals("the mean of 8 and 9, rounded", "9", exercise.rpe)
    }

    @Test
    fun `an exercise of only warm-ups still reports at least one set and no weight or rpe`() {
        // Arrange
        val session = workoutSession(
            exercises = listOf(
                sessionExercise(sets = listOf(sessionSet(type = SetType.WARMUP, weight = "40", reps = "10", completed = true))),
            ),
        )

        // Act
        val exercise = session.toWorkoutLog(finishedAt = FIXED_TIMESTAMP, completeAll = false).loggedExercises.single()

        // Assert
        assertEquals("1", exercise.sets)
        assertEquals("", exercise.reps)
        assertEquals("", exercise.weight)
        assertEquals("", exercise.rpe)
    }

    @Test
    fun `sets with no weight typed leave the summary weight blank`() {
        // Arrange
        val session = workoutSession(
            exercises = listOf(sessionExercise(sets = listOf(sessionSet(completed = true)))),
        )

        // Act
        val exercise = session.toWorkoutLog(finishedAt = FIXED_TIMESTAMP, completeAll = false).loggedExercises.single()

        // Assert
        assertEquals("", exercise.weight)
    }

    @Test
    fun `a comma decimal is understood when picking the heaviest set`() {
        // Arrange — "82,5" is 82.5, which beats 80
        val session = workoutSession(
            exercises = listOf(
                sessionExercise(
                    sets = listOf(
                        sessionSet(weight = "80", completed = true),
                        sessionSet(weight = "82,5", completed = true),
                    ),
                ),
            ),
        )

        // Act
        val exercise = session.toWorkoutLog(finishedAt = FIXED_TIMESTAMP, completeAll = false).loggedExercises.single()

        // Assert
        assertEquals("82,5", exercise.weight)
    }

    // ------------------------------------------------------------------------------------
    // Stored log -> session (editing)
    // ------------------------------------------------------------------------------------

    @Test
    fun `a stored log reopens as a session with the same identity and sets`() {
        // Arrange
        val log = workoutLogEntity(
            weekNumber = 2,
            dayIndex = 1,
            title = "Upper Body",
            startedAt = FIXED_TIMESTAMP,
            notes = "Felt strong",
            loggedExercises = listOf(
                loggedExerciseEntity(
                    name = "Bench Press",
                    exerciseId = "Barbell_Bench_Press",
                    notes = "Paused",
                    setLogs = listOf(
                        loggedSetEntity(type = "warmup", weight = "40", reps = "10", actualRpe = ""),
                        loggedSetEntity(type = "failure", weight = "80", reps = "8", targetReps = "8", targetRpe = "8", actualRpe = "10"),
                    ),
                ),
            ),
        )

        // Act
        val session = log.toWorkoutSession(Ids()::next)

        // Assert
        assertEquals(log.clientId, session.clientId)
        assertEquals(2, session.weekNumber)
        assertEquals(1, session.dayIndex)
        assertEquals("Upper Body", session.title)
        assertEquals(FIXED_TIMESTAMP, session.startedAt)
        assertEquals("Felt strong", session.notes)

        val exercise = session.exercises.single()
        assertEquals("Bench Press", exercise.name)
        assertEquals("Barbell_Bench_Press", exercise.exerciseId)
        assertEquals("Paused", exercise.notes)
        assertEquals(listOf(SetType.WARMUP, SetType.FAILURE), exercise.sets.map { it.type })
        assertEquals("80", exercise.sets[1].weight)
        assertEquals("10", exercise.sets[1].actualRpe)
        assertTrue(exercise.sets.all { it.completed })
    }

    @Test
    fun `a log with no start time reopens with its finish time as the start`() {
        // Arrange
        val log = workoutLogEntity(timestamp = FIXED_TIMESTAMP, startedAt = null)

        // Act
        val session = log.toWorkoutSession(Ids()::next)

        // Assert
        assertEquals(FIXED_TIMESTAMP, session.startedAt)
    }

    @Test
    fun `a log with no title or notes reopens with blanks`() {
        // Act
        val session = workoutLogEntity(title = null, notes = null).toWorkoutSession(Ids()::next)

        // Assert
        assertEquals("", session.title)
        assertEquals("", session.notes)
    }

    @Test
    fun `an unknown set type from a newer build reopens as a normal set`() {
        // Arrange
        val log = workoutLogEntity(
            loggedExercises = listOf(loggedExerciseEntity(setLogs = listOf(loggedSetEntity(type = "cluster")))),
        )

        // Act
        val set = log.toWorkoutSession(Ids()::next).exercises.single().sets.single()

        // Assert
        assertEquals(SetType.NORMAL, set.type)
    }

    @Test
    fun `finishing an unchanged reopened session reproduces the stored sets`() {
        // The edit round trip: reopen, save with nothing changed, get the same log back.
        // Arrange
        val original = workoutLogEntity(
            id = 9,
            title = "Upper Body",
            workoutType = "strength",
            startedAt = FIXED_TIMESTAMP,
            durationSec = 3600,
            loggedExercises = listOf(
                loggedExerciseEntity(
                    name = "Bench Press",
                    sets = "1",
                    reps = "5",
                    weight = "80",
                    rpe = "8",
                    setLogs = listOf(loggedSetEntity(weight = "80", reps = "5", actualRpe = "8")),
                ),
            ),
        )

        // Act
        val rewritten = original.toWorkoutSession(Ids()::next)
            .toWorkoutLog(finishedAt = original.timestamp, completeAll = false, id = original.id)

        // Assert
        assertEquals(original.loggedExercises.single().setLogs, rewritten.loggedExercises.single().setLogs)
        assertEquals(original.clientId, rewritten.clientId)
        assertEquals(9L, rewritten.id)
    }

    // ------------------------------------------------------------------------------------
    // Parsers
    // ------------------------------------------------------------------------------------

    @Test
    fun `weights parse with either decimal separator and reject nonsense`() {
        // Act & Assert
        assertEquals(82.5, parseWeight("82.5")!!, 0.0)
        assertEquals(82.5, parseWeight(" 82,5 ")!!, 0.0)
        assertEquals(0.0, parseWeight("0")!!, 0.0)
        assertNull(parseWeight(""))
        assertNull(parseWeight("bodyweight"))
        assertNull("negative weights are refused", parseWeight("-5"))
        assertNull(parseWeight("NaN"))
        assertNull(parseWeight("Infinity"))
    }

    @Test
    fun `reps parse as non-negative whole numbers`() {
        // Act & Assert
        assertEquals(8, parseReps("8"))
        assertEquals(0, parseReps("0"))
        assertEquals(12, parseReps(" 12 "))
        assertNull(parseReps("8.5"))
        assertNull(parseReps("-1"))
        assertNull(parseReps("AMRAP"))
        assertNull(parseReps(""))
    }

    @Test
    fun `an rpe parses from the first number and only within one to ten`() {
        // Act & Assert
        assertEquals(8, parseRpe("8"))
        assertEquals(8, parseRpe("7.5"))
        assertEquals(8, parseRpe("7,5"))
        assertEquals("a planned range reads as its first number", 8, parseRpe("8-10"))
        assertEquals(10, parseRpe("10"))
        assertEquals(1, parseRpe("1"))
        assertNull("out of range is dropped, not clamped", parseRpe("11"))
        assertNull(parseRpe("0"))
        assertNull(parseRpe("-"))
        assertNull(parseRpe(""))
        assertNull(parseRpe("hard"))
    }

    @Test
    fun `an unknown wire name reads as a normal set`() {
        // Act & Assert
        assertEquals(SetType.WARMUP, SetType.fromWireName("warmup"))
        assertEquals(SetType.DROP, SetType.fromWireName("drop"))
        assertEquals(SetType.FAILURE, SetType.fromWireName("failure"))
        assertEquals(SetType.NORMAL, SetType.fromWireName("normal"))
        assertEquals(SetType.NORMAL, SetType.fromWireName("superset"))
        assertEquals(SetType.NORMAL, SetType.fromWireName(null))
    }

    @Test
    fun `working sets exclude only warm-ups`() {
        // Arrange
        val sets = listOf(
            sessionSet(type = SetType.WARMUP),
            sessionSet(type = SetType.NORMAL),
            sessionSet(type = SetType.DROP),
            sessionSet(type = SetType.FAILURE),
        )

        // Act
        val working = sets.workingSets()

        // Assert
        assertEquals(listOf(SetType.NORMAL, SetType.DROP, SetType.FAILURE), working.map { it.type })
    }
}
