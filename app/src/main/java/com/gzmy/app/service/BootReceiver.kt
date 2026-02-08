package com.gzmy.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

/**
 * BootReceiver — Cihaz yeniden başlatıldığında FCM token'ı yeniler.
 *
 * Cihaz restart'ından sonra FCM token değişebilir veya bayatlayabilir.
 * Bu receiver token'ı alır ve Firestore'a kaydeder, böylece
 * partner hala bildirim gönderebilir.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Cihaz yeniden başlatıldı — FCM token yenileniyor")

        val prefs = context.getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)

        if (userId.isNullOrEmpty()) {
            Log.d(TAG, "userId yok — token yenileme atlanıyor")
            return
        }

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d(TAG, "Boot sonrası FCM token alındı")

                // SharedPrefs'e kaydet
                prefs.edit().putString("fcm_token", token).apply()

                // Firestore'a kaydet
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
                    .addOnSuccessListener { Log.d(TAG, "Boot sonrası token Firestore'a kaydedildi") }
                    .addOnFailureListener { e -> Log.e(TAG, "Token kaydetme hatası: ${e.message}") }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Boot sonrası FCM token alınamadı: ${e.message}")
            }
    }
}
