package com.example.hybrid_ai_app.core.domain.catalog

import com.example.hybrid_ai_app.testing.catalogExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ranking behind "Alternative exercise": what to offer when the machine an athlete wanted is
 * taken. The scorer is a pure function, so the whole contract is pinned here with no fixtures.
 */
class AlternativeScorerTest {

    private val source = catalogExercise(
        id = "source",
        name = "Barbell Squat",
        force = "push",
        mechanic = "compound",
        level = "intermediate",
        primaryMuscles = listOf("quadriceps"),
        secondaryMuscles = listOf("glutes", "hamstrings"),
        equipment = "barbell",
    )

    // ------------------------------------------------------------------------------------
    // Who is a candidate
    // ------------------------------------------------------------------------------------

    @Test
    fun `an exercise sharing a primary muscle is a candidate`() {
        // Arrange
        val candidate = catalogExercise(id = "leg-press", primaryMuscles = listOf("quadriceps", "calves"))

        // Act & Assert
        assertTrue(AlternativeScorer.isCandidate(source, candidate))
    }

    @Test
    fun `an exercise sharing no primary muscle is not a candidate, however similar otherwise`() {
        // Arrange — same mechanic, force and level, and even the source's muscle as a *secondary*
        val candidate = catalogExercise(
            id = "rdl",
            mechanic = "compound",
            force = "push",
            level = "intermediate",
            primaryMuscles = listOf("hamstrings"),
            secondaryMuscles = listOf("quadriceps"),
        )

        // Act & Assert
        assertFalse(AlternativeScorer.isCandidate(source, candidate))
    }

    @Test
    fun `the source itself is never offered`() {
        // Act & Assert
        assertFalse(AlternativeScorer.isCandidate(source, source))
    }

    @Test
    fun `stretching and cardio are never offered`() {
        // Arrange
        val stretch = catalogExercise(id = "stretch", category = "stretching", primaryMuscles = listOf("quadriceps"))
        val cardio = catalogExercise(id = "cardio", category = "cardio", primaryMuscles = listOf("quadriceps"))

        // Act & Assert
        assertFalse(AlternativeScorer.isCandidate(source, stretch))
        assertFalse(AlternativeScorer.isCandidate(source, cardio))
    }

    @Test
    fun `strongman, plyometrics and olympic lifts are still candidates`() {
        // Arrange
        val categories = listOf("strongman", "plyometrics", "olympic weightlifting", "powerlifting")

        // Act & Assert
        categories.forEach { category ->
            val candidate = catalogExercise(id = category, category = category, primaryMuscles = listOf("quadriceps"))
            assertTrue(category, AlternativeScorer.isCandidate(source, candidate))
        }
    }

    // ------------------------------------------------------------------------------------
    // The score
    // ------------------------------------------------------------------------------------

    @Test
    fun `each shared primary muscle is worth five`() {
        // Arrange
        val one = catalogExercise(primaryMuscles = listOf("quadriceps"), mechanic = null, force = null, level = null, secondaryMuscles = emptyList())
        val src = source.copy(primaryMuscles = listOf("quadriceps", "glutes"))
        val two = one.copy(primaryMuscles = listOf("quadriceps", "glutes"))

        // Act & Assert
        assertEquals(5, AlternativeScorer.score(src, one))
        assertEquals(10, AlternativeScorer.score(src, two))
    }

    @Test
    fun `the same mechanic and the same force are worth two each`() {
        // Arrange
        val base = catalogExercise(mechanic = null, force = null, level = null, secondaryMuscles = emptyList())

        // Act
        val withMechanic = AlternativeScorer.score(source, base.copy(mechanic = "compound"))
        val withForce = AlternativeScorer.score(source, base.copy(force = "push"))

        // Assert — 5 for the shared primary muscle, plus 2
        assertEquals(7, withMechanic)
        assertEquals(7, withForce)
    }

    @Test
    fun `each shared secondary muscle is worth one and the same level is worth one`() {
        // Arrange
        val base = catalogExercise(mechanic = null, force = null, level = null, secondaryMuscles = emptyList())

        // Act
        val withSecondaries = AlternativeScorer.score(source, base.copy(secondaryMuscles = listOf("glutes", "hamstrings")))
        val withLevel = AlternativeScorer.score(source, base.copy(level = "intermediate"))

        // Assert
        assertEquals(5 + 2, withSecondaries)
        assertEquals(5 + 1, withLevel)
    }

    @Test
    fun `a missing mechanic, force or level on both sides is not a match`() {
        // Arrange — null equals null, which must not be read as "the same"
        val bare = catalogExercise(mechanic = null, force = null, level = null, secondaryMuscles = emptyList())
        val src = bare.copy(id = "src")

        // Act
        val score = AlternativeScorer.score(src, bare.copy(id = "other"))

        // Assert — only the shared primary muscle counts
        assertEquals(5, score)
    }

    @Test
    fun `equipment does not affect the score`() {
        // A busy machine is the reason to switch equipment, so it must not be rewarded or punished.
        // Arrange
        val sameEquipment = catalogExercise(equipment = "barbell")
        val otherEquipment = sameEquipment.copy(equipment = "machine")

        // Act & Assert
        assertEquals(
            AlternativeScorer.score(source, sameEquipment),
            AlternativeScorer.score(source, otherEquipment),
        )
    }

    // ------------------------------------------------------------------------------------
    // The ranking
    // ------------------------------------------------------------------------------------

    @Test
    fun `alternatives are ordered from most to least similar`() {
        // Arrange
        val best = catalogExercise(
            id = "best",
            name = "Front Squat",
            primaryMuscles = listOf("quadriceps"),
            secondaryMuscles = listOf("glutes"),
        )
        val middle = catalogExercise(
            id = "middle",
            name = "Leg Extensions",
            mechanic = "isolation",
            force = "push",
            level = "beginner",
            primaryMuscles = listOf("quadriceps"),
            secondaryMuscles = emptyList(),
        )
        val worst = catalogExercise(
            id = "worst",
            name = "Wall Sit",
            mechanic = null,
            force = "static",
            level = "beginner",
            primaryMuscles = listOf("quadriceps"),
            secondaryMuscles = emptyList(),
        )

        // Act
        val ranked = AlternativeScorer.rank(source, listOf(worst, best, middle))

        // Assert
        assertEquals(listOf("best", "middle", "worst"), ranked.map { it.id })
    }

    @Test
    fun `ties are broken alphabetically by name`() {
        // Arrange — identical scores
        val zebra = catalogExercise(id = "z", name = "Zercher Squat")
        val apple = catalogExercise(id = "a", name = "Anderson Squat")
        val mango = catalogExercise(id = "m", name = "Machine Squat")

        // Act
        val ranked = AlternativeScorer.rank(source, listOf(zebra, mango, apple))

        // Assert
        assertEquals(listOf("Anderson Squat", "Machine Squat", "Zercher Squat"), ranked.map { it.name })
    }

    @Test
    fun `the result is capped at the limit`() {
        // Arrange
        val catalog = (1..30).map { catalogExercise(id = "e$it", name = "Squat %02d".format(it)) }

        // Act
        val ranked = AlternativeScorer.rank(source, catalog, limit = 5)

        // Assert
        assertEquals(5, ranked.size)
    }

    @Test
    fun `the default limit is twenty`() {
        // Arrange
        val catalog = (1..30).map { catalogExercise(id = "e$it", name = "Squat %02d".format(it)) }

        // Act
        val ranked = AlternativeScorer.rank(source, catalog)

        // Assert
        assertEquals(20, ranked.size)
    }

    @Test
    fun `ranking drops the source, excluded categories and exercises with no shared muscle`() {
        // Arrange
        val catalog = listOf(
            source,
            catalogExercise(id = "stretch", category = "stretching", primaryMuscles = listOf("quadriceps")),
            catalogExercise(id = "curl", primaryMuscles = listOf("biceps")),
            catalogExercise(id = "keep", primaryMuscles = listOf("quadriceps")),
        )

        // Act
        val ranked = AlternativeScorer.rank(source, catalog)

        // Assert
        assertEquals(listOf("keep"), ranked.map { it.id })
    }
}
