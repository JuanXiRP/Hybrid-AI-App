package com.example.hybrid_ai_app.home.data.mapper

import com.example.hybrid_ai_app.home.data.remote.dto.DayDto
import com.example.hybrid_ai_app.home.data.remote.dto.ExerciseDto
import com.example.hybrid_ai_app.home.data.remote.dto.WeekDto
import com.example.hybrid_ai_app.home.data.remote.dto.WorkoutPlanDto
import com.example.hybrid_ai_app.testing.TestIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The home feature's DTO-to-domain mapper.
 *
 * The exercise, day and week conversions are field-for-field and lossless. `startDate` is not, and
 * these tests pin its real behaviour rather than the intended behaviour — see
 * [the date tests][a plan whose startDate is in any other format silently becomes now] below.
 */
class PlanMapperTest {

    private fun exerciseDtoOf(
        name: String = "Back Squat",
        sets: String = "4",
        reps: String = "6",
        rpe: String = "8",
    ) = ExerciseDto(name = name, sets = sets, reps = reps, rpe = rpe)

    private fun dayDtoOf(
        dayName: String = "Lower Body",
        exercises: List<ExerciseDto> = listOf(exerciseDtoOf()),
    ) = DayDto(dayName = dayName, exercises = exercises)

    private fun planDtoOf(
        id: String = TestIds.uniqueObjectId(),
        startDate: String = "2026-09-18T10:00:00.000Z",
        active: Boolean = true,
        durationWeeks: Int = 8,
        goal: String = "both",
        weeks: List<WeekDto> = listOf(WeekDto(weekNumber = 1, days = listOf(dayDtoOf()))),
    ) = WorkoutPlanDto(
        id = id,
        startDate = startDate,
        active = active,
        durationWeeks = durationWeeks,
        goal = goal,
        weeks = weeks,
    )

    // ------------------------------------------------------------------------------------
    // The lossless part
    // ------------------------------------------------------------------------------------

    @Test
    fun `an exercise maps field for field`() {
        // Arrange
        val dto = exerciseDtoOf(name = "Deadlift", sets = "5", reps = "3", rpe = "9")

        // Act
        val domain = dto.toDomain()

        // Assert
        assertEquals("Deadlift", domain.name)
        assertEquals("5", domain.sets)
        assertEquals("3", domain.reps)
        assertEquals("9", domain.rpe)
    }

    @Test
    fun `the placeholder dash the import path writes survives mapping`() {
        // The backend's import prompt tells Gemini to emit "-" where the source document states
        // no value, so these fields are strings and the dash must reach the UI unchanged rather
        // than being coerced to a number or blanked.
        // Arrange
        val dto = exerciseDtoOf(sets = "-", reps = "-", rpe = "-")

        // Act
        val domain = dto.toDomain()

        // Assert
        assertEquals("-", domain.sets)
        assertEquals("-", domain.reps)
        assertEquals("-", domain.rpe)
    }

    @Test
    fun `a nested plan maps every week day and exercise`() {
        // Arrange
        val dto = planDtoOf(
            weeks = listOf(
                WeekDto(
                    weekNumber = 1,
                    days = listOf(
                        dayDtoOf(
                            dayName = "Push",
                            exercises = listOf(
                                exerciseDtoOf(name = "Bench"),
                                exerciseDtoOf(name = "Dip"),
                            ),
                        ),
                        dayDtoOf(dayName = "Rest", exercises = emptyList()),
                    ),
                ),
                WeekDto(weekNumber = 2, days = listOf(dayDtoOf(dayName = "Pull"))),
            ),
        )

        // Act
        val domain = dto.toDomain()

        // Assert
        assertEquals(2, domain.weeks.size)
        assertEquals(listOf(1, 2), domain.weeks.map { it.weekNumber })
        assertEquals(listOf("Push", "Rest"), domain.weeks[0].days.map { it.dayName })
        assertEquals(listOf("Bench", "Dip"), domain.weeks[0].days[0].exercises.map { it.name })
        assertTrue(domain.weeks[0].days[1].exercises.isEmpty())
        assertEquals("Pull", domain.weeks[1].days.single().dayName)
    }

    @Test
    fun `the active flag is renamed to isActive without changing meaning`() {
        // Arrange
        val active = planDtoOf(active = true)
        val inactive = planDtoOf(active = false)

        // Act & Assert
        assertTrue(active.toDomain().isActive)
        assertTrue(!inactive.toDomain().isActive)
    }

    @Test
    fun `identity and scalar fields carry over`() {
        // Arrange
        val planId = TestIds.uniqueObjectId()
        val dto = planDtoOf(id = planId, durationWeeks = 12, goal = "endurance")

        // Act
        val domain = dto.toDomain()

        // Assert
        assertEquals(planId, domain.id)
        assertEquals(12, domain.durationWeeks)
        assertEquals("endurance", domain.goal)
    }

    @Test
    fun `an empty plan maps to an empty week list rather than failing`() {
        // Arrange
        val dto = planDtoOf(weeks = emptyList())

        // Act
        val domain = dto.toDomain()

        // Assert
        assertTrue(domain.weeks.isEmpty())
    }

    // ------------------------------------------------------------------------------------
    // startDate — the lossy part
    // ------------------------------------------------------------------------------------

    @Test
    fun `the backend's ISO timestamp is parsed`() {
        // Arrange
        val dto = planDtoOf(startDate = "2026-09-18T10:00:00.000Z")

        // Act
        val parsed = dto.toDomain().startDate

        // Assert — the format string quotes the trailing 'Z' as a literal instead of treating it
        // as a UTC marker, and sets no TimeZone, so the instant is read in the JVM's default
        // zone. The assertion therefore has to reproduce that same local-zone interpretation.
        val sameInterpretation = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.getDefault(),
        ).parse("2026-09-18T10:00:00.000Z")
        assertEquals(sameInterpretation, parsed)
    }

    @Test
    fun `the quoted Z means the timestamp is read in the device's zone, not UTC`() {
        // Pinning the consequence explicitly: two devices in different zones derive different
        // instants from the same server response. It only shows up as an off-by-hours start date
        // rather than an error, which is why it has gone unnoticed.
        // Arrange
        val body = "2026-09-18T10:00:00.000Z"
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val madrid = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Europe/Madrid")
        }

        // Act
        val asUtc = utc.parse(body)!!
        val asMadrid = madrid.parse(body)!!

        // Assert
        assertNotEquals(
            "the same body yields different instants per zone, which is the bug being pinned",
            asUtc.time,
            asMadrid.time,
        )
    }

    @Test
    fun `a blank startDate falls back to now rather than throwing`() {
        // Arrange
        val before = System.currentTimeMillis()
        val dto = planDtoOf(startDate = "")

        // Act
        val parsed = dto.toDomain().startDate
        val after = System.currentTimeMillis()

        // Assert
        assertTrue(
            "expected a fallback to the current instant, got $parsed",
            parsed.time in before..after,
        )
    }

    @Test
    fun `a startDate in any other ISO shape silently becomes now`() {
        // The format only accepts millisecond precision with a literal trailing Z. An offset form
        // (+00:00), a second-precision form, or an epoch number all hit the catch and become
        // Date() — so the plan appears to start today. Pinned because it is a silent data loss,
        // not an error: nothing logs, nothing fails, the date is just wrong.
        // Arrange
        val unsupportedShapes = listOf(
            "2026-09-18T10:00:00+00:00",
            "2026-09-18T10:00:00Z",
            "2026-09-18",
            "1789725600000",
        )
        val before = System.currentTimeMillis()

        // Act & Assert
        unsupportedShapes.forEach { shape ->
            val parsed = planDtoOf(startDate = shape).toDomain().startDate
            assertTrue(
                "\"$shape\" should have fallen back to now, got $parsed",
                parsed.time >= before,
            )
        }
    }

    @Test
    fun `startDate cannot round trip back to the string it came from`() {
        // Consequence of the above: Date carries no format memory, so re-encoding a mapped plan
        // cannot reproduce the original body. Anything needing the server's exact string has to
        // keep the DTO, not the domain model.
        // Arrange
        val original = "2026-09-18T10:00:00+00:00"

        // Act
        val mapped: Date = planDtoOf(startDate = original).toDomain().startDate
        val reEncoded = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.getDefault(),
        ).format(mapped)

        // Assert
        assertNotEquals(original, reEncoded)
    }
}
