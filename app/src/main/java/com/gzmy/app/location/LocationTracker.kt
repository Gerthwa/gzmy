package com.gzmy.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestore

/**
 * LocationTracker — Tracks user location with battery-friendly settings
 * and syncs to Firestore couples document.
 *
 * Usage:
 *   LocationTracker.start(context)  // After permission granted
 *   LocationTracker.stop()          // onDestroyView or logout
 */
object LocationTracker {

    private const val TAG = "LocationTracker"
    private const val INTERVAL_MS = 300_000L       // 5 minutes
    private const val MIN_DISPLACEMENT_M = 100f    // 100 meters

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    @Volatile var lastLat: Double = 0.0; private set
    @Volatile var lastLng: Double = 0.0; private set

    interface LocationListener {
        fun onMyLocationUpdated(lat: Double, lng: Double)
    }

    private val listeners = mutableSetOf<LocationListener>()

    fun addListener(l: LocationListener) { listeners.add(l) }
    fun removeListener(l: LocationListener) { listeners.remove(l) }

    fun start(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Location permission not granted, skipping")
            return
        }

        if (fusedClient != null) {
            Log.d(TAG, "Already tracking, skipping")
            return
        }

        fusedClient = LocationServices.getFusedLocationProviderClient(context)

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISPLACEMENT_M)
            .setMinUpdateIntervalMillis(INTERVAL_MS / 2)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                lastLat = loc.latitude
                lastLng = loc.longitude
                Log.d(TAG, "Location: $lastLat, $lastLng")

                // Write to Firestore
                writeLocationToFirestore(context, lastLat, lastLng)

                // Notify listeners
                listeners.forEach { it.onMyLocationUpdated(lastLat, lastLng) }
            }
        }

        try {
            fusedClient?.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
            Log.d(TAG, "Location tracking started (interval=${INTERVAL_MS}ms, displacement=${MIN_DISPLACEMENT_M}m)")

            // Get last known immediately
            fusedClient?.lastLocation?.addOnSuccessListener { loc ->
                if (loc != null) {
                    lastLat = loc.latitude
                    lastLng = loc.longitude
                    writeLocationToFirestore(context, lastLat, lastLng)
                    listeners.forEach { it.onMyLocationUpdated(lastLat, lastLng) }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
        }
    }

    fun stop() {
        locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
        fusedClient = null
        locationCallback = null
        Log.d(TAG, "Location tracking stopped")
    }

    private fun writeLocationToFirestore(context: Context, lat: Double, lng: Double) {
        val prefs = context.getSharedPreferences("gzmy_prefs", Context.MODE_PRIVATE)
        val coupleCode = prefs.getString("couple_code", "") ?: ""
        val userId = prefs.getString("user_id", "") ?: ""

        if (coupleCode.isEmpty() || userId.isEmpty()) return

        FirebaseFirestore.getInstance()
            .collection("couples").document(coupleCode)
            .update("location_$userId", mapOf("lat" to lat, "lng" to lng))
            .addOnSuccessListener { Log.d(TAG, "Location written to Firestore") }
            .addOnFailureListener { e -> Log.w(TAG, "Location write failed: ${e.message}") }
    }
}
