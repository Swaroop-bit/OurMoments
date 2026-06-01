package com.swaroop.photosender

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
// LOG 1: THE MOST IMPORTANT CHECK. Did the message arrive?
        Log.d("FCM_DEBUG", "🔔 Ping received from Firebase!")
        Log.d("FCM_DEBUG", "Payload Data: ${message.data}")
        // 1. ALWAYS UPDATE WIDGET (Silent background update)
        PhotoWidget.scheduleWidgetUpdate(applicationContext)

        // 2. CHECK SETTINGS: Did the user turn off notifications?
        if (!UserPrefs.areNotificationsEnabled(applicationContext)) {
            // Notifications are muted. Stop right here so the phone doesn't buzz!
            return
        }

        // Extract the dynamic title and body we sent from index.js
        val title = message.data["title"] ?: "New Moment! 💌"
        val body = message.data["body"] ?: "Your partner just sent a new photo to your widget."

        // 3. SHOW NOTIFICATION
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "our_moments_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0+ requires a Notification Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Our Moments Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new photos from your partner"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create an Intent to open the app when the user taps the notification
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)

        // Build the physical notification popup
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // Uses your new heart logo
            .setContentTitle(title) // Passed from onMessageReceived
            .setContentText(body)   // Passed from onMessageReceived
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true) // Clears the notification when tapped
            .setContentIntent(pendingIntent)

        // Fire the notification
        notificationManager.notify(1001, builder.build())
    }

    override fun onNewToken(token: String) {
        // Leave this blank unless you are doing specific token routing
    }
}
