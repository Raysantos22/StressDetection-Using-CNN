package com.StressDetection

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class OnboardingAdapter : RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    private val onboardingData = listOf(
        OnboardingData(
            "STRESS\nDETECTION\nAPP",
            "Stress is common among college students. The app is mainly concerned with the wellness of college students. Using face scan, the app will detect the stress level of the individual and suggest recommendation for the stress level.",
            0
        ),
        OnboardingData(
            "Easy To Use",
            "Grant Camera Access:\nThe app will likely request permission to access your phone's camera. Grant this permission.\n\nCapture Your Face:\nFollow the app's instructions to capture a video or image of your face.\n\nAnalysis and Results:\nThe app will then analyze the captured image or video to assess your stress level.\n\nFeedback and Recommendations:\nwill typically display your stress level as a score or provide feedback, suggesting strategies for managing stress.",
            1
        ),
        OnboardingData(
            "Analysis\nand\nFeedback:",
            "Ready to start your stress detection journey? Our advanced AI will analyze your facial expressions to provide personalized insights about your stress levels.",
            2
        )
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_page, parent, false)
        return OnboardingViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        holder.bind(onboardingData[position])
    }

    override fun getItemCount(): Int = onboardingData.size

    class OnboardingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.titleText)
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val illustrationContainer: RelativeLayout = itemView.findViewById(R.id.illustrationContainer)

        fun bind(data: OnboardingData) {
            titleText.text = data.title
            descriptionText.text = data.description

            when (data.pageNumber) {
                0 -> {
                    titleText.setTextColor(ContextCompat.getColor(itemView.context, R.color.black))
                    descriptionText.setTextColor(ContextCompat.getColor(itemView.context, R.color.dark_text))
                    illustrationContainer.visibility = View.GONE
                }
                1 -> {
                    titleText.setTextColor(ContextCompat.getColor(itemView.context, R.color.black))
                    descriptionText.setTextColor(ContextCompat.getColor(itemView.context, R.color.dark_text))
                    illustrationContainer.visibility = View.GONE
                }
                2 -> {
                    titleText.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
                    descriptionText.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
                    illustrationContainer.visibility = View.VISIBLE
                }
            }
        }
    }
}
data class OnboardingData(
    val title: String,
    val description: String,
    val pageNumber: Int
)
