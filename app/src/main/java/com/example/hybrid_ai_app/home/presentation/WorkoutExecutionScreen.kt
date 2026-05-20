package com.example.hybrid_ai_app.home.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.hybrid_ai_app.tracking.LocationTrackingService
import com.example.hybrid_ai_app.tracking.WorkoutLocationManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutExecutionScreen(
    workoutId: String,
    workoutType: String,
    navController: NavController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Collecting tracking states from our safe singleton manager
    val pathPoints by WorkoutLocationManager.pathPoints.collectAsState()
    val isTracking by WorkoutLocationManager.isTracking.collectAsState()
    val elapsedTimeSec by WorkoutLocationManager.elapsedTimeSec.collectAsState()

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var mapProperties by remember { mutableStateOf(MapProperties(isMyLocationEnabled = false)) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(40.785091, -73.968285), 10f)
    }

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

    LaunchedEffect(workoutType) {
        if (workoutType == "run") {
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

    // Auto-center camera execution
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
            TopAppBar(title = { Text("Executing Track: $workoutId") })
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (workoutType == "run") {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = mapProperties,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                ) {
                    if (pathPoints.isNotEmpty()) {
                        Polyline(
                            points = pathPoints,
                            color = MaterialTheme.colorScheme.primary,
                            width = 12f
                        )
                    }
                }

                // Metrics Overlay with integrated controls and chronometer
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
                        // Chronometer and Distance Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TIME", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = formatSeconds(elapsedTimeSec),
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("GPS POINTS", style = MaterialTheme.typography.labelSmall)
                                Text(text = "${pathPoints.size}", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }

                        // Play / Pause Toggle controllers
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
                                ) {
                                    Text("PAUSE RUN")
                                }
                            } else {
                                Button(
                                    onClick = {
                                        Intent(context, LocationTrackingService::class.java).apply {
                                            action = LocationTrackingService.ACTION_RESUME
                                            context.startService(this)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("RESUME RUN")
                                }
                            }
                        }
                    }
                }
            } else {
                // Gym Layout View
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Gym Routine Logging Active", style = MaterialTheme.typography.headlineSmall)
                }
            }

            Button(
                onClick = {
                    Intent(context, LocationTrackingService::class.java).apply {
                        action = LocationTrackingService.ACTION_STOP
                        context.startService(this)
                    }
                    navController.popBackStack()
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Finish Workout")
            }
        }
    }
}

// Helper utility function to parse seconds into structured UI timeline
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