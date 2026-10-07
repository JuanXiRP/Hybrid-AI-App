package com.example.hybrid_ai_app.home.presentation.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import com.example.hybrid_ai_app.core.presentation.PremiumBottomSheet
import com.example.hybrid_ai_app.home.presentation.workout.components.ExerciseCardActions
import com.example.hybrid_ai_app.home.presentation.workout.components.RestBar
import com.example.hybrid_ai_app.navigation.Screen

/**
 * The strength workout, the way a set-by-set logger works: an exercise card per movement, each with
 * a table of sets, and a rest timer pinned to the bottom.
 *
 * It renders in two modes decided by the ViewModel's route: a live session (stopwatch, rest timer,
 * minimize) or an edit of a past workout (no timer, Save and Close).
 */
@Composable
fun WorkoutSessionScreen(
    navController: NavController,
    rootNavController: NavController,
    viewModel: WorkoutSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var paywall by remember { mutableStateOf<PremiumRequiredReason?>(null) }
    var incompleteCount by remember { mutableStateOf<Int?>(null) }

    ScreenPresenceEffect()

    // Read through stringResource, and kept current for the long-lived collector below, so a
    // language change is picked up.
    val saveFailedMessage by rememberUpdatedState(stringResource(id = R.string.session_save_failed))

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WorkoutSessionEvent.Finished -> navController.popBackStack()
                is WorkoutSessionEvent.ShowPaywall -> paywall = event.reason
                is WorkoutSessionEvent.ConfirmIncomplete -> incompleteCount = event.count
                WorkoutSessionEvent.SaveFailed -> snackbar.showSnackbar(saveFailedMessage)
            }
        }
    }

    paywall?.let { reason ->
        PremiumBottomSheet(
            reason = reason,
            onDismiss = { paywall = null },
            onSeePlans = {
                paywall = null
                rootNavController.navigate(Screen.Paywall.route)
            },
        )
    }

    when (val current = state) {
        WorkoutSessionUiState.Loading -> CenteredSpinner()

        is WorkoutSessionUiState.Error -> ErrorState(
            message = when (current.reason) {
                WorkoutSessionError.NO_PLAN_DAY -> stringResource(id = R.string.session_error_no_day)
                WorkoutSessionError.LOG_NOT_FOUND -> stringResource(id = R.string.session_error_log_missing)
                WorkoutSessionError.READ_ONLY -> stringResource(id = R.string.session_error_read_only)
            },
            onBack = { navController.popBackStack() },
        )

        is WorkoutSessionUiState.Conflict -> {
            CenteredSpinner()
            ConflictDialog(
                existingTitle = current.existing.title.ifBlank { stringResource(id = R.string.extra_strength_title) },
                onResume = { viewModel.resolveConflict(resume = true) },
                onDiscard = { viewModel.resolveConflict(resume = false) },
                onDismiss = { navController.popBackStack() },
            )
        }

        is WorkoutSessionUiState.Active -> ActiveSession(
            active = current,
            viewModel = viewModel,
            snackbar = snackbar,
            onLeave = { navController.popBackStack() },
            incompleteCount = incompleteCount,
            onIncompleteHandled = { incompleteCount = null },
        )
    }
}

@Composable
private fun ActiveSession(
    active: WorkoutSessionUiState.Active,
    viewModel: WorkoutSessionViewModel,
    snackbar: SnackbarHostState,
    onLeave: () -> Unit,
    incompleteCount: Int?,
    onIncompleteHandled: () -> Unit,
) {
    val session = active.session
    val isEdit = active.isEditMode

    var overlay by remember { mutableStateOf<SessionOverlay?>(null) }

    // The stopwatch and the rest countdown share one ticker.
    val nowMillis by rememberNowMillis()

    LiveSessionEffects(session = session, isEdit = isEdit)

    val actions = remember(viewModel) {
        ExerciseCardActions(
            onUpdateSet = viewModel::updateSet,
            onToggleSet = viewModel::toggleSetCompleted,
            onAddSet = viewModel::addSet,
            onRemoveSet = viewModel::removeSet,
            onSetType = viewModel::setType,
            onWarmupUpTo = viewModel::convertToWarmupUpTo,
            onInfo = { overlay = SessionOverlay.Info(it) },
            onAlternative = { overlay = SessionOverlay.Alternatives(it) },
            onRest = { overlay = SessionOverlay.ExerciseRest(it) },
            onNote = { overlay = SessionOverlay.ExerciseNote(it) },
            onReorder = { overlay = SessionOverlay.Reorder },
            onRemove = { exerciseId ->
                // An exercise with completed sets asks first; an untouched one just goes.
                val hasWork = (viewModel.uiState.value as? WorkoutSessionUiState.Active)
                    ?.session?.exercises?.find { it.id == exerciseId }?.sets?.any { it.completed } == true
                if (hasWork) overlay = SessionOverlay.RemoveConfirm(exerciseId) else viewModel.removeExercise(exerciseId)
            },
        )
    }

    Scaffold(
        topBar = {
            SessionTopBar(
                session = session,
                isEdit = isEdit,
                nowMillis = nowMillis,
                onLeave = onLeave,
                onFinish = viewModel::requestFinish,
                onWorkoutNote = { overlay = SessionOverlay.WorkoutNote },
                onChangeStart = { overlay = SessionOverlay.PickStartDate },
                onDiscard = { overlay = SessionOverlay.DiscardConfirm },
            )
        },
        bottomBar = {
            // No rest timer when editing a workout that is already over.
            if (!isEdit) {
                RestBar(
                    restEndsAt = session.restEndsAt,
                    restTotalSec = session.restTotalSec,
                    nowMillis = nowMillis,
                    onAdjust = viewModel::adjustRest,
                    onSkip = viewModel::skipRest,
                    onManualRest = { overlay = SessionOverlay.ManualRest },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        SessionBody(
            session = session,
            active = active,
            actions = actions,
            onEditNotes = { overlay = SessionOverlay.WorkoutNote },
            onAddExercise = { overlay = SessionOverlay.AddExercise },
            modifier = Modifier.padding(padding),
        )
    }

    // Finishing with unticked sets
    incompleteCount?.let { count ->
        IncompleteSetsDialog(
            count = count,
            onCompleteAll = {
                onIncompleteHandled()
                viewModel.finish(discardIncomplete = false)
            },
            onDiscardIncomplete = {
                onIncompleteHandled()
                viewModel.finish(discardIncomplete = true)
            },
            onCancel = onIncompleteHandled,
        )
    }

    SessionOverlays(
        overlay = overlay,
        onChange = { overlay = it },
        active = active,
        viewModel = viewModel,
    )
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text(stringResource(id = R.string.btn_back)) }
    }
}
