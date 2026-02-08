package com.gzmy.app.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.gzmy.app.data.local.AppDatabase
import com.gzmy.app.data.model.Message
import kotlinx.coroutines.tasks.await

/**
 * SyncMessagesWorker — Sends unsent (isSynced=false) messages to Firestore
 * when network becomes available.
 *
 * Schedule with: SyncMessagesWorker.enqueue(context)
 */
class SyncMessagesWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "gzmy_sync_messages"

        /** Enqueue a one-time sync that waits for network */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncMessagesWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "Sync work enqueued (waiting for network)")
        }
    }

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getInstance(applicationContext).messageDao()
        val db = FirebaseFirestore.getInstance()

        return try {
            val unsynced = dao.getUnsyncedMessages()

            if (unsynced.isEmpty()) {
                Log.d(TAG, "No unsynced messages")
                return Result.success()
            }

            Log.d(TAG, "Syncing ${unsynced.size} messages to Firestore...")

            var successCount = 0
            for (entity in unsynced) {
                try {
                    val message = entity.toMessage()
                    db.collection("messages").document(entity.id).set(message).await()
                    dao.markAsSynced(entity.id)
                    successCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync message ${entity.id}: ${e.message}")
                    // Continue with next message
                }
            }

            Log.d(TAG, "Synced $successCount / ${unsynced.size} messages")

            if (successCount < unsynced.size) {
                Result.retry() // Some failed, retry later
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync worker failed: ${e.message}", e)
            Result.retry()
        }
    }
}
