package com.StressDetection

import android.graphics.PointF

class StressAnalyzer {
    private var currentEmotions: List<Pair<String, Float>> = emptyList()
    private var currentLandmarks: FaceLandmarks? = null
    private val emotionHistory = mutableListOf<List<Pair<String, Float>>>()
    private val landmarkHistory = mutableListOf<FaceLandmarks>()
    private var analysisStartTime = System.currentTimeMillis()

    private val emotionStressWeights = mapOf(
        "Happy" to -2f,
        "Normal" to 0f,
        "Sad" to 6f,
        "Overwhelmed" to 10f,
        "Anxious" to 9f,
        "Irritated" to 7f,
        "Worried" to 8f,
        "Fear" to 9f,
        "Angry" to 8f
    )

    fun reset() {
        currentEmotions = emptyList()
        currentLandmarks = null
        emotionHistory.clear()
        landmarkHistory.clear()
        analysisStartTime = System.currentTimeMillis()
    }

    fun updateEmotions(emotions: List<Pair<String, Float>>) {
        currentEmotions = emotions
        emotionHistory.add(emotions)

        if (emotionHistory.size > 15) {
            emotionHistory.removeAt(0)
        }
    }

    fun updateLandmarks(landmarks: FaceLandmarks) {
        currentLandmarks = landmarks
        landmarkHistory.add(landmarks)

        if (landmarkHistory.size > 15) {
            landmarkHistory.removeAt(0)
        }
    }

    fun getReliabilityScore(): Int {
        val emotionSamples = emotionHistory.size
        val landmarkSamples = landmarkHistory.size
        val analysisTime = (System.currentTimeMillis() - analysisStartTime) / 1000f

        val samplesScore = minOf(100, ((emotionSamples + landmarkSamples) * 5))
        val timeScore = minOf(100, (analysisTime * 10).toInt())
        val stabilityScore = calculateStabilityScore()

        return ((samplesScore + timeScore + stabilityScore) / 3f).toInt()
    }

    private fun calculateStabilityScore(): Int {
        if (emotionHistory.size < 3) return 0

        val recentEmotions = emotionHistory.takeLast(5)
        val emotionVariance = calculateEmotionVariance(recentEmotions)

        return maxOf(0, (100 - emotionVariance * 100).toInt())
    }

    private fun calculateEmotionVariance(emotions: List<List<Pair<String, Float>>>): Float {
        if (emotions.isEmpty()) return 1f

        val emotionCounts = mutableMapOf<String, Int>()
        emotions.forEach { emotionList ->
            emotionList.forEach { (emotion, _) ->
                emotionCounts[emotion] = emotionCounts.getOrDefault(emotion, 0) + 1
            }
        }

        val maxCount = emotionCounts.values.maxOrNull() ?: 0
        val totalCount = emotionCounts.values.sum()

        return if (totalCount > 0) 1f - (maxCount.toFloat() / totalCount) else 1f
    }

    fun getDetailedAnalysis(): String {
        val stressData = calculateStressLevel()
        val landmarks = currentLandmarks
        val reliability = getReliabilityScore()

        return buildString {
            appendLine("=== COMPREHENSIVE STRESS ANALYSIS ===")
            appendLine("Timestamp: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}")
            appendLine("Analysis Reliability: $reliability%")
            appendLine("")

            appendLine("STRESS ASSESSMENT:")
            appendLine("• Overall Level: ${getStressLevelDescription(stressData.level)}")
            appendLine("• Stress Score: ${stressData.score}/100")
            appendLine("• Primary Emotion: ${stressData.dominantEmotion}")
            appendLine("")

            appendLine("DETAILED BREAKDOWN:")
            appendLine("• Emotional Impact: ${stressData.emotionScore}/40")
            appendLine("  └─ ${getEmotionAnalysis()}")
            appendLine("• Facial Tension: ${stressData.facialTensionScore}/30")
            appendLine("  └─ ${getFacialTensionAnalysis(landmarks)}")
            appendLine("• Eye Fatigue: ${stressData.eyeFatigueScore}/30")
            appendLine("  └─ ${getEyeFatigueAnalysis(landmarks)}")
            appendLine("")

            landmarks?.let {
                appendLine("FACIAL METRICS:")
                appendLine("• Left Eye Openness: ${(it.leftEyeOpenness * 100).toInt()}%")
                appendLine("• Right Eye Openness: ${(it.rightEyeOpenness * 100).toInt()}%")
                appendLine("• Eyebrow Tension: ${(it.eyebrowTension * 100).toInt()}%")
                appendLine("• Mouth Tension: ${(it.mouthTension * 100).toInt()}%")
                appendLine("• Eye Bag Severity: ${(it.eyeBagSeverity * 100).toInt()}%")
                appendLine("• Overall Facial Tension: ${(it.overallFacialTension * 100).toInt()}%")
                appendLine("")
            }

            appendLine("TREND ANALYSIS:")
            appendLine("• Emotion Samples: ${emotionHistory.size}")
            appendLine("• Landmark Samples: ${landmarkHistory.size}")
            appendLine("• Analysis Stability: ${calculateStabilityScore()}%")
            appendLine("")

            appendLine("RECOMMENDATIONS:")
            when (stressData.level) {
                1 -> {
                    appendLine("✅ You appear to be in a relaxed state")
                    appendLine("• Continue current activities")
                    appendLine("• Maintain good posture and lighting")
                }
                2 -> {
                    appendLine("⚠️ Moderate stress detected")
                    appendLine("• Take 5-10 deep breaths")
                    appendLine("• Consider a short break")
                    appendLine("• Check your posture and environment")
                }
                3 -> {
                    appendLine("🚨 High stress levels detected")
                    appendLine("• Take immediate stress relief measures")
                    appendLine("• Practice deep breathing or meditation")
                    appendLine("• Consider stepping away from stressful tasks")
                    appendLine("• Seek support if stress persists")
                }
            }
        }
    }

    private fun getStressLevelDescription(level: Int): String {
        return when (level) {
            1 -> "Low Stress (Relaxed)"
            2 -> "Moderate Stress (Some Tension)"
            3 -> "High Stress (Significant Indicators)"
            else -> "Unknown"
        }
    }

    private fun getEmotionAnalysis(): String {
        if (currentEmotions.isEmpty()) return "No emotion data"

        val dominant = currentEmotions.maxByOrNull { it.second }
        return if (dominant != null) {
            "Primarily showing ${dominant.first} (${(dominant.second * 100).toInt()}% confidence)"
        } else "Mixed emotions detected"
    }

    private fun getFacialTensionAnalysis(landmarks: FaceLandmarks?): String {
        return landmarks?.let {
            when {
                it.overallFacialTension > 0.7f -> "High tension in multiple facial areas"
                it.overallFacialTension > 0.4f -> "Moderate tension detected"
                else -> "Minimal facial tension"
            }
        } ?: "No facial data available"
    }

    private fun getEyeFatigueAnalysis(landmarks: FaceLandmarks?): String {
        return landmarks?.let {
            val avgOpenness = (it.leftEyeOpenness + it.rightEyeOpenness) / 2f
            when {
                avgOpenness < 0.4f -> "Significant eye fatigue or squinting"
                avgOpenness < 0.6f -> "Moderate eye strain detected"
                else -> "Eyes appear alert and open"
            }
        } ?: "No eye data available"
    }

    fun calculateStressLevel(): StressAnalysisResult {
        val emotionScore = calculateEmotionStressScore()
        val facialTensionScore = calculateFacialTensionScore()
        val eyeFatigueScore = calculateEyeFatigueScore()

        val totalScore = emotionScore + facialTensionScore + eyeFatigueScore
        val normalizedScore = minOf(100f, maxOf(0f, totalScore))

        val stressLevel = when {
            normalizedScore <= 30f -> 1
            normalizedScore <= 65f -> 2
            else -> 3
        }

        val dominantEmotion = currentEmotions.maxByOrNull { it.second }?.first ?: "Unknown"

        return StressAnalysisResult(
            level = stressLevel,
            score = normalizedScore.toInt(),
            emotionScore = emotionScore.toInt(),
            facialTensionScore = facialTensionScore.toInt(),
            eyeFatigueScore = eyeFatigueScore.toInt(),
            dominantEmotion = dominantEmotion
        )
    }

    private fun calculateEmotionStressScore(): Float {
        if (currentEmotions.isEmpty()) return 0f

        var emotionStress = 0f
        var totalConfidence = 0f

        currentEmotions.forEach { (emotion, confidence) ->
            val stressWeight = emotionStressWeights[emotion] ?: 0f
            emotionStress += stressWeight * confidence
            totalConfidence += confidence
        }

        val normalizedScore = if (totalConfidence > 0) {
            (emotionStress / totalConfidence) * 4f
        } else 0f

        return maxOf(0f, minOf(40f, normalizedScore))
    }

    private fun calculateFacialTensionScore(): Float {
        val landmarks = currentLandmarks ?: return 0f

        val tensionFactors = listOf(
            landmarks.eyebrowTension * 0.35f,
            landmarks.mouthTension * 0.25f,
            landmarks.overallFacialTension * 0.4f
        )

        val avgTension = tensionFactors.average().toFloat()
        return avgTension * 30f
    }

    private fun calculateEyeFatigueScore(): Float {
        val landmarks = currentLandmarks ?: return 0f

        val eyeOpenness = (landmarks.leftEyeOpenness + landmarks.rightEyeOpenness) / 2f
        val eyeFatigue = 1f - eyeOpenness
        val eyeBagContribution = landmarks.eyeBagSeverity

        val combinedFatigue = (eyeFatigue * 0.6f + eyeBagContribution * 0.4f)
        return combinedFatigue * 30f
    }
}
// Data classes
data class FaceLandmarks(
    val leftEyeOpenness: Float,
    val rightEyeOpenness: Float,
    val eyebrowTension: Float,
    val eyeBagSeverity: Float,
    val mouthTension: Float,
    val overallFacialTension: Float,
    val landmarkPoints: Map<String, PointF> = emptyMap()
)

data class StressAnalysisResult(
    val level: Int,           // 1, 2, or 3
    val score: Int,           // 0-100
    val emotionScore: Int,    // 0-40
    val facialTensionScore: Int,  // 0-30
    val eyeFatigueScore: Int,     // 0-30
    val dominantEmotion: String
)

data class CaptureResult(
    val bitmap: android.graphics.Bitmap?,
    val stressAnalysis: StressAnalysisResult,
    val detailedAnalysis: String,
    val timestamp: Long,
    val landmarks: FaceLandmarks?
)