package com.StressDetection

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min



import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class StressAnalyzer {
    private var currentEmotions: List<Pair<String, Float>> = emptyList()
    private var currentLandmarks: FaceLandmarks? = null
    private val emotionHistory = mutableListOf<List<Pair<String, Float>>>()
    private val landmarkHistory = mutableListOf<FaceLandmarks>()
    private var analysisStartTime = System.currentTimeMillis()

    // Enhanced emotion scoring system
    private val emotionStressPoints = mapOf(
        "Happy" to -15f,        // Happy reduces stress significantly
        "Normal" to 0f,         // Neutral baseline
        "Sad" to 25f,           // Moderate stress
        "Overwhelmed" to 35f,   // High moderate stress
        "Anxious" to 40f,       // High moderate stress
        "Irritated" to 30f,     // Moderate stress
        "Worried" to 35f,       // High moderate stress
        "Fear" to 45f,          // High stress
        "Angry" to 50f          // Very high stress
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

        if (emotionHistory.size > 20) {
            emotionHistory.removeAt(0)
        }
    }

    fun updateLandmarks(landmarks: FaceLandmarks) {
        currentLandmarks = landmarks
        landmarkHistory.add(landmarks)

        if (landmarkHistory.size > 20) {
            landmarkHistory.removeAt(0)
        }
    }

    fun getReliabilityScore(): Int {
        val emotionSamples = emotionHistory.size
        val landmarkSamples = landmarkHistory.size
        val analysisTime = (System.currentTimeMillis() - analysisStartTime) / 1000f

        val samplesScore = min(100, ((emotionSamples + landmarkSamples) * 4))
        val timeScore = min(100, (analysisTime * 8).toInt())
        val stabilityScore = calculateStabilityScore()

        return ((samplesScore + timeScore + stabilityScore) / 3f).toInt()
    }

    private fun calculateStabilityScore(): Int {
        if (emotionHistory.size < 3) return 0

        val recentEmotions = emotionHistory.takeLast(5)
        val emotionVariance = calculateEmotionVariance(recentEmotions)

        return max(0, (100 - emotionVariance * 100).toInt())
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

    fun calculateStressLevel(): StressAnalysisResult {
        // Calculate comprehensive stress score (0-100+)
        val emotionPoints = calculateEmotionStressPoints()
        val landmarkPoints = calculateLandmarkStressPoints()
        val behavioralPoints = calculateBehavioralStressPoints()

        val totalStressPoints = emotionPoints + landmarkPoints + behavioralPoints

        // Three-stage classification
        val stressLevel = when {
            totalStressPoints <= 30f -> 1  // Low stress
            totalStressPoints <= 70f -> 2  // Moderate stress
            else -> 3                      // High stress
        }

        val dominantEmotion = currentEmotions.maxByOrNull { it.second }?.first ?: "Unknown"

        return StressAnalysisResult(
            level = stressLevel,
            score = min(100, max(0, totalStressPoints.toInt())),
            emotionScore = min(60, max(0, emotionPoints.toInt())),
            facialTensionScore = min(25, max(0, landmarkPoints.toInt())),
            eyeFatigueScore = min(15, max(0, behavioralPoints.toInt())),
            dominantEmotion = dominantEmotion
        )
    }

    private fun calculateEmotionStressPoints(): Float {
        if (currentEmotions.isEmpty()) return 0f

        var totalStressPoints = 0f
        var totalConfidence = 0f
        var emotionMultiplier = 1f

        currentEmotions.forEach { (emotion, confidence) ->
            val stressPoints = emotionStressPoints[emotion] ?: 0f
            totalStressPoints += stressPoints * confidence
            totalConfidence += confidence
        }

        // Multiple simultaneous negative emotions increase severity
        val negativeEmotions = currentEmotions.count { (emotion, confidence) ->
            confidence > 0.3f && (emotionStressPoints[emotion] ?: 0f) > 0
        }

        if (negativeEmotions >= 2) {
            emotionMultiplier = 1.3f // 30% increase for multiple stress emotions
        }

        return if (totalConfidence > 0) {
            (totalStressPoints / totalConfidence) * emotionMultiplier
        } else 0f
    }

    private fun calculateLandmarkStressPoints(): Float {
        val landmarks = currentLandmarks ?: return 0f

        var stressPoints = 0f

        // Eye region analysis (0-15 points)
        stressPoints += calculateEyeStressPoints(landmarks)

        // Eyebrow/forehead tension (0-8 points)
        stressPoints += landmarks.eyebrowTension * 8f

        // Mouth/jaw tension (0-5 points)
        stressPoints += landmarks.mouthTension * 5f

        // Overall facial asymmetry and tension (0-7 points)
        stressPoints += landmarks.overallFacialTension * 7f

        // NEW: Enhanced facial stress indicators
        // Forehead wrinkles (0-6 points)
        stressPoints += landmarks.foreheadWrinkles * 6f

        // Jaw tension (0-5 points)
        stressPoints += landmarks.jawTension * 5f

        // Dark circles/fatigue (0-4 points)
        stressPoints += landmarks.darkCircles * 4f

        // Skin stress indicators (0-3 points)
        stressPoints += landmarks.skinStress * 3f

        // Facial asymmetry (0-3 points)
        stressPoints += landmarks.facialAsymmetry * 3f

        return stressPoints
    }

    private fun calculateEyeStressPoints(landmarks: FaceLandmarks): Float {
        var eyeStress = 0f

        // Eye openness analysis (0-6 points)
        val avgEyeOpenness = (landmarks.leftEyeOpenness + landmarks.rightEyeOpenness) / 2f
        val eyeAsymmetry = abs(landmarks.leftEyeOpenness - landmarks.rightEyeOpenness)

        // Squinting or very wide eyes indicate stress
        when {
            avgEyeOpenness < 0.3f -> eyeStress += 4f  // Severe squinting
            avgEyeOpenness < 0.5f -> eyeStress += 2f  // Moderate squinting
            avgEyeOpenness > 0.9f -> eyeStress += 3f  // Very wide eyes (surprise/fear)
        }

        // Eye asymmetry indicates stress
        if (eyeAsymmetry > 0.2f) {
            eyeStress += 2f
        }

        // Eye bags severity (0-4 points)
        eyeStress += landmarks.eyeBagSeverity * 4f

        // Eye strain indicators (0-5 points)
        val eyeStrainFactor = calculateEyeStrainFactor(landmarks)
        eyeStress += eyeStrainFactor * 5f

        return eyeStress
    }

    private fun calculateEyeStrainFactor(landmarks: FaceLandmarks): Float {
        val avgEyeOpenness = (landmarks.leftEyeOpenness + landmarks.rightEyeOpenness) / 2f
        val eyeBags = landmarks.eyeBagSeverity
        val eyebrowTension = landmarks.eyebrowTension

        // Combine factors for eye strain
        return (
                (1f - avgEyeOpenness) * 0.4f +  // Lower openness = more strain
                        eyeBags * 0.3f +                // Eye bags = fatigue
                        eyebrowTension * 0.3f           // Furrowed brows = concentration/strain
                )
    }

    private fun calculateBehavioralStressPoints(): Float {
        var behavioralStress = 0f

        // Rapid emotion changes indicate instability (0-5 points)
        if (emotionHistory.size >= 3) {
            val emotionChanges = calculateEmotionInstability()
            behavioralStress += emotionChanges * 5f
        }

        // Persistent negative states (0-5 points)
        val persistentNegative = calculatePersistentNegativeState()
        behavioralStress += persistentNegative * 5f

        // Micro-expression intensity (0-5 points)
        val microExpressionIntensity = calculateMicroExpressionIntensity()
        behavioralStress += microExpressionIntensity * 5f

        return behavioralStress
    }

    private fun calculateEmotionInstability(): Float {
        if (emotionHistory.size < 3) return 0f

        val recent = emotionHistory.takeLast(3)
        var changes = 0

        for (i in 1 until recent.size) {
            val prev = recent[i-1].maxByOrNull { it.second }?.first
            val curr = recent[i].maxByOrNull { it.second }?.first

            if (prev != curr && prev != null && curr != null) {
                changes++
            }
        }

        return changes / 2f // Normalize to 0-1
    }

    private fun calculatePersistentNegativeState(): Float {
        if (emotionHistory.size < 3) return 0f

        val recentNegativeCount = emotionHistory.takeLast(5).count { emotions ->
            val dominant = emotions.maxByOrNull { it.second }
            dominant != null && (emotionStressPoints[dominant.first] ?: 0f) > 20f
        }

        return recentNegativeCount / 5f // Normalize to 0-1
    }

    private fun calculateMicroExpressionIntensity(): Float {
        return currentEmotions.maxOfOrNull { it.second } ?: 0f
    }

    fun getDetailedAnalysis(): String {
        val stressData = calculateStressLevel()
        val landmarks = currentLandmarks
        val reliability = getReliabilityScore()

        return buildString {
            appendLine("=== ENHANCED STRESS ANALYSIS ===")
            appendLine("Timestamp: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}")
            appendLine("Analysis Confidence: $reliability%")
            appendLine("")

            appendLine("STRESS CLASSIFICATION:")
            when (stressData.level) {
                1 -> {
                    appendLine("✅ LEVEL 1 - LOW STRESS (0-30 points)")
                    appendLine("Score: ${stressData.score}/100")
                    appendLine("Status: Primarily happy/normal emotions detected")
                }
                2 -> {
                    appendLine("⚠️ LEVEL 2 - MODERATE STRESS (31-70 points)")
                    appendLine("Score: ${stressData.score}/100")
                    appendLine("Status: Anxious, worried, or overwhelmed states")
                }
                3 -> {
                    appendLine("🚨 LEVEL 3 - HIGH STRESS (71+ points)")
                    appendLine("Score: ${stressData.score}/100")
                    appendLine("Status: Angry, fearful, or severely distressed")
                }
            }
            appendLine("")

            appendLine("DETAILED BREAKDOWN:")
            appendLine("• Emotional Impact: ${stressData.emotionScore}/60 points")
            appendLine("  └─ Primary: ${stressData.dominantEmotion}")
            appendLine("• Facial Tension: ${stressData.facialTensionScore}/25 points")
            appendLine("  └─ ${getFacialTensionDescription(landmarks)}")
            appendLine("• Behavioral Stress: ${stressData.eyeFatigueScore}/15 points")
            appendLine("  └─ ${getBehavioralDescription(landmarks)}")
            appendLine("")

            landmarks?.let {
                appendLine("FACIAL LANDMARK ANALYSIS:")
                appendLine("• Eye Analysis:")
                appendLine("  - Left Eye Openness: ${(it.leftEyeOpenness * 100).toInt()}%")
                appendLine("  - Right Eye Openness: ${(it.rightEyeOpenness * 100).toInt()}%")
                appendLine("  - Eye Asymmetry: ${getEyeAsymmetryLevel(it)}")
                appendLine("  - Eye Bags: ${getEyeBagLevel(it)}")
                appendLine("  - Dark Circles: ${getDarkCircleLevel(it)}")
                appendLine("• Facial Tension:")
                appendLine("  - Eyebrow Tension: ${(it.eyebrowTension * 100).toInt()}%")
                appendLine("  - Forehead Wrinkles: ${getForeheadWrinkleLevel(it)}")
                appendLine("  - Mouth Tension: ${(it.mouthTension * 100).toInt()}%")
                appendLine("  - Jaw Tension: ${getJawTensionLevel(it)}")
                appendLine("  - Overall Tension: ${(it.overallFacialTension * 100).toInt()}%")
                appendLine("• Additional Indicators:")
                appendLine("  - Facial Asymmetry: ${getFacialAsymmetryLevel(it)}")
                appendLine("  - Skin Stress: ${getSkinStressLevel(it)}")
                appendLine("")
            }

            appendLine("STRESS INDICATORS:")
            appendLine("• Emotion Stability: ${getEmotionStabilityDescription()}")
            appendLine("• Micro-expressions: ${getMicroExpressionDescription()}")
            appendLine("• Analysis Duration: ${getAnalysisDuration()} seconds")
            appendLine("")

            appendLine("RECOMMENDATIONS:")
            when (stressData.level) {
                1 -> {
                    appendLine("✅ Excellent! You're in a relaxed state")
                    appendLine("• Continue current activities")
                    appendLine("• Maintain this positive emotional state")
                }
                2 -> {
                    appendLine("⚠️ Moderate stress management needed:")
                    appendLine("• Take 10 deep breaths")
                    appendLine("• Consider a 5-minute break")
                    appendLine("• Practice progressive muscle relaxation")
                    appendLine("• Address the source of worry/anxiety")
                }
                3 -> {
                    appendLine("🚨 Immediate stress intervention required:")
                    appendLine("• Stop current stressful activities immediately")
                    appendLine("• Practice deep breathing for 2-3 minutes")
                    appendLine("• Use grounding techniques (5-4-3-2-1 method)")
                    appendLine("• Consider speaking with someone for support")
                    appendLine("• If persistent, seek professional help")
                }
            }
        }
    }

    private fun getFacialTensionDescription(landmarks: FaceLandmarks?): String {
        return landmarks?.let {
            when {
                it.overallFacialTension > 0.7f -> "High tension across multiple facial regions"
                it.overallFacialTension > 0.4f -> "Moderate tension detected"
                else -> "Minimal facial tension"
            }
        } ?: "No facial data available"
    }

    private fun getBehavioralDescription(landmarks: FaceLandmarks?): String {
        return landmarks?.let {
            val avgOpenness = (it.leftEyeOpenness + it.rightEyeOpenness) / 2f
            when {
                avgOpenness < 0.3f -> "Severe eye strain/squinting detected"
                avgOpenness < 0.5f -> "Moderate eye fatigue present"
                avgOpenness > 0.9f -> "Heightened alertness/surprise"
                else -> "Normal eye behavior"
            }
        } ?: "No behavioral data available"
    }

    private fun getEyeAsymmetryLevel(landmarks: FaceLandmarks): String {
        val asymmetry = abs(landmarks.leftEyeOpenness - landmarks.rightEyeOpenness)
        return when {
            asymmetry > 0.3f -> "High asymmetry (stress indicator)"
            asymmetry > 0.15f -> "Moderate asymmetry"
            else -> "Symmetric (good)"
        }
    }

    private fun getEyeBagLevel(landmarks: FaceLandmarks): String {
        return when {
            landmarks.eyeBagSeverity > 0.7f -> "Severe (chronic fatigue)"
            landmarks.eyeBagSeverity > 0.4f -> "Moderate (some fatigue)"
            landmarks.eyeBagSeverity > 0.2f -> "Light (minimal fatigue)"
            else -> "None detected"
        }
    }

    private fun getEmotionStabilityDescription(): String {
        val stability = calculateStabilityScore()
        return when {
            stability > 80 -> "Very stable emotions"
            stability > 60 -> "Moderately stable"
            stability > 40 -> "Some emotional fluctuation"
            else -> "High emotional instability"
        }
    }

    private fun getMicroExpressionDescription(): String {
        val maxConfidence = currentEmotions.maxOfOrNull { it.second } ?: 0f
        return when {
            maxConfidence > 0.8f -> "Strong, clear expressions"
            maxConfidence > 0.6f -> "Moderate expression intensity"
            maxConfidence > 0.4f -> "Subtle expressions"
            else -> "Minimal expression detected"
        }
    }

    private fun getAnalysisDuration(): Int {
        return ((System.currentTimeMillis() - analysisStartTime) / 1000).toInt()
    }

    // NEW: Helper methods for enhanced facial indicators
    private fun getDarkCircleLevel(landmarks: FaceLandmarks): String {
        return when {
            landmarks.darkCircles > 0.7f -> "Severe (chronic fatigue)"
            landmarks.darkCircles > 0.5f -> "Moderate (some fatigue)"
            landmarks.darkCircles > 0.3f -> "Mild (slight fatigue)"
            else -> "None detected"
        }
    }

    private fun getForeheadWrinkleLevel(landmarks: FaceLandmarks): String {
        return when {
            landmarks.foreheadWrinkles > 0.7f -> "Deep wrinkles (high stress)"
            landmarks.foreheadWrinkles > 0.5f -> "Moderate wrinkles (some stress)"
            landmarks.foreheadWrinkles > 0.3f -> "Light wrinkles (mild stress)"
            else -> "Smooth forehead"
        }
    }

    private fun getJawTensionLevel(landmarks: FaceLandmarks): String {
        return when {
            landmarks.jawTension > 0.7f -> "High tension (possible clenching)"
            landmarks.jawTension > 0.5f -> "Moderate tension"
            landmarks.jawTension > 0.3f -> "Mild tension"
            else -> "Relaxed jaw"
        }
    }

    private fun getFacialAsymmetryLevel(landmarks: FaceLandmarks): String {
        return when {
            landmarks.facialAsymmetry > 0.6f -> "High asymmetry (stress indicator)"
            landmarks.facialAsymmetry > 0.4f -> "Moderate asymmetry"
            landmarks.facialAsymmetry > 0.2f -> "Mild asymmetry"
            else -> "Symmetrical face"
        }
    }

    private fun getSkinStressLevel(landmarks: FaceLandmarks): String {
        return when {
            landmarks.skinStress > 0.7f -> "High skin stress indicators"
            landmarks.skinStress > 0.5f -> "Moderate skin stress"
            landmarks.skinStress > 0.3f -> "Mild skin stress"
            else -> "Normal skin condition"
        }
    }
}
data class FaceLandmarks(
    val leftEyeOpenness: Float,
    val rightEyeOpenness: Float,
    val eyebrowTension: Float,
    val eyeBagSeverity: Float,
    val mouthTension: Float,
    val overallFacialTension: Float,
    val landmarkPoints: Map<String, PointF> = emptyMap(),
    // Enhanced stress indicators
    val foreheadWrinkles: Float = 0f,
    val jawTension: Float = 0f,
    val darkCircles: Float = 0f,
    val skinStress: Float = 0f,
    val facialAsymmetry: Float = 0f
)

data class StressAnalysisResult(
    val level: Int,           // 1, 2, or 3
    val score: Int,           // 0-100
    val emotionScore: Int,    // 0-60
    val facialTensionScore: Int,  // 0-25
    val eyeFatigueScore: Int,     // 0-15
    val dominantEmotion: String
)

data class CaptureResult(
    val bitmap: android.graphics.Bitmap?,
    val stressAnalysis: StressAnalysisResult,
    val detailedAnalysis: String,
    val timestamp: Long,
    val landmarks: FaceLandmarks?
)