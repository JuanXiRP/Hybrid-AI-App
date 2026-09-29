package com.example.hybrid_ai_app.core.data.repository

import com.example.hybrid_ai_app.core.data.catalog.CATALOG_IMAGE_BASE_URL
import com.example.hybrid_ai_app.core.data.catalog.CatalogExerciseDto
import com.example.hybrid_ai_app.core.data.catalog.ExerciseCatalogDataSource
import com.example.hybrid_ai_app.core.domain.catalog.AlternativeScorer
import com.example.hybrid_ai_app.core.domain.model.CatalogExercise
import com.example.hybrid_ai_app.core.domain.repository.ExerciseCatalogRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private val NON_WORD = Regex("[^\\p{L}\\p{N}\\s]")
private val WHITESPACE = Regex("\\s+")

/** Lowercase, punctuation stripped, whitespace collapsed — the backend's `normalizeName`. */
internal fun normalizeExerciseName(name: String): String = name
    .lowercase()
    .replace(NON_WORD, " ")
    .replace(WHITESPACE, " ")
    .trim()

@Singleton
class ExerciseCatalogRepositoryImpl @Inject constructor(
    private val dataSource: ExerciseCatalogDataSource,
) : ExerciseCatalogRepository {

    private class Index(
        val all: List<CatalogExercise>,
        val byId: Map<String, CatalogExercise>,
        val byNormalizedName: Map<String, CatalogExercise>,
    )

    private val mutex = Mutex()

    @Volatile
    private var index: Index? = null

    // Built once, from the data source's own one-time decode. The mutex is only about not building
    // the same maps twice when several callers arrive together.
    private suspend fun index(): Index = index ?: mutex.withLock {
        index ?: buildIndex(dataSource.load()).also { index = it }
    }

    private fun buildIndex(entries: List<CatalogExerciseDto>): Index {
        val all = entries.map { it.toDomain() }
        return Index(
            all = all,
            byId = all.associateBy { it.id },
            byNormalizedName = all.associateBy { normalizeExerciseName(it.name) },
        )
    }

    override suspend fun getById(id: String): CatalogExercise? = index().byId[id]

    override suspend fun resolve(exerciseId: String?, name: String): CatalogExercise? {
        val index = index()
        return exerciseId?.let { index.byId[it] } ?: index.byNormalizedName[normalizeExerciseName(name)]
    }

    override suspend fun search(query: String, muscle: String?): List<CatalogExercise> {
        val needle = normalizeExerciseName(query)
        return index().all
            .filter { it.category !in AlternativeScorer.EXCLUDED_CATEGORIES }
            .filter { muscle == null || it.primaryMuscles.any { primary -> primary.equals(muscle, ignoreCase = true) } }
            .filter { needle.isEmpty() || normalizeExerciseName(it.name).contains(needle) }
            // Names that start with the query come first: typing "squat" should offer "Squat"
            // variants before "Goblet Squat" and friends.
            .sortedWith(
                compareByDescending<CatalogExercise> { needle.isNotEmpty() && normalizeExerciseName(it.name).startsWith(needle) }
                    .thenBy { it.name },
            )
    }

    override suspend fun alternativesFor(id: String, limit: Int): List<CatalogExercise> {
        val index = index()
        val source = index.byId[id] ?: return emptyList()
        return AlternativeScorer.rank(source, index.all, limit)
    }

    override suspend fun muscles(): List<String> = index().all
        .filter { it.category !in AlternativeScorer.EXCLUDED_CATEGORIES }
        .flatMap { it.primaryMuscles }
        .distinct()
        .sorted()

    private fun CatalogExerciseDto.toDomain() = CatalogExercise(
        id = id,
        name = name,
        force = force,
        level = level,
        mechanic = mechanic,
        equipment = equipment,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        instructions = instructions,
        category = category,
        imageUrls = images.map { "$CATALOG_IMAGE_BASE_URL/$it" },
    )
}
