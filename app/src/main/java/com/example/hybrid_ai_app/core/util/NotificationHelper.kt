
package com.example.hybrid_ai_app.core.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.hybrid_ai_app.R

class NotificationHelper(private val context: Context) {

    private val channelId = "premium_status_channel"

    init {
        createNotificationChannel()
    }

    // Notification Channels are required for Android 8.0 (API 26) and above
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Premium Status"
            val descriptionText = "Notifications for Hybrid.AI PRO upgrades"
            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showPremiumWelcomeNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val builder = NotificationCompat.Builder(context, channelId)
            // Replace with your own vector icon if preferred.
            // Note: Android requires notification icons to be transparent/white PNGs or Vectors.
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("Welcome to Hybrid.AI PRO! \uD83C\uDF1F") // 🌟 Emoji
            .setContentText("Your purchase was successful. Enjoy unlimited AI plans.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // The ID allows you to update or cancel the notification later
        notificationManager.notify(1001, builder.build())
    }
}