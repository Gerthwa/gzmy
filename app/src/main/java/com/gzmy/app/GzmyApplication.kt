package com.gzmy.app

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class GzmyApplication : Application() {

    companion object {
        const val CHANNEL_ID = "gzmy_channel"
        const val CHANNEL_NAME = "gzmy Bildirimleri"

        /** Uygulama ön planda mı? FCMService bu değeri kontrol eder */
        @Volatile
        var isAppInForeground: Boolean = false
            private set

        const val ACTION_NEW_MESSAGE = "com.gzmy.app.NEW_MESSAGE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerForegroundObserver()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Partnerinden gelen titreşim ve mesajlar"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 100, 100, 100, 300, 200)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun registerForegroundObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isAppInForeground = true
                Log.d("GzmyApp", "Uygulama ÖN PLANA geçti")
            }

            override fun onStop(owner: LifecycleOwner) {
                isAppInForeground = false
                Log.d("GzmyApp", "Uygulama ARKA PLANA geçti")
            }
        })
    }
}
