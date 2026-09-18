package com.example.hybrid_ai_app.core.data.local.converter

import com.example.hybrid_ai_app.testing.dayDto
import com.example.hybrid_ai_app.testing.exerciseDto
import com.example.hybrid_ai_app.testing.loggedExerciseEntity
import com.example.hybrid_ai_app.testing.weekDto
import org.junit.Assert.assertEquals
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
    fun `log rows do NOT tolerate a new field, unlike plan rows`() {
        // The one real inconsistency between the two converters: WorkoutLogConverters uses the
        // bare `Json` default, so ignoreUnknownKeys is FALSE here while it is TRUE in
        // WorkoutPlanConverters. Adding a property to LoggedExerciseEntity therefore makes every
        // existing row throw, and the catch above turns that into a silent loss of the user's
        // logged sets — the one place in the app reading data the user typed in themselves.
        //
        // Pinned as current behaviour so the asymmetry is visible and deliberate. Fixing it means
        // giving this converter `Json { ignoreUnknownKeys = true }`, which is a data-durability
        // change and belongs in its own commit.
        // Arrange
        val rowFromNewerBuild = """[{"name":"Back Squat","sets":"4","reps":"6","weight":"100",""" +
            """"rpe":"8","tempo":"3-1-1"}]"""

        // Act
        val restored = logConverters.toLoggedExerciseList(rowFromNewerBuild)

        // Assert
        assertTrue(
            "the unknown 'tempo' field is not tolerated, so the row is dropped entirely",
            restored.isEmpty(),
        )
    }

    @Test
    fun `a log row missing a required field is dropped rather than partially restored`() {
        // Arrange — `weight` absent, and LoggedExerciseEntity gives it no default
        val incomplete = """[{"name":"Back Squat","sets":"4","reps":"6","rpe":"8"}]"""

        // Act
        val restored = logConverters.toLoggedExerciseList(incomplete)

        // Assert
        assertEquals(emptyList<Any>(), restored)
    }
}
