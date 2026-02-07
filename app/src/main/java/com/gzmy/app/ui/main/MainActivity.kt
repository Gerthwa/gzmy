package com.gzmy.app.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gzmy.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // İlk kurulum kontrolü
        checkFirstSetup()
    }
    
    private fun checkFirstSetup() {
        val prefs = getSharedPreferences("gzmy_prefs", MODE_PRIVATE)
        val coupleCode = prefs.getString("couple_code", null)
        val userId = prefs.getString("user_id", null)
        
        if (coupleCode == null || userId == null) {
            // Setup ekranına yönlendir
            // supportFragmentManager.beginTransaction()
            //     .replace(R.id.container, SetupFragment())
            //     .commit()
        } else {
            // Ana ekranı göster
            // supportFragmentManager.beginTransaction()
            //     .replace(R.id.container, GzmyFragment())
            //     .commit()
        }
    }
}
