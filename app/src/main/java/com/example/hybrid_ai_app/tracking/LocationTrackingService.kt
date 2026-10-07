package com.example.hybrid_ai_app.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.hybrid_ai_app.MainActivity
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.domain.model.ActiveRun
import com.example.hybrid_ai_app.core.domain.model.RunPoint
import com.example.hybrid_ai_app.core.domain.model.RunProgress
import com.example.hybrid_ai_app.core.domain.model.RunTimeline
import com.example.hybrid_ai_app.core.domain.repository.ActiveRunRepository
import com.example.hybrid_ai_app.core.util.TimeProvider
import com.example.hybrid_ai_app.home.presentation.run.label
import com.example.hybrid_ai_app.home.presentation.workout.SessionLinks
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tracks the run in progress in the foreground: GPS points, the clock, and, when the run has a
 * structure, the guided phases with their beeps and vibrations.
 *
 * **The run lives in the database, not here.** The run screen starts, pauses, resumes and clears it
 * through [ActiveRunRepository]; this service observes it and follows: it starts or stops GPS and
 * the clock to match, appends every fix it receives, and stops itself once the run is cleared. That
 * is what lets a run survive the activity being recreated and the process being killed: whichever
 * start command arrives next ([ACTION_START], [ACTION_RESTORE] or the system's sticky restart with a
 * null intent) reads the stored run and carries on from its clock anchors and its stored path.
 *
 * The clock is the run's own wall-clock anchors (see [ActiveRun.elapsedMs]), re-read on every tick,
 * so a late tick never drifts the run and a restart picks up the time that passed while nothing was
 * running. A partial wake lock keeps the CPU awake so the cues arrive with the screen off.
 */
@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var activeRun: ActiveRunRepository

    @Inject
    lateinit var time: TimeProvider

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var cuePlayer: RunCuePlayer

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observeJob: Job? = null
    private var timerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Fixes are written one at a time, in the order they arrived; the autoincrement id is the order
    // the path is read back in.
    private val pointQueue = Channel<RunPoint>(Channel.UNLIMITED)

    /** The run being tracked, as last read from the database. Null until the first read. */
    private var run: ActiveRun? = null
    private var timeline: RunTimeline? = null
    private var tracking = false
    private var lastSecond = -1
    private var lastCueSecond = -1
    private var announcedSegment = NO_SEGMENT

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_RESTORE = "ACTION_RESTORE"
        private const val TAG = "RunTracking"
        private const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
        private const val NOTIFICATION_ID = 1
        private const val TICK_MS = 250L
        private const val LOCATION_INTERVAL_MS = 3000L
        private const val LOCATION_MIN_INTERVAL_MS = 2000L
        private const val WAKE_LOCK_TAG = "hybridai:run"
        private const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L
        private const val NO_SEGMENT = -2

        /**
         * Starts tracking the run that was just stored. Called from the foreground, which is what
         * Android 12+ requires of a foreground service start.
         */
        fun start(context: Context) = send(context, ACTION_START)

        /**
         * Brings tracking back for a stored run after the process was killed. Harmless while the
         * service is already tracking it, so a screen can call it every time it shows a run.
         */
        fun restore(context: Context) = send(context, ACTION_RESTORE)

        private fun send(context: Context, action: String) {
            try {
                context.startService(Intent(context, LocationTrackingService::class.java).setAction(action))
            } catch (e: IllegalStateException) {
                // The app went to the background in between; the next screen that shows the run
                // asks again.
                Log.w(TAG, "Run tracking not started: the app is in the background", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        cuePlayer = RunCuePlayer(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!tracking) return
                val location = result.locations.lastOrNull() ?: return
                WorkoutLocationManager.addPoint(LatLng(location.latitude, location.longitude))
                pointQueue.trySend(RunPoint(location.latitude, location.longitude))
            }
        }

        serviceScope.launch {
            for (point in pointQueue) activeRun.addPoint(point)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        // A foreground service has a few seconds to show its notification, so it is posted before
        // anything is read from the database.
        try {
            startInForeground(buildNotification(getString(R.string.run_notif_tracking)))
        } catch (e: Exception) {
            // Android 12+ refuses a foreground start from the background (a sticky restart can be
            // one), and Android 14 refuses a location service without the permission. The run is
            // safe in the database: the app restores it the next time it opens.
            Log.w(TAG, "Run tracking could not enter the foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (observeJob == null) {
            observeJob = serviceScope.launch {
                activeRun.observe().collect(::follow)
            }
        }
        return START_STICKY
    }

    // ------------------------------------------------------------------------------------
    // Following the stored run
    // ------------------------------------------------------------------------------------

    private suspend fun follow(stored: ActiveRun?) {
        if (stored == null) {
            // Finished or discarded: nothing left to track.
            stopTracking()
            WorkoutLocationManager.clearAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        if (stored.clientId != run?.clientId) load(stored) else run = stored

        if (stored.isPaused) pause() else resume()
    }

    /** Takes over a run this instance has not seen: a new one, or one that outlived its process. */
    private suspend fun load(stored: ActiveRun) {
        stopTracking()
        run = stored
        timeline = stored.structure?.let(::RunTimeline)

        val elapsedSec = secondsAt(stored)
        lastSecond = -1
        // A fresh run plays the cue on its very first second; a restored one plays nothing that
        // fell due while it was not being tracked.
        lastCueSecond = if (elapsedSec == 0) -1 else elapsedSec
        announcedSegment = NO_SEGMENT

        WorkoutLocationManager.restore(activeRun.points().map { LatLng(it.lat, it.lng) })
    }

    private fun pause() {
        val wasTracking = tracking
        stopTracking()
        if (wasTracking || lastSecond < 0) updateNotification(getString(R.string.run_notif_paused))
    }

    @SuppressLint("MissingPermission")
    private fun resume() {
        if (tracking) return
        tracking = true
        WorkoutLocationManager.setTrackingStatus(true)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_MIN_INTERVAL_MS)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // The permission was revoked mid-run: the clock and the cues still work.
            Log.w(TAG, "Location permission missing; tracking time only", e)
        }

        acquireWakeLock()
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
        announcedSegment = NO_SEGMENT
        if (timeline == null) updateNotification(getString(R.string.run_notif_tracking))
    }

    private fun stopTracking() {
        tracking = false
        WorkoutLocationManager.setTrackingStatus(false)
        timerJob?.cancel()
        timerJob = null
        fusedLocationClient.removeLocationUpdates(locationCallback)
        releaseWakeLock()
    }

    private fun secondsAt(stored: ActiveRun): Int = (stored.elapsedMs(time.nowMillis()) / 1000L).toInt()

    // ------------------------------------------------------------------------------------
    // Clock and cues
    // ------------------------------------------------------------------------------------

    /** Polls often but acts only when the whole second changes. */
    private fun tick() {
        val current = run ?: return
        val second = secondsAt(current)
        if (second == lastSecond) return
        lastSecond = second
        onSecond(second)
    }

    private fun onSecond(second: Int) {
        val timeline = timeline ?: return
        val progress = timeline.progressAt(second)

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

    // ------------------------------------------------------------------------------------
    // Wake lock and notification
    // ------------------------------------------------------------------------------------

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

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun updateNotification(contentText: String, remainingSec: Int? = null) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(contentText, remainingSec))
    }

    private fun buildNotification(contentText: String, remainingSec: Int? = null): Notification {
        val current = run
        val title = current?.title?.takeIf { it.isNotBlank() }
            ?: getString(if (current?.isExtra == true) R.string.extra_run_title else R.string.run_notif_title)
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openRunIntent(current))
        if (remainingSec != null) {
            builder
                .setWhen(System.currentTimeMillis() + remainingSec * 1000L)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        return builder.build()
    }

    /** Opens the app on the run's own screen; before the run is read, just opens the app. */
    private fun openRunIntent(current: ActiveRun?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = SessionLinks.ACTION_OPEN_SESSION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (current != null) putExtra(SessionLinks.EXTRA_ROUTE, SessionLinks.routeFor(current))
        }
        return PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.run_notif_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        pointQueue.close()
        cuePlayer.release()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
