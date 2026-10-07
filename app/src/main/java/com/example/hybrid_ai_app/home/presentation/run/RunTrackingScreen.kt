package com.example.hybrid_ai_app.home.presentation.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.domain.model.ActiveRun
import com.example.hybrid_ai_app.core.domain.model.RunPoint
import com.example.hybrid_ai_app.core.domain.model.RunTimeline
import com.example.hybrid_ai_app.core.domain.model.distanceKm
import com.example.hybrid_ai_app.home.presentation.run.components.RunPhaseBanner
import com.example.hybrid_ai_app.home.presentation.run.components.RunPhaseBar
import com.example.hybrid_ai_app.home.presentation.workout.formatClock
import com.example.hybrid_ai_app.home.presentation.workout.rememberNowMillis
import com.example.hybrid_ai_app.tracking.WorkoutLocationManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

/** Street level: close enough to watch the line grow as the athlete moves. */
private const val FOLLOW_ZOOM = 17f
private const val FOLLOW_ANIMATION_MS = 1000
private const val CLOCK_REFRESH_MS = 250L

/**
 * The run in progress: the map following the athlete, what the configured run asks for right now,
 * and the tracked metrics.
 *
 * Everything shown is derived from the stored [run] — the clock from its anchors, the phase from its
 * structure — so the screen shows the same thing however many times it is recreated. Only the path
 * comes from the tracking service, live, through [WorkoutLocationManager].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunTrackingScreen(
    run: ActiveRun,
    hasLocationPermission: Boolean,
    snackbar: SnackbarHostState,
    saving: Boolean,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
) {
    val pathPoints by WorkoutLocationManager.pathPoints.collectAsState()
    val nowMillis by rememberNowMillis(CLOCK_REFRESH_MS)
    val elapsedSec = run.elapsedMs(nowMillis) / 1000L

    val timeline = remember(run.structure) { run.structure?.let(::RunTimeline) }
    val progress = timeline?.progressAt(elapsedSec.toInt())

    var confirmDiscard by remember { mutableStateOf(false) }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.run_discard_title)) },
            text = { Text(stringResource(R.string.run_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDiscard()
                }) { Text(stringResource(R.string.session_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(text = runTitle(run.title, run.isExtra), fontWeight = FontWeight.Bold) },
                // Leaving keeps the run going; the minimized bar and the notification lead back.
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.session_minimize))
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDiscard = true }, enabled = !saving) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.run_discard_cd))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            RunMap(pathPoints = pathPoints, hasLocationPermission = hasLocationPermission)

            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RunPhaseBanner(progress = progress)

                    // What the athlete configured, not what the plan's text said.
                    Text(
                        text = runStructureSummary(run.structure),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (run.structure != null && timeline != null && timeline.totalSec > 0) {
                        RunPhaseBar(
                            structure = run.structure,
                            progressFraction = elapsedSec.toFloat() / timeline.totalSec,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    RunMetrics(pathPoints = pathPoints, elapsedSec = elapsedSec)

                    if (run.isPaused) {
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) { Text(text = stringResource(id = R.string.btn_resume_run)) }
                    } else {
                        OutlinedButton(onClick = onPause) { Text(text = stringResource(id = R.string.btn_pause_run)) }
                    }
                }
            }

            Button(
                onClick = onFinish,
                enabled = !saving,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .fillMaxWidth(0.8f)
                    .height(56.dp),
            ) {
                Text(text = stringResource(id = R.string.btn_finish_workout), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * The map, zoomed to street level on the first fix and then following the athlete. Dragging the
 * map stops the following, so the athlete can look around, and a button brings it back.
 */
@Composable
private fun RunMap(pathPoints: List<LatLng>, hasLocationPermission: Boolean) {
    val cameraPositionState = rememberCameraPositionState()
    val scope = rememberCoroutineScope()
    var following by rememberSaveable { mutableStateOf(true) }
    var centered by remember { mutableStateOf(false) }
    val last = pathPoints.lastOrNull()

    // A drag or a pinch by the athlete, never a move made here, takes the camera over.
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            following = false
        }
    }

    LaunchedEffect(last, following) {
        val point = last ?: return@LaunchedEffect
        when {
            // The first fix (or a restored path) jumps straight to street level: from the default
            // world view an animation would take seconds and show nothing useful.
            !centered -> {
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(point, FOLLOW_ZOOM))
                centered = true
            }
            // Pan only, so a zoom the athlete chose by pinching is kept.
            following -> cameraPositionState.animate(CameraUpdateFactory.newLatLng(point), FOLLOW_ANIMATION_MS)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
        ) {
            if (pathPoints.size > 1) {
                Polyline(points = pathPoints, color = MaterialTheme.colorScheme.primary, width = 12f)
            }
        }

        if (last == null) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ) {
                Text(
                    text = stringResource(R.string.run_waiting_gps),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else if (!following) {
            SmallFloatingActionButton(
                onClick = {
                    following = true
                    scope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(last, FOLLOW_ZOOM), FOLLOW_ANIMATION_MS)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 104.dp),
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.run_recenter_cd))
            }
        }
    }
}

@Composable
private fun RunMetrics(pathPoints: List<LatLng>, elapsedSec: Long) {
    val distanceKm = remember(pathPoints) { pathPoints.map { RunPoint(it.latitude, it.longitude) }.distanceKm() }
    val speedKmh = if (elapsedSec > 0) distanceKm / (elapsedSec / 3600.0) else 0.0

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        Metric(label = stringResource(id = R.string.metric_time), value = formatClock(elapsedSec))
        Metric(label = stringResource(id = R.string.metric_speed), value = stringResource(R.string.run_speed_format, speedKmh))
        Metric(label = stringResource(id = R.string.metric_distance), value = stringResource(R.string.run_distance_format, distanceKm))
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(text = value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
    }
}
