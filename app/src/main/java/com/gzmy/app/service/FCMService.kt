package com.gzmy.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.gzmy.app.R
import com.gzmy.app.ui.main.MainActivity

class FCMService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "gzmy_channel"
        const val CHANNEL_NAME = "gzmy"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Token'ı kaydet - Setup sırasında kullanılacak
        val prefs = getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
        
        // Firestore'a gönder
        val userId = prefs.getString("user_id", null)
        if (userId != null) {
            // Token'ı Firestore'a kaydet
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("tokens")
                .document(userId)
                .set(mapOf(
                    "fcmToken" to token,
                    "lastUpdated" to com.google.firebase.Timestamp.now()
                ))
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Bildirim verilerini al
        val data = remoteMessage.data
        val type = data["type"] ?: "note"
        val vibrationPattern = data["vibrationPattern"] ?: "gentle"
        val senderName = data["senderName"] ?: "Partnerin"
        
        // Titreşim çal (app kapalıyken bile)
        when (type) {
            "vibration" -> vibrate(vibrationPattern)
            "heartbeat" -> vibrateHeartbeat()
            "note" -> vibrateGentle()
        }
        
        // Bildirim göster
        val title = remoteMessage.notification?.title ?: "gzmy"
        val body = remoteMessage.notification?.body ?: "Yeni mesaj!"
        
        showNotification(title, body, data)
    }

    private fun vibrate(pattern: String) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        val vibrationPattern = when (pattern) {
            "gentle" -> longArrayOf(0, 200)
            "heartbeat" -> longArrayOf(0, 100, 100, 100, 300, 200)
            "intense" -> longArrayOf(0, 500)
            else -> longArrayOf(0, 200)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(vibrationPattern, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(vibrationPattern, -1)
        }
    }

    private fun vibrateGentle() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }

    private fun vibrateHeartbeat() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 100, 100, 100, 300, 200)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sevgilinden gelen titreşim ve mesajlar"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 100, 100, 100)
                setBypassDnd(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("type", data["type"])
            putExtra("messageId", data["messageId"])
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 100, 100, 100))

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
