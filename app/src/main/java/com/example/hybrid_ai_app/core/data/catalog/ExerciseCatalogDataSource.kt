package com.example.hybrid_ai_app.core.data.catalog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The free-exercise-db commit the bundled `assets/exercises.json` was taken from, and the one the
 * exercise images are fetched at.
 *
 * It is the same commit the backend pins in `hybrid-ai-backend/src/data/README.md`. The two copies
 * of the catalog are refreshed together (`node scripts/sync-exercise-catalog.js <sha>` on the
 * backend, then copy the file here and update this constant): an id the backend stamps on a plan
 * that this copy lacks would leave the exercise without an info sheet or alternatives.
 */
const val CATALOG_SHA = "f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5"

/** Where the catalog's relative image paths live. Not the pinned backend host, on purpose. */
const val CATALOG_IMAGE_BASE_URL =
    "https://raw.githubusercontent.com/yuhonas/free-exercise-db/$CATALOG_SHA/exercises"

private const val CATALOG_ASSET = "exercises.json"

/**
 * One entry of `exercises.json`, as the file spells it.
 *
 * `force`, `mechanic` and `equipment` are `null` for some exercises, and every collection is
 * defaulted so a slimmer entry in a future refresh still decodes.
 */
@Serializable
data class CatalogExerciseDto(
    val id: String,
    val name: String,
    val force: String? = null,
    val level: String? = null,
    val mechanic: String? = null,
    val equipment: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val category: String? = null,
    val images: List<String> = emptyList(),
)

/**
 * Reads the bundled catalog once and keeps it.
 *
 * Lazy and off the main thread: the file is about a megabyte, so decoding it must not happen at
 * app start or on a composition. The [Mutex] makes concurrent first callers wait for a single
 * decode instead of each doing their own.
 */
@Singleton
class ExerciseCatalogDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    @Volatile
    private var cached: List<CatalogExerciseDto>? = null

    suspend fun load(): List<CatalogExerciseDto> = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            context.assets.open(CATALOG_ASSET).bufferedReader().use { reader ->
                json.decodeFromString<List<CatalogExerciseDto>>(reader.readText())
            }
        }.also { cached = it }
    }
}
