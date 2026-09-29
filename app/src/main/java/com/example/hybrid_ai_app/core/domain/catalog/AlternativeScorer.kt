package com.example.hybrid_ai_app.core.domain.catalog

import com.example.hybrid_ai_app.core.domain.model.CatalogExercise

/**
 * Ranks catalog exercises as substitutes for another one, most similar first.
 *
 * The use case is "the machine I wanted is taken": the athlete needs something that trains the
 * same muscle, not the same equipment. That is why equipment is deliberately absent from the
 * score — a busy machine is precisely the reason to switch equipment.
 */
object AlternativeScorer {

    /** Categories that never make sense as a strength-set substitute. */
    val EXCLUDED_CATEGORIES: Set<String> = setOf("stretching", "cardio")

    private const val PER_SHARED_PRIMARY_MUSCLE = 5
    private const val SAME_MECHANIC = 2
    private const val SAME_FORCE = 2
    private const val PER_SHARED_SECONDARY_MUSCLE = 1
    private const val SAME_LEVEL = 1

    /** Whether [candidate] may be offered as a substitute for [source] at all. */
    fun isCandidate(source: CatalogExercise, candidate: CatalogExercise): Boolean = candidate.id != source.id &&
        candidate.category !in EXCLUDED_CATEGORIES &&
        candidate.primaryMuscles.any { it in source.primaryMuscles }

    fun score(source: CatalogExercise, candidate: CatalogExercise): Int {
        var total = 0
        total += PER_SHARED_PRIMARY_MUSCLE * candidate.primaryMuscles.count { it in source.primaryMuscles }
        if (source.mechanic != null && source.mechanic == candidate.mechanic) total += SAME_MECHANIC
        if (source.force != null && source.force == candidate.force) total += SAME_FORCE
        total += PER_SHARED_SECONDARY_MUSCLE * candidate.secondaryMuscles.count { it in source.secondaryMuscles }
        if (source.level != null && source.level == candidate.level) total += SAME_LEVEL
        return total
    }

    /** Best [limit] substitutes for [source], by score descending and then by name. */
    fun rank(
        source: CatalogExercise,
        catalog: List<CatalogExercise>,
        limit: Int = 20,
    ): List<CatalogExercise> = catalog
        .filter { isCandidate(source, it) }
        .sortedWith(
            compareByDescending<CatalogExercise> { score(source, it) }
                .thenBy { it.name },
        )
        .take(limit)
}
