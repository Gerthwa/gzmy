package com.gzmy.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.gzmy.app.data.model.Couple

/**
 * LiveStatusManager — Singleton that manages real-time Firestore listeners
 * for couple status (miss-you levels, partner info, online state).
 *
 * Usage:
 *   LiveStatusManager.start(context)         // Call from MainFragment.onViewCreated
 *   LiveStatusManager.stop()                 // Call from MainFragment.onDestroyView
 *   LiveStatusManager.addObserver(listener)  // UI subscribes
 */
object LiveStatusManager {

    private const val TAG = "LiveStatus"

    // ── State ──
    private var coupleListener: ListenerRegistration? = null
    private val observers = mutableSetOf<StatusObserver>()

    // ── Last known values (cached) ──
    @Volatile var lastCouple: Couple? = null
        private set
    @Volatile var myMissLevel: Int = 0
        private set
    @Volatile var partnerMissLevel: Int = 0
        private set
    @Volatile var partnerName: String = ""
        private set

    // ── Callbacks ──
    interface StatusObserver {
        fun onCoupleUpdated(couple: Couple, myLevel: Int, partnerLevel: Int, partnerName: String)
        fun onError(message: String) {}
    }

    fun addObserver(observer: StatusObserver) {
        observers.add(observer)
        // Immediately deliver cached state if available
        lastCouple?.let {
            observer.onCoupleUpdated(it, myMissLevel, partnerMissLevel, partnerName)
        }
    }

    fun removeObserver(observer: StatusObserver) {
        observers.remove(observer)
    }

    /**
     * Start listening to couple document in real-time.
     */
    fun start(context: Context) {
        val prefs = context.getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
        val coupleCode = prefs.getString("couple_code", "") ?: ""
        val userId = prefs.getString("user_id", "") ?: ""

        if (coupleCode.isEmpty() || userId.isEmpty()) {
            Log.w(TAG, "Cannot start: missing coupleCode or userId")
            return
        }

        // Avoid duplicate listeners
        if (coupleListener != null) {
            Log.d(TAG, "Already listening, skipping duplicate start")
            return
        }

        Log.d(TAG, "Starting real-time couple listener for: $coupleCode")

        val db = FirebaseFirestore.getInstance()
        coupleListener = db.collection("couples").document(coupleCode)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen error: ${error.message}")
                    observers.forEach { it.onError(error.message ?: "Unknown error") }
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val couple = snapshot.toObject(Couple::class.java) ?: return@addSnapshotListener
                lastCouple = couple

                // Determine partner
                val partnerId = if (couple.partner1Id == userId) couple.partner2Id else couple.partner1Id
                val pName = if (couple.partner1Id == userId) couple.partner2Name else couple.partner1Name

                myMissLevel = couple.missYouLevel[userId] ?: 0
                partnerMissLevel = couple.missYouLevel[partnerId] ?: 0
                partnerName = pName

                // Write to widget shared prefs
                context.getSharedPreferences("gzmy_widget", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("miss_level", partnerMissLevel)
                    .putString("partner_name", pName)
                    .apply()

                Log.d(TAG, "Couple updated: myLevel=$myMissLevel, partnerLevel=$partnerMissLevel")

                // Notify all observers
                observers.forEach { observer ->
                    observer.onCoupleUpdated(couple, myMissLevel, partnerMissLevel, pName)
                }
            }
    }

    /**
     * Write miss-you level to Firestore (debounced by caller).
     */
    fun writeMissLevel(context: Context, level: Int) {
        val prefs = context.getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
        val coupleCode = prefs.getString("couple_code", "") ?: ""
        val userId = prefs.getString("user_id", "") ?: ""

        if (coupleCode.isEmpty() || userId.isEmpty()) return

        FirebaseFirestore.getInstance()
            .collection("couples").document(coupleCode)
            .update("missYouLevel.$userId", level)
            .addOnSuccessListener { Log.d(TAG, "MissLevel written: $level") }
            .addOnFailureListener { e -> Log.e(TAG, "MissLevel write error: ${e.message}") }
    }

    /**
     * Stop all listeners and clear state.
     */
    fun stop() {
        coupleListener?.remove()
        coupleListener = null
        Log.d(TAG, "Stopped couple listener")
    }

    /**
     * Full cleanup (call on logout).
     */
    fun reset() {
        stop()
        observers.clear()
        lastCouple = null
        myMissLevel = 0
        partnerMissLevel = 0
        partnerName = ""
    }
}
