package com.example.hybrid_ai_app.home.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import com.example.hybrid_ai_app.core.presentation.PremiumBottomSheet
import com.example.hybrid_ai_app.home.presentation.run.RunSessionEvent
import com.example.hybrid_ai_app.home.presentation.run.RunSessionUiState
import com.example.hybrid_ai_app.home.presentation.run.RunSessionViewModel
import com.example.hybrid_ai_app.home.presentation.run.RunSetupScreen
import com.example.hybrid_ai_app.home.presentation.run.RunSetupUiState
import com.example.hybrid_ai_app.home.presentation.run.RunSetupViewModel
import com.example.hybrid_ai_app.home.presentation.run.RunTrackingScreen
import com.example.hybrid_ai_app.home.presentation.run.runTitle
import com.example.hybrid_ai_app.home.presentation.workout.SessionLinks
import com.example.hybrid_ai_app.home.presentation.workout.WorkoutSessionScreen
import com.example.hybrid_ai_app.navigation.Screen
import com.example.hybrid_ai_app.tracking.LocationTrackingService

/**
 * The entry point of a workout day: strength days open the set-by-set session screen, cardio days
 * the run flow below.
 *
 * The day is looked up in the cached plan here, and only to decide which screen to show; both
 * screens load their own state from their route.
 */
@Composable
fun WorkoutExecutionScreen(
    weekNumber: Int,
    dayIndex: Int,
    navController: NavController,
    rootNavController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val day = (uiState as? HomeUiState.Success)?.plan?.weeks?.find { it.weekNumber == weekNumber }?.days?.getOrNull(dayIndex)

    when {
        day == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        day.workoutType == "cardio" -> RunFlow(navController, rootNavController)
        else -> WorkoutSessionScreen(navController = navController, rootNavController = rootNavController)
    }
}

/** A run added on top of the plan (`extra_workout/run`): the same flow, with no plan day behind it. */
@Composable
fun ExtraRunScreen(navController: NavController, rootNavController: NavController) {
    RunFlow(navController, rootNavController)
}

/**
 * A run, from setup to finish. Which part shows is decided by the stored run, not by anything held
 * in the composition: arriving here while this screen's run is in progress — after leaving the
 * screen, after the activity was recreated, after the process was killed — shows the run as it is,
 * and never starts it again.
 */
@Composable
private fun RunFlow(
    navController: NavController,
    rootNavController: NavController,
    sessionViewModel: RunSessionViewModel = hiltViewModel(),
    setupViewModel: RunSetupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by sessionViewModel.uiState.collectAsState()
    val setupState by setupViewModel.uiState.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    var paywallReason by remember { mutableStateOf<PremiumRequiredReason?>(null) }
    // True from the tap on Finish until the outcome is known, so a double tap cannot save twice.
    var saving by remember { mutableStateOf(false) }
    val saveFailedMessage by rememberUpdatedState(stringResource(id = R.string.session_save_failed))

    LaunchedEffect(Unit) {
        sessionViewModel.events.collect { event ->
            when (event) {
                RunSessionEvent.Finished -> navController.popBackStack()
                RunSessionEvent.SaveFailed -> {
                    saving = false
                    snackbar.showSnackbar(saveFailedMessage)
                }
                is RunSessionEvent.ShowPaywall -> {
                    saving = false
                    paywallReason = event.reason
                }
                is RunSessionEvent.OpenRun -> navController.navigate(SessionLinks.routeFor(event.run)) {
                    navController.currentDestination?.route?.let { current -> popUpTo(current) { inclusive = true } }
                }
            }
        }
    }

    paywallReason?.let { reason ->
        PremiumBottomSheet(
            reason = reason,
            onDismiss = { paywallReason = null },
            onSeePlans = {
                paywallReason = null
                rootNavController.navigate(Screen.Paywall.route)
            },
        )
    }

    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { granted ->
            hasLocationPermission = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val setup = setupViewModel.uiState.value as? RunSetupUiState.Success
            if (hasLocationPermission && setup != null) sessionViewModel.start(setup.structure)
        },
    )

    when (val current = state) {
        RunSessionUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        RunSessionUiState.Setup -> RunSetupScreen(
            viewModel = setupViewModel,
            onBack = { navController.popBackStack() },
            onStart = {
                val setup = setupState as? RunSetupUiState.Success ?: return@RunSetupScreen
                if (hasLocationPermission(context)) {
                    hasLocationPermission = true
                    sessionViewModel.start(setup.structure)
                } else {
                    permissionLauncher.launch(requiredPermissions())
                }
            },
        )

        is RunSessionUiState.Running -> {
            // Every time a run is shown, make sure it is being tracked: after a process death this
            // is what brings the service back. The service ignores it when it already is.
            LaunchedEffect(current.run.clientId) {
                if (hasLocationPermission(context)) LocationTrackingService.restore(context)
            }
            RunTrackingScreen(
                run = current.run,
                hasLocationPermission = hasLocationPermission,
                snackbar = snackbar,
                saving = saving,
                onBack = { navController.popBackStack() },
                onPause = sessionViewModel::pause,
                onResume = sessionViewModel::resume,
                onFinish = {
                    saving = true
                    sessionViewModel.finish()
                },
                onDiscard = sessionViewModel::discard,
            )
        }

        is RunSessionUiState.Conflict -> AlertDialog(
            onDismissRequest = { navController.popBackStack() },
            title = { Text(stringResource(R.string.session_conflict_title)) },
            text = {
                Text(stringResource(R.string.session_conflict_message, runTitle(current.existing.title, current.existing.isExtra)))
            },
            confirmButton = {
                TextButton(onClick = { sessionViewModel.resolveConflict(resume = true) }) {
                    Text(stringResource(R.string.session_conflict_resume))
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionViewModel.resolveConflict(resume = false) }) {
                    Text(stringResource(R.string.session_conflict_discard))
                }
            },
        )
    }
}

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun hasLocationPermission(context: Context): Boolean = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
