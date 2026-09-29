package com.example.hybrid_ai_app.core.domain.repository

import com.example.hybrid_ai_app.core.domain.model.CatalogExercise

/** Read access to the exercise catalog bundled with the app. */
interface ExerciseCatalogRepository {

    suspend fun getById(id: String): CatalogExercise?

    /**
     * Finds the catalog entry for a plan exercise: the exact id first, then the normalised name.
     *
     * The name fallback is what covers imported exercises, which the backend leaves without an id
     * unless the match is unambiguous. It mirrors the backend's `resolveExercise`.
     */
    suspend fun resolve(exerciseId: String?, name: String): CatalogExercise?

    /**
     * Strength-style exercises matching [query] (a case-insensitive substring of the name, blank
     * for all) and, when [muscle] is given, training it as a primary muscle. Stretching and cardio
     * are never returned.
     */
    suspend fun search(query: String, muscle: String?): List<CatalogExercise>

    /** Substitutes for the exercise with [id], most similar first. Empty for an unknown id. */
    suspend fun alternativesFor(id: String, limit: Int = 20): List<CatalogExercise>

    /** Every primary muscle the catalog knows, sorted, for the add-exercise filter. */
    suspend fun muscles(): List<String>
}
