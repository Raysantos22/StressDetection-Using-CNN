package com.surendramaran.yolov8tflite

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var dotIndicator1: MaterialCardView
    private lateinit var dotIndicator2: MaterialCardView
    private lateinit var dotIndicator3: MaterialCardView
    private lateinit var nextButton: Button

    // Page content
    private lateinit var headerTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var contentLayout: LinearLayout

    private var currentPage = 0
    private val totalPages = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding_simple)

        // Initialize UI elements
        dotIndicator1 = findViewById(R.id.dotIndicator1)
        dotIndicator2 = findViewById(R.id.dotIndicator2)
        dotIndicator3 = findViewById(R.id.dotIndicator3)
        nextButton = findViewById(R.id.nextButton)

        headerTextView = findViewById(R.id.headerTextView)
        descriptionTextView = findViewById(R.id.descriptionTextView)
        contentLayout = findViewById(R.id.contentLayout)

        // Setup initial content
        updatePageContent(0)

        // Handle button click
        nextButton.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                updatePageContent(currentPage)
            } else {
                // Last page, go to main activity
                startMainActivity()
            }
        }
    }

    private fun updatePageContent(page: Int) {
        // Update indicators
        updateIndicators(page)

        // Update button text on last page
        nextButton.text = if (page == totalPages - 1) "Get Started" else "Next"

        // Update content based on page
        when (page) {
            0 -> {
                // First slide - Introduction
                headerTextView.text = "Breaking communication barriers through technology"

                // Set description text
                descriptionTextView.text = "SIGNIFY is a Filipino Sign Language (FSL) recognition application that helps deaf and mute individuals communicate with those who don't understand sign language.\n\nOur advanced YOLOv11 technology accurately detects hand gestures and facial expressions in real-time, converting them instantly to text or speech.\n\nWe're committed to creating a more inclusive world where everyone can be understood."

                // Clear any bullet points
                contentLayout.removeAllViews()
                descriptionTextView.visibility = View.VISIBLE
            }

            1 -> {
                // Second slide - Key Features
                headerTextView.text = "Key Features"

                // Hide description text and show bullet points
                descriptionTextView.visibility = View.GONE
                contentLayout.removeAllViews()

                // Create bullet points for features
                val features = listOf(
                    "Sign Detection: Accurately identifies FSL letters and words",
                    "Emotion Recognition: Detects facial expressions for proper tone in speech",
                    "Word Formation: \"Capture\" button saves detected signs to form words",
                    "Space Function: Add spaces between words for natural communication",
                    "Expressive Speech: \"Speak\" button uses detected emotion for natural tone"
                )

                // Add bullet points
                for (feature in features) {
                    addBulletPoint(feature)
                }
            }

            2 -> {
                // Third slide - Best Practices
                headerTextView.text = "For best results:"

                // Hide description text and show bullet points
                descriptionTextView.visibility = View.GONE
                contentLayout.removeAllViews()

                // Create bullet points for best practices
                val bestPractices = listOf(
                    "Position camera 3 feet away",
                    "Ensure good lighting on hands and face",
                    "Keep hands clearly visible in the frame",
                    "Make facial expressions visible for full context",
                    "Sign at a natural pace",
                    "One sign at a time for better accuracy",
                    "Use \"Clear\" buttons to restart if needed"
                )

                // Add bullet points
                for (practice in bestPractices) {
                    addBulletPoint(practice)
                }
            }
        }

        // Add swipe detection
        setupSwipeDetection()
    }

    private fun setupSwipeDetection() {
        // Apply swipe detection to both top and bottom cards
        val topCard = findViewById<View>(R.id.topCard)
        val bottomCard = findViewById<View>(R.id.bottomCard)

        val swipeListener = object : OnSwipeTouchListener(this@OnboardingActivity) {
            override fun onSwipeLeft() {
                if (currentPage < totalPages - 1) {
                    currentPage++
                    updatePageContent(currentPage)
                }
            }

            override fun onSwipeRight() {
                if (currentPage > 0) {
                    currentPage--
                    updatePageContent(currentPage)
                }
            }
        }

        // Apply the same listener to both cards
        topCard.setOnTouchListener(swipeListener)
        bottomCard.setOnTouchListener(swipeListener)
    }
    private fun addBulletPoint(text: String) {
        // Create text view for bullet point
        val bulletPoint = TextView(this).apply {
            this.text = "• $text"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setPadding(0, 0, 0, 24) // Add space between bullet points
        }

        // Add to layout
        contentLayout.addView(bulletPoint)
    }

    private fun updateIndicators(position: Int) {
        val activeColor = ContextCompat.getColor(this, android.R.color.white)
        val inactiveColor = ContextCompat.getColor(this, R.color.translucent_white)

        dotIndicator1.setCardBackgroundColor(if (position == 0) activeColor else inactiveColor)
        dotIndicator2.setCardBackgroundColor(if (position == 1) activeColor else inactiveColor)
        dotIndicator3.setCardBackgroundColor(if (position == 2) activeColor else inactiveColor)
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}