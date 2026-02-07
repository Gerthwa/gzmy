package com.gzmy.app

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class GzmyApplication : Application() {

    companion object {
        const val CHANNEL_ID = "gzmy_channel"
        const val CHANNEL_NAME = "gzmy Bildirimleri"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
