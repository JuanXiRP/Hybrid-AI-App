package com.example.hybrid_ai_app.home.presentation.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.home.domain.model.SessionExercise
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import com.example.hybrid_ai_app.home.presentation.workout.components.AddExerciseSheet
import com.example.hybrid_ai_app.home.presentation.workout.components.AlternativesSheet
import com.example.hybrid_ai_app.home.presentation.workout.components.ExerciseInfoSheet
import com.example.hybrid_ai_app.home.presentation.workout.components.ExerciseRestDialog
import com.example.hybrid_ai_app.home.presentation.workout.components.ManualRestSheet
import com.example.hybrid_ai_app.home.presentation.workout.components.NoteDialog
import com.example.hybrid_ai_app.home.presentation.workout.components.ReorderSheet

/** What is layered over the session at the moment: a sheet or a dialog, at most one. */
internal sealed interface SessionOverlay {
    data object AddExercise : SessionOverlay
    data object Reorder : SessionOverlay
    data object ManualRest : SessionOverlay
    data object WorkoutNote : SessionOverlay
    data object DiscardConfirm : SessionOverlay
    data object PickStartDate : SessionOverlay
    data class PickStartTime(val dateMillis: Long) : SessionOverlay
    data class Info(val exerciseId: String) : SessionOverlay
    data class Alternatives(val exerciseId: String) : SessionOverlay
    data class ExerciseRest(val exerciseId: String) : SessionOverlay
    data class ExerciseNote(val exerciseId: String) : SessionOverlay
    data class RemoveConfirm(val exerciseId: String) : SessionOverlay
}

/**
 * Renders the current [overlay], if any, and reports through [onChange] when it should change or
 * close. An overlay about an exercise that has since disappeared (removed while the sheet was open)
 * closes itself rather than showing stale data.
 */
@Composable
internal fun SessionOverlays(
    overlay: SessionOverlay?,
    onChange: (SessionOverlay?) -> Unit,
    active: WorkoutSessionUiState.Active,
    viewModel: WorkoutSessionViewModel,
) {
    val session = active.session

    when (val current = overlay) {
        null -> Unit
        SessionOverlay.AddExercise -> AddExerciseSheet(
            search = viewModel::searchExercises,
            loadMuscles = viewModel::muscles,
            onPick = {
                onChange(null)
                viewModel.addExercise(it.id)
            },
            onDismiss = { onChange(null) },
        )
        SessionOverlay.Reorder -> ReorderSheet(
            exercises = session.exercises,
            onMove = viewModel::moveExercise,
            onDismiss = { onChange(null) },
        )
        SessionOverlay.ManualRest -> ManualRestSheet(
            choices = WorkoutSessionViewModel.REST_CHOICES_SECONDS,
            onPick = {
                onChange(null)
                viewModel.startManualRest(it)
            },
            onDismiss = { onChange(null) },
        )
        SessionOverlay.WorkoutNote -> NoteDialog(
            title = stringResource(id = R.string.session_menu_note),
            hint = stringResource(id = R.string.session_note_hint),
            initial = session.notes,
            onSave = {
                onChange(null)
                viewModel.setWorkoutNotes(it)
            },
            onDismiss = { onChange(null) },
        )
        SessionOverlay.DiscardConfirm -> DiscardDialog(
            onConfirm = {
                onChange(null)
                viewModel.discard()
            },
            onDismiss = { onChange(null) },
        )
        SessionOverlay.PickStartDate -> StartDatePickerDialog(
            initialMillis = session.startedAt,
            onDatePicked = { onChange(SessionOverlay.PickStartTime(it)) },
            onDismiss = { onChange(null) },
        )
        is SessionOverlay.PickStartTime -> StartTimePickerDialog(
            dateMillis = current.dateMillis,
            initialMillis = session.startedAt,
            onPicked = {
                onChange(null)
                viewModel.setStartedAt(it)
            },
            onDismiss = { onChange(null) },
        )
        is SessionOverlay.Info -> ForExercise(session, current.exerciseId, onGone = { onChange(null) }) { exercise ->
            ExerciseInfoSheet(
                exerciseName = exercise.name,
                entry = active.catalogByExercise[exercise.id],
                onDismiss = { onChange(null) },
            )
        }
        is SessionOverlay.Alternatives -> ForExercise(session, current.exerciseId, onGone = { onChange(null) }) { exercise ->
            AlternativesSheet(
                exerciseName = exercise.name,
                loadAlternatives = { viewModel.alternativesFor(exercise.id) },
                onPick = {
                    onChange(null)
                    viewModel.replaceExercise(exercise.id, it.id)
                },
                onDismiss = { onChange(null) },
            )
        }
        is SessionOverlay.ExerciseRest -> ForExercise(session, current.exerciseId, onGone = { onChange(null) }) { exercise ->
            ExerciseRestDialog(
                currentSeconds = exercise.restSeconds,
                choices = WorkoutSessionViewModel.REST_CHOICES_SECONDS,
                onPick = {
                    onChange(null)
                    viewModel.setExerciseRest(exercise.id, it)
                },
                onDismiss = { onChange(null) },
            )
        }
        is SessionOverlay.ExerciseNote -> ForExercise(session, current.exerciseId, onGone = { onChange(null) }) { exercise ->
            NoteDialog(
                title = exercise.name,
                hint = stringResource(id = R.string.session_exercise_note_hint),
                initial = exercise.notes,
                onSave = {
                    onChange(null)
                    viewModel.setExerciseNotes(exercise.id, it)
                },
                onDismiss = { onChange(null) },
            )
        }
        is SessionOverlay.RemoveConfirm -> RemoveExerciseDialog(
            onConfirm = {
                onChange(null)
                viewModel.removeExercise(current.exerciseId)
            },
            onDismiss = { onChange(null) },
        )
    }
}

@Composable
private fun ForExercise(
    session: WorkoutSession,
    exerciseId: String,
    onGone: () -> Unit,
    content: @Composable (SessionExercise) -> Unit,
) {
    val exercise = session.exercises.find { it.id == exerciseId }
    if (exercise == null) {
        LaunchedEffect(Unit) { onGone() }
    } else {
        content(exercise)
    }
}
