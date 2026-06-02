package com.example.hybrid_ai_app.tracking

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var totalSeconds = 0L

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        private const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

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
            ACTION_START -> startTracking()
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        createNotificationChannel()
        updateNotification("Tracking your run... Activity started.")

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        WorkoutLocationManager.setTrackingStatus(true)
        startTimer()
    }

    private fun pauseTracking() {
        WorkoutLocationManager.setTrackingStatus(false)
        timerJob?.cancel()
        updateNotification("Workout Paused")
    }

    private fun resumeTracking() {
        WorkoutLocationManager.setTrackingStatus(true)
        startTimer()
        updateNotification("Tracking your run...")
    }

    private fun stopTracking() {
        timerJob?.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                totalSeconds++
                WorkoutLocationManager.updateTime(totalSeconds)
            }
        }
    }

    private fun updateNotification(contentText: String) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentTitle("Hybrid.AI Active Workout")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Workout Tracking",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}