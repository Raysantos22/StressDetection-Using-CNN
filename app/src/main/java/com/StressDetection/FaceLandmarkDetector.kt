package com.StressDetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.sqrt

class FaceLandmarkDetector(
    private val context: Context,
    private val landmarkListener: LandmarkListener
) {
    private val detector: FaceDetector

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()

        detector = FaceDetection.getClient(options)
    }

    fun detectLandmarks(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces[0]
                    val landmarks = extractLandmarks(face, bitmap.width, bitmap.height)
                    landmarkListener.onLandmarksDetected(landmarks)
                } else {
                    landmarkListener.onLandmarksDetected(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e("FaceLandmark", "Face detection failed", e)
                landmarkListener.onLandmarksDetected(null)
            }
    }

    private fun extractLandmarks(face: Face, imageWidth: Int, imageHeight: Int): FaceLandmarks {
        // Extract actual landmark positions
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)
        val leftMouth = face.getLandmark(FaceLandmark.MOUTH_LEFT)
        val rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT)
        val bottomMouth = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)
        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)

        // Get face contours for better analysis
        val faceContour = face.getContour(FaceContour.FACE)
        val leftEyebrowTop = face.getContour(FaceContour.LEFT_EYEBROW_TOP)
        val rightEyebrowTop = face.getContour(FaceContour.RIGHT_EYEBROW_TOP)
        val leftEyeContour = face.getContour(FaceContour.LEFT_EYE)
        val rightEyeContour = face.getContour(FaceContour.RIGHT_EYE)

        // Calculate enhanced metrics
        val leftEyeOpenness = calculateEyeOpenness(face, true)
        val rightEyeOpenness = calculateEyeOpenness(face, false)
        val eyebrowTension = calculateEyebrowTension(face, leftEyebrowTop, rightEyebrowTop, leftEye, rightEye)
        val eyeBagSeverity = estimateEyeBags(face, leftEyeContour, rightEyeContour)
        val mouthTension = calculateMouthTension(face, leftMouth, rightMouth, bottomMouth)
        val overallTension = calculateOverallTension(face)

        // NEW: Additional stress indicators
        val foreheadWrinkles = calculateForeheadWrinkles(face, leftEyebrowTop, rightEyebrowTop)
        val jawTension = calculateJawTension(face, faceContour)
        val darkCircles = estimateDarkCircles(face, leftEyeContour, rightEyeContour)
        val skinStress = calculateSkinStressIndicators(face)
        val facialAsymmetry = calculateFacialAsymmetry(face)

        // Convert landmark positions to normalized coordinates (0-1)
        val normalizedLandmarks = mutableMapOf<String, PointF>()

        leftEye?.let {
            normalizedLandmarks["LEFT_EYE"] = PointF(it.position.x / imageWidth, it.position.y / imageHeight)
        }
        rightEye?.let {
            normalizedLandmarks["RIGHT_EYE"] = PointF(it.position.x / imageWidth, it.position.y / imageHeight)
        }
        noseBase?.let {
            normalizedLandmarks["NOSE_BASE"] = PointF(it.position.x / imageWidth, it.position.y / imageHeight)
        }
        leftMouth?.let {
            normalizedLandmarks["MOUTH_LEFT"] = PointF(it.position.x / imageWidth, it.position.y / imageHeight)
        }
        rightMouth?.let {
            normalizedLandmarks["MOUTH_RIGHT"] = PointF(it.position.x / imageWidth, it.position.y / imageHeight)
        }
        bottomMouth?.let {
            normalizedLandmarks["MOUTH_BOTTOM"] = PointF(it.position.x / imageWidth, it.position.y / imageHeight)
        }

        // Add eyebrow points if available
        leftEyebrowTop?.points?.let { points ->
            if (points.isNotEmpty()) {
                val centerPoint = points[points.size / 2]
                normalizedLandmarks["LEFT_EYEBROW"] = PointF(centerPoint.x / imageWidth, centerPoint.y / imageHeight)
            }
        }
        rightEyebrowTop?.points?.let { points ->
            if (points.isNotEmpty()) {
                val centerPoint = points[points.size / 2]
                normalizedLandmarks["RIGHT_EYEBROW"] = PointF(centerPoint.x / imageWidth, centerPoint.y / imageHeight)
            }
        }

        // Add cheek points for additional analysis
        leftCheek?.let {
            normalizedLandmarks["LEFT_CHEEK"] = PointF(it.position.x / imageWidth, it.position.y / imageHeight)
        }
        rightCheek?.let {
            normalizedLandmarks["RIGHT_CHEEK"] = PointF(it.position.x / imageWidth, it.position.y / imageHeight)
        }

        return FaceLandmarks(
            leftEyeOpenness = leftEyeOpenness,
            rightEyeOpenness = rightEyeOpenness,
            eyebrowTension = eyebrowTension,
            eyeBagSeverity = eyeBagSeverity,
            mouthTension = mouthTension,
            overallFacialTension = overallTension,
            landmarkPoints = normalizedLandmarks,
            // NEW: Enhanced stress indicators
            foreheadWrinkles = foreheadWrinkles,
            jawTension = jawTension,
            darkCircles = darkCircles,
            skinStress = skinStress,
            facialAsymmetry = facialAsymmetry
        )
    }

    private fun calculateEyeOpenness(face: Face, isLeftEye: Boolean): Float {
        return if (face.leftEyeOpenProbability != null && face.rightEyeOpenProbability != null) {
            if (isLeftEye) face.leftEyeOpenProbability!! else face.rightEyeOpenProbability!!
        } else 0.5f
    }

    private fun calculateEyebrowTension(
        face: Face,
        leftEyebrowTop: FaceContour?,
        rightEyebrowTop: FaceContour?,
        leftEye: FaceLandmark?,
        rightEye: FaceLandmark?
    ): Float {
        return try {
            var tensionScore = 0f

            // Method 1: Distance between eyebrows and eyes
            if (leftEyebrowTop != null && rightEyebrowTop != null && leftEye != null && rightEye != null) {
                val leftEyebrowPoints = leftEyebrowTop.points
                val rightEyebrowPoints = rightEyebrowTop.points

                if (leftEyebrowPoints.isNotEmpty() && rightEyebrowPoints.isNotEmpty()) {
                    val leftEyebrowCenter = leftEyebrowPoints[leftEyebrowPoints.size / 2]
                    val rightEyebrowCenter = rightEyebrowPoints[rightEyebrowPoints.size / 2]

                    val leftDistance = abs(leftEyebrowCenter.y - leftEye.position.y)
                    val rightDistance = abs(rightEyebrowCenter.y - rightEye.position.y)
                    val avgDistance = (leftDistance + rightDistance) / 2f

                    // Closer eyebrows to eyes = more tension
                    val normalizedTension = 1f - (avgDistance / 50f) // 50px as reference
                    tensionScore += maxOf(0f, minOf(1f, normalizedTension)) * 0.6f
                }
            }

            // Method 2: Eye openness as tension indicator
            val leftEyeOpen = face.leftEyeOpenProbability ?: 0.5f
            val rightEyeOpen = face.rightEyeOpenProbability ?: 0.5f
            val avgEyeOpen = (leftEyeOpen + rightEyeOpen) / 2f

            // Squinting indicates eyebrow tension
            if (avgEyeOpen < 0.7f) {
                tensionScore += (0.7f - avgEyeOpen) * 0.4f
            }

            // Method 3: Head pose indicating concentration/stress
            val headY = abs(face.headEulerAngleY)
            val headX = abs(face.headEulerAngleX)

            // Forward head posture or tilted head can indicate tension
            if (headX > 10f || headY > 20f) {
                tensionScore += 0.2f
            }

            // Method 4: Eye asymmetry (one eyebrow more tense)
            val eyeAsymmetry = abs(leftEyeOpen - rightEyeOpen)
            if (eyeAsymmetry > 0.2f) {
                tensionScore += eyeAsymmetry * 0.3f
            }

            return maxOf(0f, minOf(1f, tensionScore))
        } catch (e: Exception) {
            Log.e("FaceLandmark", "Error calculating eyebrow tension", e)
            0f
        }
    }

    private fun estimateEyeBags(face: Face, leftEyeContour: FaceContour?, rightEyeContour: FaceContour?): Float {
        return try {
            // Enhanced eye bag detection
            val leftEyeOpen = face.leftEyeOpenProbability ?: 0.5f
            val rightEyeOpen = face.rightEyeOpenProbability ?: 0.5f
            val avgEyeOpen = (leftEyeOpen + rightEyeOpen) / 2f

            // Lower eye openness combined with other factors
            var eyeBagScore = 0f

            // Factor 1: Reduced eye openness (fatigue indicator)
            if (avgEyeOpen < 0.6f) {
                eyeBagScore += (0.6f - avgEyeOpen) * 2f
            }

            // Factor 2: Eye asymmetry (one eye more tired)
            val eyeAsymmetry = kotlin.math.abs(leftEyeOpen - rightEyeOpen)
            if (eyeAsymmetry > 0.15f) {
                eyeBagScore += eyeAsymmetry * 1.5f
            }

            // Factor 3: Head pose (tired posture)
            val headY = face.headEulerAngleY
            val headZ = face.headEulerAngleZ
            if (kotlin.math.abs(headY) > 15f || kotlin.math.abs(headZ) > 10f) {
                eyeBagScore += 0.2f
            }

            // Factor 4: Eye contour analysis if available
            if (leftEyeContour != null && rightEyeContour != null) {
                val leftHeight = calculateEyeHeight(leftEyeContour.points)
                val rightHeight = calculateEyeHeight(rightEyeContour.points)
                val avgHeight = (leftHeight + rightHeight) / 2f

                // Lower height ratio indicates puffiness/bags
                if (avgHeight < 15f) {
                    eyeBagScore += (15f - avgHeight) / 15f * 0.3f
                }
            }

            return kotlin.math.max(0f, kotlin.math.min(1f, eyeBagScore))
        } catch (e: Exception) {
            Log.e("FaceLandmark", "Error estimating eye bags", e)
            0f
        }
    }

    private fun calculateEyeHeight(eyePoints: List<PointF>): Float {
        if (eyePoints.size < 6) return 0f

        val topPoint = eyePoints.minByOrNull { it.y }
        val bottomPoint = eyePoints.maxByOrNull { it.y }

        return if (topPoint != null && bottomPoint != null) {
            abs(bottomPoint.y - topPoint.y)
        } else 0f
    }

    private fun calculateMouthTension(
        face: Face,
        leftMouth: FaceLandmark?,
        rightMouth: FaceLandmark?,
        bottomMouth: FaceLandmark?
    ): Float {
        return if (leftMouth != null && rightMouth != null && bottomMouth != null) {
            val width = abs(rightMouth.position.x - leftMouth.position.x)
            val centerY = (leftMouth.position.y + rightMouth.position.y) / 2
            val height = abs(bottomMouth.position.y - centerY)

            if (height > 0) {
                val ratio = width / height
                // Higher width-to-height ratio indicates tension
                minOf(1f, maxOf(0f, (ratio - 2f) / 4f))
            } else 0f
        } else 0f
    }

    private fun calculateOverallTension(face: Face): Float {
        val leftEyeOpen = face.leftEyeOpenProbability ?: 0.5f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 0.5f
        val avgEyeOpen = (leftEyeOpen + rightEyeOpen) / 2f

        // Lower eye openness contributes to tension
        val eyeTension = maxOf(0f, (0.6f - avgEyeOpen) * 1.5f)

        // Use head rotation for additional tension indicators
        val headY = face.headEulerAngleY
        val headZ = face.headEulerAngleZ
        val headTension = (abs(headY) + abs(headZ)) / 90f // Normalize to 0-1

        return minOf(1f, (eyeTension * 0.7f + headTension * 0.3f))
    }

    // NEW: Enhanced facial stress indicators
    private fun calculateForeheadWrinkles(face: Face, leftEyebrowTop: FaceContour?, rightEyebrowTop: FaceContour?): Float {
        return try {
            var wrinkleScore = 0f

            // Method 1: Eyebrow position analysis
            if (leftEyebrowTop != null && rightEyebrowTop != null) {
                val leftPoints = leftEyebrowTop.points
                val rightPoints = rightEyebrowTop.points

                if (leftPoints.size >= 3 && rightPoints.size >= 3) {
                    // Analyze eyebrow curvature for wrinkle detection
                    val leftCurvature = calculateEyebrowCurvature(leftPoints)
                    val rightCurvature = calculateEyebrowCurvature(rightPoints)
                    val avgCurvature = (leftCurvature + rightCurvature) / 2f

                    // Higher curvature indicates furrowed brow
                    wrinkleScore += avgCurvature * 0.6f
                }
            }

            // Method 2: Head pose indicating concentration/stress
            val headX = abs(face.headEulerAngleX)
            if (headX > 5f) { // Forward head lean
                wrinkleScore += (headX / 30f) * 0.4f
            }

            maxOf(0f, minOf(1f, wrinkleScore))
        } catch (e: Exception) {
            Log.e("FaceLandmark", "Error calculating forehead wrinkles", e)
            0f
        }
    }

    private fun calculateEyebrowCurvature(eyebrowPoints: List<PointF>): Float {
        if (eyebrowPoints.size < 3) return 0f

        // Calculate the "bend" in eyebrow line
        val start = eyebrowPoints.first()
        val middle = eyebrowPoints[eyebrowPoints.size / 2]
        val end = eyebrowPoints.last()

        // Calculate deviation from straight line
        val straightLineY = start.y + (end.y - start.y) * 0.5f
        val deviation = abs(middle.y - straightLineY)

        return minOf(1f, deviation / 20f) // Normalize
    }

    private fun calculateJawTension(face: Face, faceContour: FaceContour?): Float {
        return try {
            var jawTension = 0f

            // Method 1: Face width analysis at jaw level
            faceContour?.points?.let { points ->
                if (points.size >= 8) {
                    val lowerPoints = points.takeLast(points.size / 3) // Lower third of face
                    val jawWidth = calculateJawWidth(lowerPoints)

                    // Wider jaw might indicate clenching
                    if (jawWidth > 120f) { // Average jaw width threshold
                        jawTension += ((jawWidth - 120f) / 40f) * 0.5f
                    }
                }
            }

            // Method 2: Head tilt indicating jaw tension
            val headZ = abs(face.headEulerAngleZ)
            if (headZ > 8f) {
                jawTension += (headZ / 30f) * 0.3f
            }

            // Method 3: Mouth position relative to face
            val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)
            val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)

            if (mouthLeft != null && mouthRight != null) {
                val mouthWidth = abs(mouthRight.position.x - mouthLeft.position.x)
                // Very narrow mouth might indicate jaw clenching
                if (mouthWidth < 40f) {
                    jawTension += (40f - mouthWidth) / 40f * 0.2f
                }
            }

            maxOf(0f, minOf(1f, jawTension))
        } catch (e: Exception) {
            Log.e("FaceLandmark", "Error calculating jaw tension", e)
            0f
        }
    }

    private fun calculateJawWidth(lowerFacePoints: List<PointF>): Float {
        if (lowerFacePoints.size < 4) return 0f

        // Find leftmost and rightmost points in lower face
        val leftmost = lowerFacePoints.minByOrNull { it.x }
        val rightmost = lowerFacePoints.maxByOrNull { it.x }

        return if (leftmost != null && rightmost != null) {
            abs(rightmost.x - leftmost.x)
        } else 0f
    }

    private fun estimateDarkCircles(face: Face, leftEyeContour: FaceContour?, rightEyeContour: FaceContour?): Float {
        return try {
            var darkCircleScore = 0f

            // Method 1: Enhanced eye bag analysis
            val eyeBagSeverity = estimateEyeBags(face, leftEyeContour, rightEyeContour)
            darkCircleScore += eyeBagSeverity * 0.5f

            // Method 2: Eye openness patterns (tired eyes)
            val leftEyeOpen = face.leftEyeOpenProbability ?: 0.5f
            val rightEyeOpen = face.rightEyeOpenProbability ?: 0.5f
            val avgEyeOpen = (leftEyeOpen + rightEyeOpen) / 2f

            // Very low eye openness suggests fatigue/dark circles
            if (avgEyeOpen < 0.4f) {
                darkCircleScore += (0.4f - avgEyeOpen) * 1.5f
            }

            // Method 3: Eye asymmetry (one eye more tired)
            val eyeAsymmetry = abs(leftEyeOpen - rightEyeOpen)
            if (eyeAsymmetry > 0.2f) {
                darkCircleScore += eyeAsymmetry * 0.8f
            }

            maxOf(0f, minOf(1f, darkCircleScore))
        } catch (e: Exception) {
            Log.e("FaceLandmark", "Error estimating dark circles", e)
            0f
        }
    }

    private fun calculateSkinStressIndicators(face: Face): Float {
        return try {
            var skinStress = 0f

            // Method 1: Overall facial tension patterns
            val leftEyeOpen = face.leftEyeOpenProbability ?: 0.5f
            val rightEyeOpen = face.rightEyeOpenProbability ?: 0.5f
            val avgEyeOpen = (leftEyeOpen + rightEyeOpen) / 2f

            // Squinting patterns indicate skin stress
            if (avgEyeOpen < 0.5f) {
                skinStress += (0.5f - avgEyeOpen) * 0.6f
            }

            // Method 2: Head positioning stress indicators
            val headY = abs(face.headEulerAngleY)
            val headX = abs(face.headEulerAngleX)

            // Extreme head positions indicate stress posture
            if (headY > 15f || headX > 10f) {
                skinStress += ((headY + headX) / 50f) * 0.4f
            }

            maxOf(0f, minOf(1f, skinStress))
        } catch (e: Exception) {
            Log.e("FaceLandmark", "Error calculating skin stress", e)
            0f
        }
    }

    private fun calculateFacialAsymmetry(face: Face): Float {
        return try {
            var asymmetryScore = 0f

            // Method 1: Eye asymmetry
            val leftEyeOpen = face.leftEyeOpenProbability ?: 0.5f
            val rightEyeOpen = face.rightEyeOpenProbability ?: 0.5f
            val eyeAsymmetry = abs(leftEyeOpen - rightEyeOpen)
            asymmetryScore += eyeAsymmetry * 0.4f

            // Method 2: Landmark position asymmetry
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
            val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)

            if (leftEye != null && rightEye != null && noseBase != null) {
                val faceCenter = noseBase.position.x
                val leftDistance = abs(leftEye.position.x - faceCenter)
                val rightDistance = abs(rightEye.position.x - faceCenter)
                val positionAsymmetry = abs(leftDistance - rightDistance) / maxOf(leftDistance, rightDistance)

                asymmetryScore += positionAsymmetry * 0.3f
            }

            // Method 3: Mouth asymmetry
            val leftMouth = face.getLandmark(FaceLandmark.MOUTH_LEFT)
            val rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT)
            val bottomMouth = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)

            if (leftMouth != null && rightMouth != null && bottomMouth != null) {
                val mouthCenter = (leftMouth.position.x + rightMouth.position.x) / 2f
                val mouthCenterDeviation = abs(bottomMouth.position.x - mouthCenter)
                asymmetryScore += (mouthCenterDeviation / 20f) * 0.3f
            }

            maxOf(0f, minOf(1f, asymmetryScore))
        } catch (e: Exception) {
            Log.e("FaceLandmark", "Error calculating facial asymmetry", e)
            0f
        }
    }

    interface LandmarkListener {
        fun onLandmarksDetected(landmarks: FaceLandmarks?)
    }
}