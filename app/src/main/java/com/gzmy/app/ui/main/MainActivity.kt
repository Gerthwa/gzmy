package com.gzmy.app.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.gzmy.app.R
import com.gzmy.app.databinding.ActivityMainBinding
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

        // Pil optimizasyonu muafiyeti iste (bildirim teslimi için kritik)
        requestBatteryOptimizationExemption()

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

    /**
     * Pil optimizasyonu muafiyeti iste.
     *
     * Birçok Android üretici (Samsung, Xiaomi, Huawei, OPPO, Realme)
     * arka plan süreçlerini agresif olarak öldürüyor. Bu, FCM bildirimlerinin
     * uygulama kapalıyken teslim edilmesini engelliyor.
     *
     * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS izni ile sistem
     * "Bu uygulamayı pil optimizasyonundan muaf tut" dialog'unu gösterir.
     * Bu tek seferlik bir istek — kullanıcı kabul ederse tekrar sorulmaz.
     */
    private fun requestBatteryOptimizationExemption() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d(TAG, "Pil optimizasyonu muafiyeti isteniyor...")

                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Log.d(TAG, "Pil optimizasyonu zaten muaf")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pil optimizasyonu muafiyeti istenemedi: ${e.message}")
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
}
