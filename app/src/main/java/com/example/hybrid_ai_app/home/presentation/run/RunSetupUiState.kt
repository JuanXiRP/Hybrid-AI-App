package com.example.hybrid_ai_app.home.presentation.run

import com.example.hybrid_ai_app.core.data.remote.dto.ExerciseDto
import com.example.hybrid_ai_app.core.domain.model.RunStructure

sealed interface RunSetupUiState {
    data object Loading : RunSetupUiState

    /** The day could not be found in the cached plan. */
    data object Error : RunSetupUiState

    /**
     * [instruction] is the run description written in the plan, shown above the setup for
     * reference; an extra run ([isExtra]) has none, and a blank [dayName] the screen names itself.
     */
    data class Success(
        val dayName: String,
        val instruction: ExerciseDto?,
        val config: RunSetupConfig,
        val isExtra: Boolean = false,
    ) : RunSetupUiState {
        /** The guided structure, or null for a free run. */
        val structure: RunStructure? get() = config.toStructure()
        val totalSec: Int get() = structure?.totalSec() ?: 0

        /** A free run needs no duration; a guided one needs at least one second of it. */
        val canStart: Boolean get() = structure == null || totalSec > 0
    }
}
