package com.example.safeguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class EmergencyNotificationService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token) // Ensure base class handles it
        sendTokenToServer(token) // Send new token to database/server
    }

    private fun sendTokenToServer(token: String) {
        // Save token in Firebase database or send it to your server
        Log.d("FCM", "New token received: $token")
    }

    companion object {
        private const val CHANNEL_ID = "emergency_channel"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        if (remoteMessage.data.isNotEmpty()) {
            // Check if it's an emergency notification
            val type = remoteMessage.data["type"]
            if (type == "emergency") {
                val userName = remoteMessage.data["userName"] ?: "Someone"
                val latitude = remoteMessage.data["latitude"]?.toDoubleOrNull()
                val longitude = remoteMessage.data["longitude"]?.toDoubleOrNull()
                val emergencyId = remoteMessage.data["emergencyId"]

                if (emergencyId != null) {
                    sendEmergencyNotification(userName, latitude, longitude, emergencyId)
                }
            }
        }
    }

    private fun sendEmergencyNotification(
        userName: String,
        latitude: Double?,
        longitude: Double?,
        emergencyId: String
    ) {
        val intent = Intent(this, EmergencyResponseActivity::class.java).apply {
            putExtra("EMERGENCY_ID", emergencyId)
            latitude?.let { putExtra("LATITUDE", it) }
            longitude?.let { putExtra("LONGITUDE", it) }
            putExtra("USER_NAME", userName)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // A built-in alert icon
            .setContentTitle("EMERGENCY ALERT!")
            .setContentText("$userName needs help! Tap to respond.")
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setContentIntent(pendingIntent)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency alerts from contacts in danger"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }
}
