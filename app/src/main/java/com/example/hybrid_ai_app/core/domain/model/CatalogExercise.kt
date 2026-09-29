package com.example.hybrid_ai_app.core.domain.model

/**
 * An exercise from the bundled free-exercise-db catalog.
 *
 * Catalog text (names, instructions, muscles) is English only; translating it is a known
 * limitation, not an oversight.
 */
data class CatalogExercise(
    val id: String,
    val name: String,
    val force: String?,
    val level: String?,
    val mechanic: String?,
    val equipment: String?,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val instructions: List<String>,
    val category: String?,
    val imageUrls: List<String>,
)
