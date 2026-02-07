package com.gzmy.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.gzmy.app.GzmyApplication
import com.gzmy.app.R
import com.gzmy.app.ui.main.MainActivity

class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val WAKELOCK_TIMEOUT = 10_000L // 10 saniye
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Yeni FCM token alındı")

        val prefs = getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()

        val userId = prefs.getString("user_id", null)
        if (userId != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("tokens")
                .document(userId)
                .set(
                    mapOf(
                        "fcmToken" to token,
                        "lastUpdated" to com.google.firebase.Timestamp.now()
                    )
                )
                .addOnSuccessListener { Log.d(TAG, "Token Firestore'a kaydedildi") }
                .addOnFailureListener { e -> Log.e(TAG, "Token kaydetme hatası: ${e.message}") }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // WakeLock al: CPU'nun uyanık kalmasını sağla (uygulama kapalıyken kritik)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "gzmy:FCMWakeLock"
        )
        wakeLock.acquire(WAKELOCK_TIMEOUT)

        try {
            Log.d(TAG, "=== MESAJ ALINDI ===")
            Log.d(TAG, "Data: ${remoteMessage.data}")
            Log.d(TAG, "Notification: ${remoteMessage.notification}")

            val data = remoteMessage.data

            // Cloud Function küçük harf gönderir, ama güvenlik için lowercase'e çevir
            val type = (data["type"] ?: "note").lowercase()
            val vibrationPattern = (data["vibrationPattern"] ?: "gentle").lowercase()
            val title = data["title"] ?: "gzmy"
            val body = data["body"] ?: "Yeni mesaj!"

            Log.d(TAG, "İşlenecek: type=$type, pattern=$vibrationPattern, title=$title")

            // Titreşim çal
            try {
                when (type) {
                    "vibration" -> vibrate(vibrationPattern)
                    "heartbeat" -> vibrateHeartbeat()
                    "note" -> vibrateGentle()
                    else -> vibrateGentle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Titreşim hatası: ${e.message}")
            }

            // Bildirim göster
            showNotification(title, body, data)
            Log.d(TAG, "=== BİLDİRİM GÖSTERİLDİ ===")

        } finally {
            // WakeLock'u serbest bırak
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    // --- Vibrator Helper (API 31+ uyumlu) ---

    private fun getVibratorCompat(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun vibrate(pattern: String) {
        val vibrator = getVibratorCompat()
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
        val vibrator = getVibratorCompat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }

    private fun vibrateHeartbeat() {
        val vibrator = getVibratorCompat()
        val pattern = longArrayOf(0, 100, 100, 100, 300, 200)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    // --- Bildirim ---

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("type", data["type"])
            putExtra("messageId", data["messageId"])
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, GzmyApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 100, 100, 100))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
