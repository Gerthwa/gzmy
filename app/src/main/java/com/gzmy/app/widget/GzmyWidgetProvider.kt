package com.gzmy.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.gzmy.app.R
import com.gzmy.app.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class GzmyWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "GzmyWidget"

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
        Log.d(TAG, "Widget enabled")
        WidgetUpdateWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "Widget disabled")
        WidgetUpdateWorker.cancel(context)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val prefs = context.getSharedPreferences("gzmy_widget", Context.MODE_PRIVATE)
        val lastMessage = prefs.getString("last_message", "Henuz mesaj yok") ?: "Henuz mesaj yok"
        val missLevel = prefs.getInt("miss_level", -1)
        val partnerName = prefs.getString("partner_name", "") ?: ""
        val drawingUrl = prefs.getString("drawing_url", "") ?: ""

        val views = RemoteViews(context.packageName, R.layout.widget_gzmy)

        // Partner name
        if (partnerName.isNotEmpty()) {
            views.setTextViewText(R.id.tvWidgetPartnerName, "💕 $partnerName")
        }

        // Last message
        views.setTextViewText(R.id.tvWidgetLastMessage, lastMessage)

        // Miss level
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

        // Click to open app
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

        // Drawing image: load async and update
        if (drawingUrl.isNotEmpty()) {
            views.setViewVisibility(R.id.ivWidgetDrawing, View.VISIBLE)
            loadDrawingBitmap(context, appWidgetManager, appWidgetId, drawingUrl)
        } else {
            views.setViewVisibility(R.id.ivWidgetDrawing, View.GONE)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
        Log.d(TAG, "Widget updated: partner=$partnerName, miss=$missLevel, drawing=${drawingUrl.isNotEmpty()}")
    }

    /**
     * Load the drawing bitmap from URL in a coroutine and update widget.
     */
    private fun loadDrawingBitmap(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        url: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                val inputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap != null) {
                    // Scale down for widget
                    val scaled = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
                    bitmap.recycle()

                    withContext(Dispatchers.Main) {
                        val views = RemoteViews(context.packageName, R.layout.widget_gzmy)
                        views.setImageViewBitmap(R.id.ivWidgetDrawing, scaled)
                        views.setViewVisibility(R.id.ivWidgetDrawing, View.VISIBLE)
                        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load drawing bitmap: ${e.message}")
            }
        }
    }
}
