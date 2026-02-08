package com.gzmy.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.gzmy.app.GzmyApplication
import com.gzmy.app.R
import com.gzmy.app.ui.chat.ChatFragment
import com.gzmy.app.ui.main.MainActivity
import com.gzmy.app.util.VibrationManager
import com.gzmy.app.widget.GzmyWidgetProvider

class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val WAKELOCK_TIMEOUT = 10_000L
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

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "gzmy:FCMWakeLock"
        )
        wakeLock.acquire(WAKELOCK_TIMEOUT)

        try {
            Log.d(TAG, "=== MESAJ ALINDI ===")
            Log.d(TAG, "Data: ${remoteMessage.data}")
            Log.d(TAG, "Notification: title=${remoteMessage.notification?.title}, body=${remoteMessage.notification?.body}")

            val data = remoteMessage.data
            val type = (data["type"] ?: "note").lowercase()
            val vibrationPattern = (data["vibrationPattern"] ?: "gentle").lowercase()
            val title = data["title"] ?: remoteMessage.notification?.title ?: "gzmy"
            val body = data["body"] ?: remoteMessage.notification?.body ?: "Yeni mesaj!"

            Log.d(TAG, "İşlenecek: type=$type, pattern=$vibrationPattern, title=$title")
            Log.d(TAG, "isAppInForeground=${GzmyApplication.isAppInForeground}, isChatActive=${ChatFragment.isChatScreenActive}")

            // ─── HYBRID PAYLOAD DAVRANIŞI ───
            // notification+data payload ile:
            //   FOREGROUND  → onMessageReceived() çağrılır (biz yönetiriz)
            //   BACKGROUND  → Sistem otomatik bildirim gösterir (bu metot çağrılmaz)
            //   KILLED      → Sistem otomatik bildirim gösterir (bu metot çağrılmaz)
            //
            // Bu metot SADECE foreground'da çağrılır. Bu yüzden:
            //   1. Sistem bildirimini bastır (NotificationManager ile iptal et)
            //   2. LocalBroadcast ile UI'ı güncelle

            if (GzmyApplication.isAppInForeground) {
                // === FOREGROUND ===
                Log.d(TAG, "Uygulama ön planda — sessiz broadcast gönderiliyor")

                // Sistem hybrid notification'dan otomatik bildirim oluşturabilir,
                // biz ön plandayken bunu istemiyoruz — notification tag ile iptal et
                cancelSystemNotification(type)

                val broadcastIntent = Intent(GzmyApplication.ACTION_NEW_MESSAGE).apply {
                    putExtra("title", title)
                    putExtra("body", body)
                    putExtra("type", type)
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)

                // Chat ekranında değilsek hafif bir titreşim ver
                if (!ChatFragment.isChatScreenActive) {
                    VibrationManager.performLightTap(this)
                }
            } else {
                // === BACKGROUND (nadir — genelde sistem halleder) ===
                Log.d(TAG, "Uygulama arka planda — bildirim gösteriliyor")

                // Titreşim çal
                try {
                    when (type) {
                        "vibration" -> vibrateByPattern(vibrationPattern)
                        "heartbeat" -> VibrationManager.performHeartbeat(this)
                        "note", "chat" -> VibrationManager.performLightTap(this)
                        else -> VibrationManager.performLightTap(this)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Titreşim hatası: ${e.message}")
                }

                // Bildirim göster (fallback — normalde sistem halleder)
                showNotification(title, body, data)
            }

            // Widget'ı güncelle (her durumda)
            updateWidget(body)

            Log.d(TAG, "=== İŞLEM TAMAMLANDI ===")

        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    /**
     * Foreground'da sistem tarafından otomatik oluşturulan hybrid bildirimi iptal et.
     * Cloud Functions'ta tag: "gzmy_<type>" kullanıyoruz.
     */
    private fun cancelSystemNotification(type: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Sistem, notification tag'i ile bildirim oluşturur
            nm.cancel("gzmy_$type", 0)
        } catch (e: Exception) {
            Log.w(TAG, "Sistem bildirimi iptal edilemedi: ${e.message}")
        }
    }

    private fun vibrateByPattern(pattern: String) {
        val vibPattern = when (pattern) {
            "gentle" -> longArrayOf(0, 200)
            "heartbeat" -> longArrayOf(0, 80, 120, 80, 400, 80, 120, 80)
            "intense" -> longArrayOf(0, 500)
            else -> longArrayOf(0, 200)
        }
        VibrationManager.vibratePattern(this, vibPattern)
    }

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

    /** Widget güncelleme — SharedPrefs'e yaz + broadcast tetikle */
    private fun updateWidget(lastMessage: String) {
        try {
            getSharedPreferences("gzmy_widget", Context.MODE_PRIVATE)
                .edit().putString("last_message", lastMessage).apply()

            GzmyWidgetProvider.triggerUpdate(this)
            Log.d(TAG, "Widget güncelleme tetiklendi")
        } catch (e: Exception) {
            Log.e(TAG, "Widget güncelleme hatası: ${e.message}")
        }
    }
}
