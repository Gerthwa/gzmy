package com.gzmy.app

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.gzmy.app.data.local.AppDatabase
import com.gzmy.app.lifecycle.AppLifecycleObserver
import com.gzmy.app.widget.WidgetUpdateWorker
import com.gzmy.app.worker.SyncMessagesWorker

class GzmyApplication : Application() {

    companion object {
        const val CHANNEL_ID = "gzmy_channel"
        const val CHANNEL_NAME = "gzmy Bildirimleri"

        /** Uygulama ön planda mı? FCMService bu değeri kontrol eder */
        val isAppInForeground: Boolean
            get() = AppLifecycleObserver.isInForeground

        const val ACTION_NEW_MESSAGE = "com.gzmy.app.NEW_MESSAGE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize Room database
        AppDatabase.getInstance(this)

        // Register lifecycle observer (foreground/background tracking)
        AppLifecycleObserver.register()

        // Schedule periodic widget updates (every 30 min)
        WidgetUpdateWorker.schedule(this)

        // Enqueue offline message sync (runs when network available)
        SyncMessagesWorker.enqueue(this)
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
}
