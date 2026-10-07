package com.example.hybrid_ai_app.home.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.hybrid_ai_app.MainActivity
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.util.TimeProvider
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import com.example.hybrid_ai_app.home.domain.repository.ActiveWorkoutRepository
import com.example.hybrid_ai_app.home.presentation.workout.SessionLinks
import com.example.hybrid_ai_app.home.presentation.workout.SessionScreenPresence
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps a workout alive in the background: an ongoing notification with a native stopwatch that
 * turns into a countdown while a rest runs, and the sound and vibration when the rest ends.
 *
 * It is a foreground service so the process survives the phone being locked between sets, and it
 * exists only while a session does: it observes the stored session and stops itself when it is
 * cleared. The countdown itself is drawn by the system (a chronometer), so nothing here needs to
 * tick; the only timed work is one `delay` until the rest ends.
 *
 * The end-of-rest cue is fired here only when the session screen is not showing; while it is, the
 * screen plays the cue itself (see [SessionScreenPresence]), so the athlete is alerted exactly once.
 */
@AndroidEntryPoint
class WorkoutSessionService : Service() {

    @Inject
    lateinit var activeWorkout: ActiveWorkoutRepository

    @Inject
    lateinit var time: TimeProvider

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null
    private var restEndJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels()

        // A foreground service has a few seconds after startForegroundService() to show its
        // notification, so it is posted before anything is read from the database.
        try {
            startInForeground(buildOngoing(session = null))
        } catch (e: IllegalStateException) {
            // Android 12+ refuses to start a foreground service from the background. The session
            // is safe in the database; the screen starts the service again the next time it shows.
            stopSelf()
            return START_NOT_STICKY
        }

        if (observeJob == null) {
            observeJob = scope.launch {
                activeWorkout.observe().collect { session ->
                    if (session == null) stopSelfAndNotification() else onSession(session)
                }
            }
        }
        // Not sticky: after the process is killed the system would restart the service from the
        // background, which it may not be allowed to do. The workout screen starts it again from the
        // foreground, and the session itself lives in the database either way.
        return START_NOT_STICKY
    }

    private fun onSession(session: WorkoutSession) {
        notificationManager().notify(NOTIFICATION_ID, buildOngoing(session))

        restEndJob?.cancel()
        val endsAt = session.restEndsAt ?: return
        val wait = endsAt - time.nowMillis()
        if (wait <= 0) return

        restEndJob = scope.launch {
            delay(wait)
            // The stopwatch replaces the finished countdown, and the athlete is told the rest is over.
            notificationManager().notify(NOTIFICATION_ID, buildOngoing(session))
            if (!SessionScreenPresence.isResumed) postRestOver(session)
        }
    }

    private fun stopSelfAndNotification() {
        restEndJob?.cancel()
        notificationManager().cancel(NOTIFICATION_REST_OVER_ID)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    // ------------------------------------------------------------------------------------
    // Notifications
    // ------------------------------------------------------------------------------------

    private fun buildOngoing(session: WorkoutSession?): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_workout_notification)
            .setContentTitle(
                session?.title?.takeIf { it.isNotBlank() }
                    ?: getString(if (session?.isExtra == true) R.string.extra_strength_title else R.string.session_notif_title),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(openSessionIntent(session))

        val restEndsAt = session?.restEndsAt
        if (session != null && restEndsAt != null && restEndsAt > time.nowMillis()) {
            // A system-drawn countdown to the end of the rest.
            builder
                .setContentText(getString(R.string.session_notif_rest_text))
                .setWhen(restEndsAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else if (session != null) {
            // A system-drawn stopwatch since the workout started.
            builder
                .setContentText(getString(R.string.session_notif_running_text))
                .setWhen(session.startedAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
        } else {
            builder.setContentText(getString(R.string.session_notif_running_text))
        }
        return builder.build()
    }

    private fun postRestOver(session: WorkoutSession) {
        val notification = NotificationCompat.Builder(this, CHANNEL_REST)
            .setSmallIcon(R.drawable.ic_workout_notification)
            .setContentTitle(getString(R.string.session_notif_rest_over_title))
            .setContentText(getString(R.string.session_notif_rest_over_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openSessionIntent(session))
            .build()
        notificationManager().notify(NOTIFICATION_REST_OVER_ID, notification)
    }

    private fun openSessionIntent(session: WorkoutSession?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = SessionLinks.ACTION_OPEN_SESSION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (session != null) putExtra(SessionLinks.EXTRA_ROUTE, SessionLinks.routeFor(session))
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannels() {
        val manager = notificationManager()

        // The ongoing notification is silent: it updates constantly and must never buzz.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SESSION,
                getString(R.string.session_notif_channel_session),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.session_notif_channel_session_desc) },
        )

        // The alert is the opposite: loud, once, when the rest is over.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REST,
                getString(R.string.session_notif_channel_rest),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.session_notif_channel_rest_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )
    }

    private fun notificationManager(): NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_SESSION = "workout_session_channel"
        const val CHANNEL_REST = "rest_timer_channel"
        private const val NOTIFICATION_ID = 2001
        private const val NOTIFICATION_REST_OVER_ID = 2002

        /**
         * Starts (or keeps alive) the service. Call it from the foreground, when a session starts
         * or resumes: Android refuses to start a foreground service from the background.
         */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WorkoutSessionService::class.java))
        }
    }
}
