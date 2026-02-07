package com.gzmy.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.gzmy.app.data.model.Couple
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * WidgetUpdateWorker — Periodically fetches couple data from Firestore
 * and updates the home screen widget via WorkManager.
 *
 * Schedule:
 *   WidgetUpdateWorker.schedule(context)   // Call in Application.onCreate()
 *   WidgetUpdateWorker.cancel(context)     // Call on logout
 */
class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "WidgetWorker"
        private const val WORK_NAME = "gzmy_widget_sync"
        private const val INTERVAL_MINUTES = 30L

        /**
         * Schedule periodic widget updates every 30 minutes.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Scheduled periodic widget updates every ${INTERVAL_MINUTES}m")
        }

        /**
         * Cancel periodic updates (e.g. on logout).
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled periodic widget updates")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
            val coupleCode = prefs.getString("couple_code", "") ?: ""
            val userId = prefs.getString("user_id", "") ?: ""

            if (coupleCode.isEmpty() || userId.isEmpty()) {
                Log.w(TAG, "No user session, skipping widget update")
                return Result.success()
            }

            // Fetch couple document
            val db = FirebaseFirestore.getInstance()
            val doc = db.collection("couples").document(coupleCode).get().await()

            if (!doc.exists()) {
                Log.w(TAG, "Couple document not found")
                return Result.success()
            }

            val couple = doc.toObject(Couple::class.java) ?: return Result.success()

            // Determine partner
            val partnerId = if (couple.partner1Id == userId) couple.partner2Id else couple.partner1Id
            val partnerName = if (couple.partner1Id == userId) couple.partner2Name else couple.partner1Name
            val partnerLevel = couple.missYouLevel[partnerId] ?: 0

            // Fetch last message
            val msgSnapshot = db.collection("messages")
                .whereEqualTo("coupleCode", coupleCode)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            val lastMessage = if (!msgSnapshot.isEmpty) {
                val msg = msgSnapshot.documents[0]
                val sender = msg.getString("senderName") ?: ""
                val content = msg.getString("content") ?: ""
                "$sender: $content"
            } else {
                "Henüz mesaj yok"
            }

            // Write to widget prefs
            applicationContext.getSharedPreferences("gzmy_widget", Context.MODE_PRIVATE)
                .edit()
                .putString("last_message", lastMessage)
                .putInt("miss_level", partnerLevel)
                .putString("partner_name", partnerName)
                .apply()

            // Trigger widget refresh
            val widgetManager = AppWidgetManager.getInstance(applicationContext)
            val widgetComponent = ComponentName(applicationContext, GzmyWidgetProvider::class.java)
            val widgetIds = widgetManager.getAppWidgetIds(widgetComponent)

            if (widgetIds.isNotEmpty()) {
                val updateIntent = Intent(applicationContext, GzmyWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                applicationContext.sendBroadcast(updateIntent)
                Log.d(TAG, "Widget updated: msg='$lastMessage', level=$partnerLevel")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Widget update failed: ${e.message}", e)
            Result.retry()
        }
    }
}
