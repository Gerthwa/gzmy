package com.gzmy.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.gzmy.app.R
import com.gzmy.app.ui.main.MainActivity

class GzmyWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "GzmyWidget"
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
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "Widget devre dışı bırakıldı")
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val prefs = context.getSharedPreferences("gzmy_widget", Context.MODE_PRIVATE)
        val lastMessage = prefs.getString("last_message", "Henüz mesaj yok") ?: "Henüz mesaj yok"
        val missLevel = prefs.getInt("miss_level", -1)

        val views = RemoteViews(context.packageName, R.layout.widget_gzmy)

        // Son mesajı göster
        views.setTextViewText(R.id.tvWidgetLastMessage, lastMessage)

        // Özlem seviyesini göster
        val missText = if (missLevel >= 0) {
            val emoji = when {
                missLevel < 20 -> "🤍"
                missLevel < 40 -> "💛"
                missLevel < 60 -> "🧡"
                missLevel < 80 -> "❤️"
                else -> "❤️‍🔥"
            }
            "$emoji $missLevel / 100"
        } else {
            "—"
        }
        views.setTextViewText(R.id.tvWidgetMissLevel, missText)

        // Widget'a tıklayınca uygulamayı aç
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
        Log.d(TAG, "Widget güncellendi: msg='$lastMessage', miss=$missLevel")
    }
}
