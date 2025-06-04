package com.StressDetection

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class LauncherActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("onboarding_prefs", MODE_PRIVATE)

        // Add a small delay for splash effect
        Handler(Looper.getMainLooper()).postDelayed({
            // Check if user has completed onboarding
            val hasCompletedOnboarding = sharedPreferences.getBoolean("onboarding_completed", false)

            if (hasCompletedOnboarding) {
                // Go directly to MainActivity
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                // Show onboarding
                startActivity(Intent(this, OnboardingActivity::class.java))
            }
            finish()
        }, 1000) // 1 second delay
    }
}