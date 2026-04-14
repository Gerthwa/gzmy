package com.gzmy.app.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.gzmy.app.R
import com.gzmy.app.databinding.ActivityMainBinding
import com.gzmy.app.ui.chat.ChatFragment
import com.gzmy.app.ui.setup.MainFragment
import com.gzmy.app.ui.setup.SetupFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val TAG = "MainActivity"
    }

    // Android 13+ bildirim izni launcher'ı
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "Bildirim izni: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: immersive status/nav bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Android 13+ için bildirim izni iste
        requestNotificationPermission()

        if (savedInstanceState == null) {
            checkFirstSetup()
            handleLaunchIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkFirstSetup() {
        val prefs = getSharedPreferences("gzmy_prefs", MODE_PRIVATE)
        val coupleCode = prefs.getString("couple_code", null)
        val userId = prefs.getString("user_id", null)

        val fragment = if (coupleCode == null || userId == null) {
            SetupFragment()
        } else {
            MainFragment()
        }

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.container, fragment)
            .commit()
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return
        val type = intent.getStringExtra("type") ?: return

        val prefs = getSharedPreferences("gzmy_prefs", MODE_PRIVATE)
        val isConfigured = !prefs.getString("couple_code", null).isNullOrEmpty() &&
            !prefs.getString("user_id", null).isNullOrEmpty()
        if (!isConfigured) return

        if (type == "chat" || type == "note" || type == "vibration" || type == "heartbeat") {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right, R.anim.slide_out_left,
                    R.anim.slide_in_left, R.anim.slide_out_right
                )
                .replace(R.id.container, ChatFragment())
                .addToBackStack("chat_from_notification")
                .commit()
        }
    }
}
