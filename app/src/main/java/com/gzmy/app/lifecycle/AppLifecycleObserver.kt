package com.gzmy.app.lifecycle

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * AppLifecycleObserver — Tracks whether the app is in foreground or background.
 * Uses ProcessLifecycleOwner (single-process apps).
 *
 * Register once in Application.onCreate():
 *   AppLifecycleObserver.register()
 *
 * Query state anywhere:
 *   AppLifecycleObserver.isInForeground
 */
object AppLifecycleObserver : DefaultLifecycleObserver {

    private const val TAG = "AppLifecycle"

    @Volatile
    var isInForeground: Boolean = false
        private set

    private val listeners = mutableSetOf<ForegroundListener>()

    interface ForegroundListener {
        fun onForeground() {}
        fun onBackground() {}
    }

    /**
     * Register with ProcessLifecycleOwner. Call once in Application.onCreate().
     */
    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        Log.d(TAG, "Registered with ProcessLifecycleOwner")
    }

    fun addListener(listener: ForegroundListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ForegroundListener) {
        listeners.remove(listener)
    }

    override fun onStart(owner: LifecycleOwner) {
        isInForeground = true
        Log.d(TAG, "App → FOREGROUND")
        listeners.forEach { it.onForeground() }
    }

    override fun onStop(owner: LifecycleOwner) {
        isInForeground = false
        Log.d(TAG, "App → BACKGROUND")
        listeners.forEach { it.onBackground() }
    }
}
