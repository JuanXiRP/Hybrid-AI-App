package com.example.hybrid_ai_app.home.presentation.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import com.example.hybrid_ai_app.home.service.WorkoutSessionService
import kotlinx.coroutines.delay

/** Publishes whether this screen is resumed, which the foreground service reads to avoid doubling a cue. */
@Composable
internal fun ScreenPresenceEffect() {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> SessionScreenPresence.isResumed = true
                Lifecycle.Event.ON_PAUSE -> SessionScreenPresence.isResumed = false
                else -> Unit
            }
        }
        SessionScreenPresence.isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            SessionScreenPresence.isResumed = false
        }
    }
}

/** What only a live session does: play the end-of-rest cue and ask to post notifications. */
@Composable
internal fun LiveSessionEffects(session: WorkoutSession, isEdit: Boolean) {
    if (isEdit) return
    val context = LocalContext.current

    // Keeps the stopwatch and the rest countdown alive in the notification shade while the phone is
    // locked. Started here, from the foreground, because Android refuses to start a foreground
    // service from the background; it stops itself when the session is cleared.
    LaunchedEffect(session.clientId) { WorkoutSessionService.start(context) }

    // The cue when a rest runs out while this screen is showing. When it is not (the workout is
    // minimized, or the app is in the background) the foreground service plays it instead.
    LaunchedEffect(session.restEndsAt) {
        val endsAt = session.restEndsAt ?: return@LaunchedEffect
        val wait = endsAt - System.currentTimeMillis()
        if (wait > 0) {
            delay(wait)
            if (SessionScreenPresence.isResumed) RestCue.play(context)
        }
    }

    // Notifications are how the countdown reaches the lock screen and the shade. Denying them is
    // fine: everything still works inside the app.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(session.clientId) {
        if (!asked &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            asked = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
