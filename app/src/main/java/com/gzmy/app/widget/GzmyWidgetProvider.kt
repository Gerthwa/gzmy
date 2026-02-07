package com.gzmy.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.gzmy.app.R
import com.gzmy.app.ui.main.MainActivity

class GzmyWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "GzmyWidget"

        /**
         * Trigger an immediate widget refresh from anywhere (e.g. FCMService, LiveStatusManager).
         */
        fun triggerUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, GzmyWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, GzmyWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "Widget etkinleştirildi")
        // Ensure WorkManager is scheduled when first widget is placed
        WidgetUpdateWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "Widget devre dışı bırakıldı — tüm widget'lar kaldırıldı")
        WidgetUpdateWorker.cancel(context)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val prefs = context.getSharedPreferences("gzmy_widget", Context.MODE_PRIVATE)
        val lastMessage = prefs.getString("last_message", "Henüz mesaj yok") ?: "Henüz mesaj yok"
        val missLevel = prefs.getInt("miss_level", -1)
        val partnerName = prefs.getString("partner_name", "") ?: ""

        val views = RemoteViews(context.packageName, R.layout.widget_gzmy)

        // Partner ismi
        if (partnerName.isNotEmpty()) {
            views.setTextViewText(R.id.tvWidgetPartnerName, "💕 $partnerName")
        }

        // Son mesaj / not
        views.setTextViewText(R.id.tvWidgetLastMessage, lastMessage)

        // Özlem seviyesi: emoji text + ProgressBar
        if (missLevel >= 0) {
            val emoji = when {
                missLevel < 20 -> "🤍"
                missLevel < 40 -> "💛"
                missLevel < 60 -> "🧡"
                missLevel < 80 -> "❤️"
                else -> "❤️‍🔥"
            }
            views.setTextViewText(R.id.tvWidgetMissLevel, "$emoji $missLevel")
            views.setProgressBar(R.id.progressWidgetMiss, 100, missLevel, false)
        } else {
            views.setTextViewText(R.id.tvWidgetMissLevel, "—")
            views.setProgressBar(R.id.progressWidgetMiss, 100, 0, false)
        }

        // Widget'a tıklayınca uygulamayı aç
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
        Log.d(TAG, "Widget güncellendi: partner='$partnerName', msg='$lastMessage', miss=$missLevel")
    }
}
