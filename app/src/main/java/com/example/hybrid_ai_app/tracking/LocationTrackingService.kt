package com.example.hybrid_ai_app.tracking

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.domain.model.RunProgress
import com.example.hybrid_ai_app.core.domain.model.RunTimeline
import com.example.hybrid_ai_app.home.presentation.run.label
import com.google.android.gms.location.*
import kotlinx.coroutines.*

/**
 * Tracks a run in the foreground: GPS points, the elapsed time, and, when the start intent carries a
 * RunStructure, the guided run on top of it.
 *
 * The guided run lives here and not in the screen so its beeps and vibrations keep coming with the
 * phone locked. The clock reads SystemClock.elapsedRealtime instead of counting delays, so a late
 * tick never drifts the run, and a partial wake lock keeps the CPU awake while it runs.
 */
class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var cuePlayer: RunCuePlayer

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Clock: the time banked before the last resume, and when that resume happened.
    private var accumulatedMs = 0L
    private var resumedAtMs = NOT_RUNNING
    private var lastSecond = -1

    // Guided run. Null for a free run.
    private var timeline: RunTimeline? = null
    private var lastCueSecond = -1
    private var announcedSegment = NO_SEGMENT

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        private const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
        private const val NOTIFICATION_ID = 1
        private const val TICK_MS = 250L
        private const val WAKE_LOCK_TAG = "hybridai:run"
        private const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L
        private const val NOT_RUNNING = -1L
        private const val NO_SEGMENT = -2
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        cuePlayer = RunCuePlayer(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                // Only log coordinates
                if (WorkoutLocationManager.isTracking.value) {
                    result.locations.lastOrNull()?.let { location ->
                        val latLng = com.google.android.gms.maps.model.LatLng(location.latitude, location.longitude)
                        WorkoutLocationManager.addPoint(latLng)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking(intent)
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTracking(intent: Intent) {
        createNotificationChannel()
        updateNotification("Tracking your run... Activity started.")

        // Every start is a new run: nothing of an earlier one may leak into its clock or cues.
        timeline = RunStructureExtras.read(intent)?.let(::RunTimeline)
        accumulatedMs = 0L
        lastSecond = -1
        lastCueSecond = -1
        announcedSegment = NO_SEGMENT

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        WorkoutLocationManager.setTrackingStatus(true)
        startTimer()
    }

    private fun pauseTracking() {
        accumulatedMs = elapsedMs()
        resumedAtMs = NOT_RUNNING
        WorkoutLocationManager.setTrackingStatus(false)
        timerJob?.cancel()
        releaseWakeLock()
        updateNotification(getString(R.string.run_notif_paused))
    }

    private fun resumeTracking() {
        WorkoutLocationManager.setTrackingStatus(true)
        startTimer()
        val progress = timeline?.progressAt(lastSecond.coerceAtLeast(0))
        if (progress != null) notifyPhase(progress) else updateNotification("Tracking your run...")
    }

    private fun stopTracking() {
        timerJob?.cancel()
        releaseWakeLock()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startTimer() {
        acquireWakeLock()
        resumedAtMs = SystemClock.elapsedRealtime()
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    private fun elapsedMs(): Long = if (resumedAtMs == NOT_RUNNING) {
        accumulatedMs
    } else {
        accumulatedMs + (SystemClock.elapsedRealtime() - resumedAtMs)
    }

    /** Polls often but acts only when the whole second changes. */
    private fun tick() {
        val second = (elapsedMs() / 1000L).toInt()
        if (second == lastSecond) return
        lastSecond = second
        onSecond(second)
    }

    private fun onSecond(second: Int) {
        WorkoutLocationManager.updateTime(second.toLong())

        val timeline = timeline ?: return
        val progress = timeline.progressAt(second)
        WorkoutLocationManager.updateRunProgress(progress)

        // After a stall several cues can be due at once; the athlete only needs the latest one.
        timeline.cuesBetween(lastCueSecond, second).lastOrNull()?.let(cuePlayer::play)
        lastCueSecond = second

        if (progress.segmentIndex != announcedSegment) {
            announcedSegment = progress.segmentIndex
            notifyPhase(progress)
        }
    }

    /** Shows the current phase with a system-drawn countdown to its end. */
    private fun notifyPhase(progress: RunProgress) {
        val segment = progress.segment
        if (segment == null) {
            updateNotification(getString(R.string.run_notif_done))
        } else {
            updateNotification(segment.label(this), remainingSec = progress.remainingSec)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun updateNotification(contentText: String, remainingSec: Int? = null) {
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle("Hybrid.AI Active Workout")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        if (remainingSec != null) {
            builder
                .setWhen(System.currentTimeMillis() + remainingSec * 1000L)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }

        startForeground(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Workout Tracking",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        cuePlayer.release()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
