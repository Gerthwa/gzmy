package com.gzmy.app.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gzmy.app.R
import com.gzmy.app.databinding.ActivityMainBinding
import com.gzmy.app.ui.setup.MainFragment
import com.gzmy.app.ui.setup.SetupFragment

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        if (savedInstanceState == null) {
            checkFirstSetup()
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
