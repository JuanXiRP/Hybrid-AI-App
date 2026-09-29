package com.example.hybrid_ai_app.core.data.repository

import com.example.hybrid_ai_app.core.data.catalog.CATALOG_IMAGE_BASE_URL
import com.example.hybrid_ai_app.core.data.catalog.CATALOG_SHA
import com.example.hybrid_ai_app.core.data.catalog.ExerciseCatalogDataSource
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.catalogExerciseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The lookups the workout screen makes against the bundled catalog. The data source (which reads
 * the asset through a `Context`) is mocked; everything the repository derives from its entries —
 * the id and name indexes, the search, the alternatives — is tested for real, on a small catalog.
 */
class ExerciseCatalogRepositoryImplTest {

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private lateinit var dataSource: ExerciseCatalogDataSource

    private val squat = catalogExerciseDto(
        id = "Barbell_Squat",
        name = "Barbell Squat",
        primaryMuscles = listOf("quadriceps"),
        images = listOf("Barbell_Squat/0.jpg"),
    )
    private val goblet = catalogExerciseDto(
        id = "Goblet_Squat",
        name = "Goblet Squat",
        equipment = "kettlebells",
        primaryMuscles = listOf("quadriceps"),
        secondaryMuscles = emptyList(),
    )
    private val legPress = catalogExerciseDto(
        id = "Leg_Press",
        name = "Leg Press",
        equipment = "machine",
        primaryMuscles = listOf("quadriceps"),
    )
    private val bench = catalogExerciseDto(
        id = "Barbell_Bench_Press",
        name = "Barbell Bench Press - Medium Grip",
        primaryMuscles = listOf("chest"),
    )
    private val stretch = catalogExerciseDto(
        id = "Quad_Stretch",
        name = "Quad Stretch",
        category = "stretching",
        primaryMuscles = listOf("quadriceps"),
    )
    private val cardio = catalogExerciseDto(
        id = "Rowing",
        name = "Rowing, Stationary",
        category = "cardio",
        primaryMuscles = listOf("lats"),
    )

    @Before
    fun setUp() {
        dataSource = mockk()
        coEvery { dataSource.load() } returns listOf(squat, goblet, legPress, bench, stretch, cardio)
    }

    private fun repository() = ExerciseCatalogRepositoryImpl(dataSource)

    // ------------------------------------------------------------------------------------
    // getById
    // ------------------------------------------------------------------------------------

    @Test
    fun `an exercise is found by its id`() = runTest {
        // Act
        val found = repository().getById("Barbell_Squat")

        // Assert
        assertEquals("Barbell Squat", found?.name)
    }

    @Test
    fun `an unknown id finds nothing`() = runTest {
        // Act & Assert
        assertNull(repository().getById("No_Such_Exercise"))
    }

    @Test
    fun `image paths become absolute URLs at the pinned commit`() = runTest {
        // Act
        val found = repository().getById("Barbell_Squat")!!

        // Assert
        assertEquals(listOf("$CATALOG_IMAGE_BASE_URL/Barbell_Squat/0.jpg"), found.imageUrls)
        assertTrue(CATALOG_IMAGE_BASE_URL.contains(CATALOG_SHA))
        assertTrue(CATALOG_IMAGE_BASE_URL.startsWith("https://"))
    }

    @Test
    fun `an entry with null force, mechanic and equipment maps through as null`() = runTest {
        // Arrange
        coEvery { dataSource.load() } returns listOf(
            catalogExerciseDto(id = "Plain", force = null, mechanic = null, equipment = null),
        )

        // Act
        val found = repository().getById("Plain")!!

        // Assert
        assertNull(found.force)
        assertNull(found.mechanic)
        assertNull(found.equipment)
    }

    @Test
    fun `the catalog is read from the data source only once`() = runTest {
        // Arrange
        val repository = repository()

        // Act
        repository.getById("Barbell_Squat")
        repository.resolve(null, "Leg Press")
        repository.search("squat", null)
        repository.alternativesFor("Barbell_Squat")
        repository.muscles()

        // Assert
        coVerify(exactly = 1) { dataSource.load() }
    }

    // ------------------------------------------------------------------------------------
    // resolve
    // ------------------------------------------------------------------------------------

    @Test
    fun `resolve prefers the exact id`() = runTest {
        // Arrange — the name points at a different exercise than the id does
        val resolved = repository().resolve(exerciseId = "Barbell_Squat", name = "Leg Press")

        // Assert
        assertEquals("Barbell_Squat", resolved?.id)
    }

    @Test
    fun `resolve falls back to the name when there is no id`() = runTest {
        // Imported exercises come without an id unless the match was unambiguous.
        // Act
        val resolved = repository().resolve(exerciseId = null, name = "Goblet Squat")

        // Assert
        assertEquals("Goblet_Squat", resolved?.id)
    }

    @Test
    fun `resolve falls back to the name when the id is not in the catalog`() = runTest {
        // A plan cached before a catalog refresh can name an id that no longer exists.
        // Act
        val resolved = repository().resolve(exerciseId = "Removed_Upstream", name = "Leg Press")

        // Assert
        assertEquals("Leg_Press", resolved?.id)
    }

    @Test
    fun `resolve matches names ignoring case, punctuation and extra whitespace`() = runTest {
        // Act
        val resolved = repository().resolve(exerciseId = null, name = "  barbell   BENCH press -  medium grip! ")

        // Assert
        assertEquals("Barbell_Bench_Press", resolved?.id)
    }

    @Test
    fun `resolve finds nothing for an unknown name and id`() = runTest {
        // Act & Assert
        assertNull(repository().resolve(exerciseId = null, name = "Made Up Movement"))
    }

    // ------------------------------------------------------------------------------------
    // search
    // ------------------------------------------------------------------------------------

    @Test
    fun `search excludes stretching and cardio`() = runTest {
        // Act
        val all = repository().search(query = "", muscle = null)

        // Assert
        assertTrue(all.none { it.category == "stretching" || it.category == "cardio" })
        assertEquals(4, all.size)
    }

    @Test
    fun `search matches a substring of the name, case-insensitively`() = runTest {
        // Act
        val results = repository().search(query = "SQUAT", muscle = null)

        // Assert
        assertEquals(listOf("Barbell Squat", "Goblet Squat"), results.map { it.name })
    }

    @Test
    fun `search puts names that start with the query first`() = runTest {
        // Arrange
        coEvery { dataSource.load() } returns listOf(
            catalogExerciseDto(id = "a", name = "Goblet Squat"),
            catalogExerciseDto(id = "b", name = "Squat Jump"),
            catalogExerciseDto(id = "c", name = "Barbell Squat"),
        )

        // Act
        val results = repository().search(query = "squat", muscle = null)

        // Assert
        assertEquals(listOf("Squat Jump", "Barbell Squat", "Goblet Squat"), results.map { it.name })
    }

    @Test
    fun `search filters by primary muscle`() = runTest {
        // Act
        val results = repository().search(query = "", muscle = "chest")

        // Assert
        assertEquals(listOf("Barbell_Bench_Press"), results.map { it.id })
    }

    @Test
    fun `search combines the query and the muscle filter`() = runTest {
        // Act
        val results = repository().search(query = "press", muscle = "quadriceps")

        // Assert
        assertEquals(listOf("Leg_Press"), results.map { it.id })
    }

    @Test
    fun `the muscle filter is case-insensitive`() = runTest {
        // Act
        val results = repository().search(query = "", muscle = "CHEST")

        // Assert
        assertEquals(1, results.size)
    }

    @Test
    fun `a search with no match is empty`() = runTest {
        // Act & Assert
        assertTrue(repository().search(query = "zzz", muscle = null).isEmpty())
    }

    // ------------------------------------------------------------------------------------
    // alternativesFor
    // ------------------------------------------------------------------------------------

    @Test
    fun `alternatives share the muscle, exclude the source and skip stretching`() = runTest {
        // Act
        val alternatives = repository().alternativesFor("Barbell_Squat")

        // Assert
        assertEquals(setOf("Goblet_Squat", "Leg_Press"), alternatives.map { it.id }.toSet())
    }

    @Test
    fun `alternatives are honoured up to the limit`() = runTest {
        // Act
        val alternatives = repository().alternativesFor("Barbell_Squat", limit = 1)

        // Assert
        assertEquals(1, alternatives.size)
    }

    @Test
    fun `an unknown exercise has no alternatives`() = runTest {
        // Act & Assert
        assertTrue(repository().alternativesFor("No_Such_Exercise").isEmpty())
    }

    // ------------------------------------------------------------------------------------
    // muscles
    // ------------------------------------------------------------------------------------

    @Test
    fun `the muscle list is distinct, sorted, and built from strength-style exercises only`() = runTest {
        // Act
        val muscles = repository().muscles()

        // Assert — "lats" belongs only to the cardio entry, so it is not offered
        assertEquals(listOf("chest", "quadriceps"), muscles)
    }
}
