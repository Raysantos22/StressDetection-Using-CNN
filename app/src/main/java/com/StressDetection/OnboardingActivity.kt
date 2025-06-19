package com.StressDetection

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.StressDetection.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var onboardingAdapter: OnboardingAdapter
    private val dots = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("onboarding_prefs", MODE_PRIVATE)

        setupViewPager()
        setupDotIndicators()
        setupClickListeners()
    }

    private fun setupViewPager() {
        onboardingAdapter = OnboardingAdapter()
        binding.viewPager.adapter = onboardingAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateDotIndicators(position)
                updatePageIndicator(position)
                updateButtons(position)
                updateBackground(position)
            }
        })
    }

    private fun setupDotIndicators() {
        dots.add(binding.dotIndicator1)
        dots.add(binding.dotIndicator2)
        dots.add(binding.dotIndicator3)
    }

    private fun updateDotIndicators(position: Int) {
        dots.forEachIndexed { index, dot ->
            if (index == position) {
                dot.background = ContextCompat.getDrawable(this, R.drawable.dot_active)
            } else {
                dot.background = ContextCompat.getDrawable(this, R.drawable.dot_inactive)
            }
        }
    }

    private fun updatePageIndicator(position: Int) {
        binding.pageIndicator.text = "${position + 1} / 3"
    }

    private fun updateButtons(position: Int) {
        when (position) {
            0, 1 -> {
                binding.nextButton.visibility = View.VISIBLE
                binding.startButton.visibility = View.GONE
                binding.skipButton.visibility = View.VISIBLE
            }
            2 -> {
                binding.nextButton.visibility = View.GONE
                binding.startButton.visibility = View.VISIBLE
                binding.skipButton.visibility = View.GONE
            }
        }
    }

    private fun updateBackground(position: Int) {
        when (position) {
            0 -> binding.root.background = ContextCompat.getDrawable(this, R.drawable.gradient_blue_green)
            1 -> binding.root.background = ContextCompat.getDrawable(this, R.drawable.gradient_blue_light_green)
            2 -> binding.root.background = ContextCompat.getDrawable(this, R.drawable.gradient_blue_purple)
        }
    }

    private fun setupClickListeners() {
        binding.nextButton.setOnClickListener {
            val currentPosition = binding.viewPager.currentItem
            if (currentPosition < 2) {
                binding.viewPager.currentItem = currentPosition + 1
            }
        }

        binding.startButton.setOnClickListener {
            completeOnboarding()
        }

        binding.skipButton.setOnClickListener {
            completeOnboarding()
        }
    }

    private fun completeOnboarding() {
        sharedPreferences.edit().putBoolean("onboarding_completed", true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}