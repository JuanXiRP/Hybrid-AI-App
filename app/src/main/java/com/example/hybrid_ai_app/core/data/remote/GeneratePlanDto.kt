package com.example.hybrid_ai_app.core.data.remote

import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutPlanDto
import kotlinx.serialization.Serializable

@Serializable
data class GeneratePlanRequest(
    val planDuration: Int,
    val goal: String
)

/**
 * Body for `POST api/ai/import-plan`: the user already follows one half of the plan and the AI
 * writes the other half around it.
 *
 * @param providedDomain which half the user supplied — "strength" (gym) or "cardio" (running).
 *   The backend generates the complementary domain and merges both into one macrocycle.
 * @param sourceText the routine pasted as plain text; null or blank when only files were attached.
 * @param attachments PDFs or photos of the routine. At least one of [sourceText]/[attachments]
 *   must carry content, or the backend answers 400.
 */
@Serializable
data class ImportPlanRequest(
    val planDuration: Int,
    val goal: String,
    val providedDomain: String,
    val sourceText: String? = null,
    val attachments: List<PlanAttachmentDto> = emptyList()
)

/** A single file handed to Gemini, base64-encoded. Mirrors the SDK's `inlineData` part. */
@Serializable
data class PlanAttachmentDto(
    val mimeType: String,
    val data: String
)

@Serializable
data class GeneratePlanResponse(
    val success: Boolean,
    val data: WorkoutPlanDto? = null
)