package com.surendramaran.yolov8tflite

import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.surendramaran.yolov8tflite.databinding.ActivityAllSignsBinding

class AllSignsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAllSignsBinding

    // All signs that can be detected with their corresponding resource IDs
    private val alphabetSigns = listOf(
        "A", "B", "C", "D", "E", "Enye", "F", "G", "H", "I", "J", "K",
        "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
    )

    // Map sign names to their corresponding image resources
    private val signImages = mapOf(
        // Alphabet signs
        "A" to R.drawable.a,
        "B" to R.drawable.b,
        "C" to R.drawable.c,
        "D" to R.drawable.d,
        "E" to R.drawable.e,
        "Enye" to R.drawable.enye,
        "F" to R.drawable.f,
        "G" to R.drawable.g,
        "H" to R.drawable.h,
        "I" to R.drawable.i,
        "J" to R.drawable.j,
        "K" to R.drawable.k,
        "L" to R.drawable.l,
        "M" to R.drawable.m,
        "N" to R.drawable.n,
        "O" to R.drawable.o,
        "P" to R.drawable.p,
        "Q" to R.drawable.q,
        "R" to R.drawable.r,
        "S" to R.drawable.s,
        "T" to R.drawable.t,
        "U" to R.drawable.u,
        "V" to R.drawable.v,
        "W" to R.drawable.w,
        "X" to R.drawable.x,
        "Y" to R.drawable.y,
        "Z" to R.drawable.z,

        // Word signs
        "Gabi" to R.drawable.gabi,
        "Kamusta" to R.drawable.kamusta,
        "Maganda" to R.drawable.maganda,
        "Mahal Kita" to R.drawable.mahal,
        "Pangalan" to R.drawable.pangalan,
        "Salamat" to R.drawable.salamat,
        "Tanghali" to R.drawable.tanghali,
        "Umaga" to R.drawable.umaga,

        // Emotion signs
        "Masaya" to R.drawable.memotion,
        "Galit" to R.drawable.galit,
        "Malungkot" to R.drawable.malungkot,
    )

    private val wordSigns = listOf(
        "Gabi", "Kamusta", "Maganda", "Mahal Kita", "Pangalan", "Salamat", "Tanghali", "Umaga"
    )

    private val emotionSigns = listOf("Masaya", "Galit", "Malungkot")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllSignsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up back button
        binding.backButton.setOnClickListener {
            finish()
        }

        // Find our layout views
        val gridLayout = findViewById<GridLayout>(R.id.alphabetGrid)
        val wordsLayout = findViewById<LinearLayout>(R.id.wordsLayout)
        val emotionsLayout = findViewById<LinearLayout>(R.id.emotionsLayout)

        // Fill the layouts with cards
        populateAlphabetGrid(gridLayout)
        populateSignsList(wordsLayout, wordSigns)
        populateSignsList(emotionsLayout, emotionSigns)
    }

    private fun populateAlphabetGrid(gridLayout: GridLayout) {
        // Set columns
        gridLayout.columnCount = (if (alphabetSigns.size > 16) 4 else 3)

        // Clear existing views
        gridLayout.removeAllViews()

        // Add alphabet signs
        for (sign in alphabetSigns) {
            val cardView = createSignCard(sign)

            // Set layout parameters for the grid
            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = GridLayout.LayoutParams.WRAP_CONTENT
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            params.setMargins(8, 8, 8, 8)

            cardView.layoutParams = params
            gridLayout.addView(cardView)
        }
    }

    private fun populateSignsList(linearLayout: LinearLayout, signs: List<String>) {
        // Clear existing views
        linearLayout.removeAllViews()

        // Add signs
        for (sign in signs) {
            val cardView = createSignCard(sign)

            // Set layout parameters for the list
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(8, 8, 8, 8)

            cardView.layoutParams = params
            linearLayout.addView(cardView)
        }
    }

    private fun createSignCard(sign: String): CardView {
        // Create card view
        val cardView = CardView(this)
        cardView.radius = 16f
        cardView.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_background))
        cardView.cardElevation = 4f

        // Create vertical layout for the card content
        val cardContent = LinearLayout(this)
        cardContent.orientation = LinearLayout.VERTICAL
        cardContent.gravity = Gravity.CENTER
        cardContent.setPadding(16, 16, 16, 16)

        // Add the image
        val imageView = ImageView(this)
        imageView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(120) // Set the image height
        )
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER

        // Set image resource from the map, or use a placeholder if not found
        val resourceId = signImages[sign] ?: R.drawable.circle_background
        imageView.setImageResource(resourceId)

        // Add text label
        val textView = TextView(this)
        textView.text = sign
        textView.setTextColor(ContextCompat.getColor(this, R.color.white))
        textView.textSize = 16f
        textView.gravity = Gravity.CENTER
        textView.setPadding(8, 16, 8, 8)

        // Add views to card
        cardContent.addView(imageView)
        cardContent.addView(textView)
        cardView.addView(cardContent)

        return cardView
    }

    // Helper method to convert dp to pixels
    private fun dpToPx(dp: Int): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }
}