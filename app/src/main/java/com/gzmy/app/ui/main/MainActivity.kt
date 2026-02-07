package com.gzmy.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gzmy.app.R
import com.gzmy.app.databinding.ActivityMainBinding
import com.gzmy.app.ui.setup.MainFragment
import com.gzmy.app.ui.setup.SetupFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Android 13+ bildirim izni launcher'ı
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("Gzmy", "Bildirim izni: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Android 13+ için bildirim izni iste
        requestNotificationPermission()

        if (savedInstanceState == null) {
            checkFirstSetup()
        }
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
            .replace(R.id.container, fragment)
            .commit()
    }
}
