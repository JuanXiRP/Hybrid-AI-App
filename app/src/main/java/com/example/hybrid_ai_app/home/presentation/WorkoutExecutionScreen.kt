package com.example.hybrid_ai_app.home.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.hybrid_ai_app.core.data.local.entity.LoggedExerciseEntity
import com.example.hybrid_ai_app.core.data.remote.dto.ExerciseDto
import com.example.hybrid_ai_app.tracking.LocationTrackingService
import com.example.hybrid_ai_app.tracking.WorkoutLocationManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutExecutionScreen(
    weekNumber: Int,
    dayIndex: Int,
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val plan = (uiState as? HomeUiState.Success)?.plan
    val currentDay = plan?.weeks?.find { it.weekNumber == weekNumber }?.days?.getOrNull(dayIndex)

    val isCardio = currentDay?.workoutType == "cardio"

    // 🟢 State Hoisting: Reactive map tracking inputs matching index positions
    val weightInputs = remember { mutableStateMapOf<Int, String>() }

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
        }
    )

    LaunchedEffect(isCardio) {
        if (isCardio) {
            WorkoutLocationManager.clearAll()
            val permissionsToRequest = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
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
                durationMs = 1000
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentDay?.dayName ?: "Executing Workout", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (currentDay == null) {
                CircularProgressIndicator()
            } else if (isCardio) {
                // MAPS / CARDIO UI
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = mapProperties,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                ) {
                    if (pathPoints.isNotEmpty()) {
                        Polyline(points = pathPoints, color = MaterialTheme.colorScheme.primary, width = 12f)
                    }
                }

                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val runInstruction = currentDay.exercises.firstOrNull()
                        if (runInstruction != null) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "TODAY'S MISSION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = runInstruction.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "${runInstruction.sets} | ${runInstruction.reps} | Target RPE: ${runInstruction.rpe}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        }

                        val distanceKm = remember(pathPoints) { calculateDistanceKm(pathPoints) }
                        val speedKmh = if (elapsedTimeSec > 0) (distanceKm / (elapsedTimeSec / 3600f)) else 0f

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TIME", style = MaterialTheme.typography.labelSmall)
                                Text(text = formatSeconds(elapsedTimeSec), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SPEED", style = MaterialTheme.typography.labelSmall)
                                Text(text = String.format("%.1f km/h", speedKmh), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("DISTANCE", style = MaterialTheme.typography.labelSmall)
                                Text(text = String.format("%.2f km", distanceKm), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isTracking) {
                                Button(
                                    onClick = {
                                        Intent(context, LocationTrackingService::class.java).apply {
                                            action = LocationTrackingService.ACTION_PAUSE
                                            context.startService(this)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) { Text("PAUSE RUN") }
                            } else {
                                Button(
                                    onClick = {
                                        Intent(context, LocationTrackingService::class.java).apply {
                                            action = LocationTrackingService.ACTION_RESUME
                                            context.startService(this)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) { Text("RESUME RUN") }
                            }
                        }
                    }
                }
            } else {
                // STRENGTH / GYM UI
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(currentDay.exercises) { index, exercise ->
                        val currentWeightValue = weightInputs[index] ?: ""
                        InteractiveExerciseCard(
                            exercise = exercise,
                            weightValue = currentWeightValue,
                            onWeightChange = { newValue -> weightInputs[index] = newValue }
                        )
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

                    // 🟢 Añadido el operador Elvis ?: emptyList() al final del bloque
                    val compiledPerformanceMetrics = currentDay?.exercises?.mapIndexed { index, exercise ->
                        val finalWeight = weightInputs[index] ?: ""
                        LoggedExerciseEntity(
                            name = exercise.name,
                            sets = exercise.sets,
                            reps = exercise.reps,
                            weight = finalWeight,
                            rpe = exercise.rpe
                        )
                    } ?: emptyList()

                    // 🟢 Triggers viewmodel logging execution down to Room & preparing Mongo Sync pipelines
                    viewModel.logCurrentWorkoutAsCompleted(metrics = compiledPerformanceMetrics)
                    navController.popBackStack()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .fillMaxWidth(0.8f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("FINISH WORKOUT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun InteractiveExerciseCard(
    exercise: ExerciseDto,
    weightValue: String,
    onWeightChange: (String) -> Unit
) {
    var isCompleted by rememberSaveable { mutableStateOf(false) }

    val containerColor by animateColorAsState(
        if (isCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant,
        label = "containerColor"
    )
    val contentColor by animateColorAsState(
        if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "contentColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(text = "Sets: ${exercise.sets}", style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.7f))
                    Text(text = "Reps: ${exercise.reps}", style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.7f))
                    Text(text = "Target RPE: ${exercise.rpe}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = weightValue,
                    onValueChange = { if (!isCompleted) onWeightChange(it) },
                    label = { Text("Weight (kg)") },
                    placeholder = { Text("0.0") },
                    enabled = !isCompleted,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth(0.7f).height(56.dp)
                )
            }

            IconButton(
                onClick = { isCompleted = !isCompleted },
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCompleted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Log exercise completion status",
                    tint = if (isCompleted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
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
            points[i].latitude, points[i].longitude,
            points[i + 1].latitude, points[i + 1].longitude,
            results
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