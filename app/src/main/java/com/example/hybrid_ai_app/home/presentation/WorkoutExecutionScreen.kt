package com.example.hybrid_ai_app.home.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.data.local.entity.LoggedExerciseEntity
import com.example.hybrid_ai_app.core.presentation.PremiumBottomSheet
import com.example.hybrid_ai_app.home.presentation.workout.WorkoutSessionScreen
import com.example.hybrid_ai_app.navigation.Screen
import com.example.hybrid_ai_app.tracking.LocationTrackingService
import com.example.hybrid_ai_app.tracking.WorkoutLocationManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

/**
 * The entry point of a workout day: strength days open the set-by-set session screen, everything
 * else keeps the GPS run-tracking screen below.
 *
 * The day is looked up in the cached plan here, and only to decide which screen to show; the
 * session screen loads its own state from its route.
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
        day.workoutType == "cardio" -> CardioWorkoutScreen(weekNumber, dayIndex, navController, rootNavController, viewModel)
        else -> WorkoutSessionScreen(navController = navController, rootNavController = rootNavController)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardioWorkoutScreen(
    weekNumber: Int,
    dayIndex: Int,
    navController: NavController,
    rootNavController: NavController,
    viewModel: HomeViewModel,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val premiumPrompt by viewModel.premiumPrompt.collectAsState()

    // Shares HomeViewModel, so the read-only guard in logCurrentWorkoutAsCompleted covers the
    // "finish session" button here too.
    premiumPrompt?.let { reason ->
        PremiumBottomSheet(
            reason = reason,
            onDismiss = viewModel::dismissPremiumPrompt,
            onSeePlans = {
                viewModel.dismissPremiumPrompt()
                rootNavController.navigate(Screen.Paywall.route)
            },
        )
    }

    val plan = (uiState as? HomeUiState.Success)?.plan
    val currentDay = plan?.weeks?.find { it.weekNumber == weekNumber }?.days?.getOrNull(dayIndex)

    val isCardio = currentDay?.workoutType == "cardio"

    // Tracking States
    val pathPoints by WorkoutLocationManager.pathPoints.collectAsState()
    val isTracking by WorkoutLocationManager.isTracking.collectAsState()
    val elapsedTimeSec by WorkoutLocationManager.elapsedTimeSec.collectAsState()

    var mapProperties by remember { mutableStateOf(MapProperties(isMyLocationEnabled = false)) }
    val cameraPositionState = rememberCameraPositionState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineLocationGranted || coarseLocationGranted) {
                mapProperties = mapProperties.copy(isMyLocationEnabled = true)
                Intent(context, LocationTrackingService::class.java).apply {
                    action = LocationTrackingService.ACTION_START
                    context.startService(this)
                }
            }
        },
    )

    LaunchedEffect(isCardio) {
        if (isCardio) {
            WorkoutLocationManager.clearAll()
            val permissionsToRequest = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            val allGranted = permissionsToRequest.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

            if (allGranted) {
                mapProperties = mapProperties.copy(isMyLocationEnabled = true)
                Intent(context, LocationTrackingService::class.java).apply {
                    action = LocationTrackingService.ACTION_START
                    context.startService(this)
                }
            } else {
                locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        }
    }

    LaunchedEffect(pathPoints.size) {
        if (pathPoints.isNotEmpty() && isTracking) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLng(pathPoints.last()),
                durationMs = 1000,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = currentDay?.dayName ?: stringResource(id = R.string.executing_workout_fallback), fontWeight = FontWeight.Bold) },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            if (currentDay == null) {
                CircularProgressIndicator()
            } else if (isCardio) {
                // MAPS / CARDIO UI
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = mapProperties,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false),
                ) {
                    if (pathPoints.isNotEmpty()) {
                        Polyline(points = pathPoints, color = MaterialTheme.colorScheme.primary, width = 12f)
                    }
                }

                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val runInstruction = currentDay.exercises.firstOrNull()
                        if (runInstruction != null) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(id = R.string.todays_mission_header),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = runInstruction.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = stringResource(
                                        id = R.string.cardio_metrics_format,
                                        runInstruction.sets,
                                        runInstruction.reps,
                                        runInstruction.rpe,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        }

                        val distanceKm = remember(pathPoints) { calculateDistanceKm(pathPoints) }
                        val speedKmh = if (elapsedTimeSec > 0) (distanceKm / (elapsedTimeSec / 3600f)) else 0f

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = stringResource(id = R.string.metric_time), style = MaterialTheme.typography.labelSmall)
                                Text(text = formatSeconds(elapsedTimeSec), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = stringResource(id = R.string.metric_speed), style = MaterialTheme.typography.labelSmall)
                                Text(text = String.format("%.1f km/h", speedKmh), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = stringResource(id = R.string.metric_distance), style = MaterialTheme.typography.labelSmall)
                                Text(text = String.format("%.2f km", distanceKm), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isTracking) {
                                Button(
                                    onClick = {
                                        Intent(context, LocationTrackingService::class.java).apply {
                                            action = LocationTrackingService.ACTION_PAUSE
                                            context.startService(this)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                ) { Text(text = stringResource(id = R.string.btn_pause_run)) }
                            } else {
                                Button(
                                    onClick = {
                                        Intent(context, LocationTrackingService::class.java).apply {
                                            action = LocationTrackingService.ACTION_RESUME
                                            context.startService(this)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                ) { Text(text = stringResource(id = R.string.btn_resume_run)) }
                            }
                        }
                    }
                }
            }

            // Global Finish Button
            Button(
                onClick = {
                    if (isCardio) {
                        Intent(context, LocationTrackingService::class.java).apply {
                            action = LocationTrackingService.ACTION_STOP
                            context.startService(this)
                        }
                    }

                    // A run has no weight to log; the day's own instruction is what gets recorded.
                    val compiledPerformanceMetrics = currentDay?.exercises?.map { exercise ->
                        LoggedExerciseEntity(
                            name = exercise.name,
                            sets = exercise.sets,
                            reps = exercise.reps,
                            weight = "",
                            rpe = exercise.rpe,
                        )
                    } ?: emptyList()

                    viewModel.logCurrentWorkoutAsCompleted(metrics = compiledPerformanceMetrics)
                    navController.popBackStack()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .fillMaxWidth(0.8f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(text = stringResource(id = R.string.btn_finish_workout), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun calculateDistanceKm(points: List<LatLng>): Float {
    var totalDistanceMeters = 0f
    if (points.size < 2) return 0f

    for (i in 0 until points.size - 1) {
        val results = FloatArray(1)
        Location.distanceBetween(
            points[i].latitude,
            points[i].longitude,
            points[i + 1].latitude,
            points[i + 1].longitude,
            results,
        )
        totalDistanceMeters += results[0]
    }
    return totalDistanceMeters / 1000f
}

private fun formatSeconds(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}
