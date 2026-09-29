package com.example.hybrid_ai_app.core.data.local.converter

import com.example.hybrid_ai_app.testing.TestIds
import com.example.hybrid_ai_app.testing.dayDto
import com.example.hybrid_ai_app.testing.exerciseDto
import com.example.hybrid_ai_app.testing.loggedExerciseEntity
import com.example.hybrid_ai_app.testing.loggedSetEntity
import com.example.hybrid_ai_app.testing.weekDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Room type converters, which are where the wire DTOs become an on-disk schema.
 *
 * `WorkoutPlanEntity.weeks` is stored as a JSON string of the **core** `List<WeekDto>` — the very
 * same type the network layer deserialises. That double duty is the reason these tests matter:
 * renaming a `WeekDto`/`DayDto`/`ExerciseDto` property does not just change the wire, it makes
 * every already-persisted row fail to decode. And because both converters swallow the exception
 * and return `emptyList()`, the failure is invisible — the user's plan or logged sets simply
 * vanish. `DatabaseModule` also configures `fallbackToDestructiveMigration()`, so no schema
 * version check stands in the way either.
 */
class WorkoutConvertersTest {

    private val planConverters = WorkoutPlanConverters()
    private val logConverters = WorkoutLogConverters()

    // ------------------------------------------------------------------------------------
    // WorkoutPlanConverters — the plan weeks
    // ------------------------------------------------------------------------------------

    @Test
    fun `a plan's weeks survive a full encode-decode round trip`() {
        // Arrange
        val weeks = listOf(
            weekDto(
                weekNumber = 1,
                days = listOf(
                    dayDto(
                        dayName = "Lower Body",
                        workoutType = "strength",
                        source = "generated",
                        exercises = listOf(
                            exerciseDto(name = "Back Squat", sets = "4", reps = "6", rpe = "8"),
                        ),
                    ),
                    dayDto(dayName = "Rest", workoutType = "rest", exercises = emptyList()),
                ),
            ),
            weekDto(weekNumber = 2, days = listOf(dayDto(dayName = "Tempo Run"))),
        )

        // Act
        val stored = planConverters.fromWeekList(weeks)
        val restored = planConverters.toWeekList(stored)

        // Assert
        assertEquals(weeks, restored)
    }

    @Test
    fun `workoutType and source survive persistence because the coach depends on them`() {
        // These two fields exist only on the core DayDto and are read solely by
        // PlanContextFormatter. If they did not round-trip, the coach would lose its grounding
        // with nothing else in the app noticing.
        // Arrange
        val weeks = listOf(
            weekDto(days = listOf(dayDto(workoutType = "cardio", source = "imported"))),
        )

        // Act
        val restored = planConverters.toWeekList(planConverters.fromWeekList(weeks))

        // Assert
        assertEquals("cardio", restored.first().days.first().workoutType)
        assertEquals("imported", restored.first().days.first().source)
    }

    @Test
    fun `a null week list is stored as an empty array rather than the string null`() {
        // Arrange
        val weeks = null

        // Act
        val stored = planConverters.fromWeekList(weeks)

        // Assert
        assertEquals("[]", stored)
        assertEquals(emptyList<Any>(), planConverters.toWeekList(stored))
    }

    @Test
    fun `an unreadable plan column decodes to an empty list instead of crashing the app`() {
        // Documenting the current behaviour, not endorsing it: this catch is exactly what makes a
        // DTO rename silent. A user with a stored plan would open the app to an empty dashboard
        // and no error.
        // Arrange
        val corrupted = """[{"weekNumber":1,"renamedDays":[]}]"""

        // Act
        val restored = planConverters.toWeekList(corrupted)

        // Assert
        assertEquals(emptyList<Any>(), restored)
    }

    @Test
    fun `plan rows tolerate a field added to the DTO after they were written`() {
        // WorkoutPlanConverters configures ignoreUnknownKeys = true, so a column written by a
        // newer build that has an extra field still decodes in an older one.
        // Arrange
        val withFutureField = """[{"weekNumber":1,"days":[{"dayName":"Push",""" +
            """"workoutType":"strength","source":"generated","exercises":[],""" +
            """"deloadWeek":true}]}]"""

        // Act
        val restored = planConverters.toWeekList(withFutureField)

        // Assert
        assertEquals(1, restored.size)
        assertEquals("Push", restored.first().days.first().dayName)
    }

    @Test
    fun `a plan row cached before exerciseId existed still decodes with a null id`() {
        // exerciseId is nullable with a default; without the default every cached plan would fail
        // to decode and silently come back empty.
        // Arrange
        val legacyRow = """[{"weekNumber":1,"days":[{"dayName":"Legs","workoutType":"strength",""" +
            """"exercises":[{"name":"Back Squat","sets":"4","reps":"6","rpe":"8"}]}]}]"""

        // Act
        val restored = planConverters.toWeekList(legacyRow)

        // Assert
        val exercise = restored.first().days.first().exercises.first()
        assertEquals("Back Squat", exercise.name)
        assertNull(exercise.exerciseId)
    }

    @Test
    fun `a catalog exerciseId survives a round trip`() {
        // Arrange
        val exerciseId = TestIds.uniqueExerciseId()
        val weeks = listOf(
            weekDto(days = listOf(dayDto(exercises = listOf(exerciseDto(exerciseId = exerciseId))))),
        )

        // Act
        val restored = planConverters.toWeekList(planConverters.fromWeekList(weeks))

        // Assert
        assertEquals(exerciseId, restored.first().days.first().exercises.first().exerciseId)
    }

    @Test
    fun `a day persisted before workoutType and source existed still decodes`() {
        // Both fields carry defaults precisely so plans cached by older builds keep parsing.
        // Arrange
        val legacyRow = """[{"weekNumber":1,"days":[{"dayName":"Legs","exercises":[]}]}]"""

        // Act
        val restored = planConverters.toWeekList(legacyRow)

        // Assert
        val day = restored.first().days.first()
        assertEquals("Legs", day.dayName)
        assertEquals("rest", day.workoutType)
        assertEquals("generated", day.source)
    }

    @Test
    fun `string lists round trip and a null becomes an empty array`() {
        // Declared for an injuries column that no entity currently has; covered so the behaviour
        // is pinned if one starts using it.
        // Arrange
        val injuries = listOf("left knee", "right shoulder")

        // Act
        val stored = planConverters.fromStringList(injuries)
        val restored = planConverters.toStringList(stored)

        // Assert
        assertEquals(injuries, restored)
        assertEquals("[]", planConverters.fromStringList(null))
        assertEquals(emptyList<String>(), planConverters.toStringList("not json"))
    }

    // ------------------------------------------------------------------------------------
    // WorkoutLogConverters — the logged sets
    // ------------------------------------------------------------------------------------

    @Test
    fun `logged exercises survive a round trip with every field intact`() {
        // Arrange
        val logged = listOf(
            loggedExerciseEntity(name = "Back Squat", sets = "4", reps = "6", weight = "100", rpe = "8"),
            loggedExerciseEntity(name = "Plank", sets = "3", reps = "60s", weight = "-", rpe = "6"),
        )

        // Act
        val restored = logConverters.toLoggedExerciseList(
            logConverters.fromLoggedExerciseList(logged),
        )

        // Assert
        assertEquals(logged, restored)
    }

    @Test
    fun `an empty log list round trips as an empty array`() {
        // Arrange
        val logged = emptyList<com.example.hybrid_ai_app.core.data.local.entity.LoggedExerciseEntity>()

        // Act
        val stored = logConverters.fromLoggedExerciseList(logged)

        // Assert
        assertEquals("[]", stored)
        assertEquals(logged, logConverters.toLoggedExerciseList(stored))
    }

    @Test
    fun `an unreadable log column decodes to an empty list rather than crashing`() {
        // Arrange
        val corrupted = "{ not json"

        // Act
        val restored = logConverters.toLoggedExerciseList(corrupted)

        // Assert
        assertEquals(emptyList<Any>(), restored)
    }

    @Test
    fun `log rows tolerate a field this build does not know, like plan rows`() {
        // Both converters are lenient now. WorkoutLogConverters used to use the bare strict `Json`,
        // so a row written by a build with one more property than this one made the decode throw,
        // the catch turned that into an empty list, and the user's logged sets vanished. That was
        // the one place in the app reading data the athlete typed in themselves, and the change to
        // `ignoreUnknownKeys = true` is deliberate: a downgrade or a rollout that overlaps builds
        // must not cost anyone their history.
        // Arrange
        val rowFromNewerBuild = """[{"name":"Back Squat","sets":"4","reps":"6","weight":"100",""" +
            """"rpe":"8","tempo":"3-1-1"}]"""

        // Act
        val restored = logConverters.toLoggedExerciseList(rowFromNewerBuild)

        // Assert
        assertEquals(1, restored.size)
        assertEquals("Back Squat", restored.single().name)
        assertEquals("100", restored.single().weight)
    }

    @Test
    fun `a log row written before per-set detail existed still decodes`() {
        // The v3 shape: five string fields, no exerciseId, notes or setLogs. Every new property has
        // a default, which is what lets rows already on the device survive the upgrade.
        // Arrange
        val legacyRow = """[{"name":"Back Squat","sets":"4","reps":"6","weight":"100","rpe":"8"}]"""

        // Act
        val restored = logConverters.toLoggedExerciseList(legacyRow)

        // Assert
        val exercise = restored.single()
        assertEquals("Back Squat", exercise.name)
        assertNull(exercise.exerciseId)
        assertNull(exercise.notes)
        assertTrue(exercise.setLogs.isEmpty())
    }

    @Test
    fun `a log row missing the legacy summary fields still decodes, with them blank`() {
        // The summary fields carry defaults now, because a row written by a build that stops
        // populating them must not be dropped. Only the name is required.
        // Arrange
        val incomplete = """[{"name":"Back Squat"}]"""

        // Act
        val restored = logConverters.toLoggedExerciseList(incomplete)

        // Assert
        val exercise = restored.single()
        assertEquals("Back Squat", exercise.name)
        assertEquals("", exercise.sets)
        assertEquals("", exercise.weight)
    }

    @Test
    fun `a row with no name is dropped, because nothing can be shown for it`() {
        // Arrange
        val nameless = """[{"sets":"4","reps":"6"}]"""

        // Act
        val restored = logConverters.toLoggedExerciseList(nameless)

        // Assert
        assertEquals(emptyList<Any>(), restored)
    }

    @Test
    fun `per-set logs survive a round trip with every field intact`() {
        // Arrange
        val logged = listOf(
            loggedExerciseEntity(
                exerciseId = TestIds.uniqueExerciseId(),
                notes = "Paused reps",
                setLogs = listOf(
                    loggedSetEntity(type = "warmup", weight = "40", reps = "10", actualRpe = ""),
                    loggedSetEntity(type = "normal", weight = "82.5", reps = "5", targetRpe = "8", actualRpe = "9"),
                    loggedSetEntity(type = "failure", weight = "70", reps = "8", completed = false),
                ),
            ),
        )

        // Act
        val restored = logConverters.toLoggedExerciseList(logConverters.fromLoggedExerciseList(logged))

        // Assert
        assertEquals(logged, restored)
    }
}
