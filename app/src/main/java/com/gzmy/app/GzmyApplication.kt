package com.gzmy.app

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.gzmy.app.data.local.AppDatabase
import com.gzmy.app.lifecycle.AppLifecycleObserver
import com.gzmy.app.widget.WidgetUpdateWorker
import com.gzmy.app.worker.SyncMessagesWorker

class GzmyApplication : Application() {

    companion object {
        const val CHANNEL_ID = "gzmy_channel"
        const val CHANNEL_NAME = "gzmy Bildirimleri"
        private const val TAG = "GzmyApp"

        /** Uygulama ön planda mı? FCMService bu değeri kontrol eder */
        val isAppInForeground: Boolean
            get() = AppLifecycleObserver.isInForeground

        const val ACTION_NEW_MESSAGE = "com.gzmy.app.NEW_MESSAGE"
    }

    override fun onCreate() {
        super.onCreate()

        // Notification channel — SES dahil
        createNotificationChannel()

        // Initialize Room database
        AppDatabase.getInstance(this)

        // Register lifecycle observer (foreground/background tracking)
        AppLifecycleObserver.register()

        // Schedule periodic widget updates (every 30 min)
        WidgetUpdateWorker.schedule(this)

        // Enqueue offline message sync (runs when network available)
        SyncMessagesWorker.enqueue(this)

        // Her uygulama açılışında FCM token'ı yenile ve Firestore'a kaydet
        refreshFcmToken()
    }

    /**
     * Notification channel — IMPORTANCE_HIGH + ses + titreşim + kilit ekranı.
     * Kanal bir kez oluşturulduktan sonra, kullanıcı ayarlarını değiştirmezse
     * tekrar createNotificationChannel çağrısı mevcut kanalı günceller.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Partnerinden gelen titreşim ve mesajlar"

                // Ses
                val audioAttr = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audioAttr)

                // Titreşim
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 100, 100, 100, 300, 200)

                // DND bypass + kilit ekranı
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC

                // LED
                enableLights(true)
                lightColor = 0xFFFF4081.toInt()

                // Heads-up
                importance = NotificationManager.IMPORTANCE_HIGH
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel oluşturuldu: $CHANNEL_ID")
        }
    }

    /**
     * Her uygulama açılışında FCM token'ı al ve Firestore'a kaydet.
     * Token bayatlama sorununu önler — cihaz yeniden başlatıldıktan sonra
     * veya uzun süre uygulama kullanılmadığında token değişebilir.
     */
    private fun refreshFcmToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d(TAG, "FCM token alındı (${token.take(20)}...)")

                val prefs = getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
                val oldToken = prefs.getString("fcm_token", null)
                val userId = prefs.getString("user_id", null)

                // Token'ı her zaman SharedPrefs'e kaydet
                prefs.edit().putString("fcm_token", token).apply()

                // Firestore'a kaydet (userId varsa)
                if (!userId.isNullOrEmpty()) {
                    FirebaseFirestore.getInstance()
                        .collection("tokens")
                        .document(userId)
                        .set(
                            mapOf(
                                "fcmToken" to token,
                                "lastUpdated" to com.google.firebase.Timestamp.now(),
                                "platform" to "android"
                            )
                        )
                        .addOnSuccessListener {
                            if (oldToken != token) {
                                Log.d(TAG, "FCM token DEĞİŞTİ ve Firestore'a kaydedildi")
                            } else {
                                Log.d(TAG, "FCM token Firestore'a kaydedildi (aynı token)")
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "FCM token Firestore kaydetme hatası: ${e.message}")
                        }
                } else {
                    Log.d(TAG, "userId yok — token sadece SharedPrefs'e kaydedildi")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "FCM token alınamadı: ${e.message}")
            }
    }
}
