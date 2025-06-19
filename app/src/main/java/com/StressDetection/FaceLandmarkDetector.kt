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

        // Calculate metrics
        val leftEyeOpenness = calculateEyeOpenness(face, true)
        val rightEyeOpenness = calculateEyeOpenness(face, false)
        val eyebrowTension = calculateEyebrowTension(face, leftEyebrowTop, rightEyebrowTop, leftEye, rightEye)
        val eyeBagSeverity = estimateEyeBags(face, leftEyeContour, rightEyeContour)
        val mouthTension = calculateMouthTension(face, leftMouth, rightMouth, bottomMouth)
        val overallTension = calculateOverallTension(face)

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

        return FaceLandmarks(
            leftEyeOpenness = leftEyeOpenness,
            rightEyeOpenness = rightEyeOpenness,
            eyebrowTension = eyebrowTension,
            eyeBagSeverity = eyeBagSeverity,
            mouthTension = mouthTension,
            overallFacialTension = overallTension,
            landmarkPoints = normalizedLandmarks
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
            if (leftEyebrowTop != null && rightEyebrowTop != null && leftEye != null && rightEye != null) {
                // Calculate distance between eyebrows and eyes
                val leftEyebrowPoints = leftEyebrowTop.points
                val rightEyebrowPoints = rightEyebrowTop.points

                if (leftEyebrowPoints.isNotEmpty() && rightEyebrowPoints.isNotEmpty()) {
                    val leftEyebrowCenter = leftEyebrowPoints[leftEyebrowPoints.size / 2]
                    val rightEyebrowCenter = rightEyebrowPoints[rightEyebrowPoints.size / 2]

                    val leftDistance = abs(leftEyebrowCenter.y - leftEye.position.y)
                    val rightDistance = abs(rightEyebrowCenter.y - rightEye.position.y)
                    val avgDistance = (leftDistance + rightDistance) / 2f

                    // Normalize tension (smaller distance = more tension)
                    val normalizedTension = 1f - (avgDistance / 60f) // 60px as reference
                    return maxOf(0f, minOf(1f, normalizedTension))
                }
            }

            // Fallback: use eye openness
            val leftEyeOpen = face.leftEyeOpenProbability ?: 0.5f
            val rightEyeOpen = face.rightEyeOpenProbability ?: 0.5f
            val avgEyeOpen = (leftEyeOpen + rightEyeOpen) / 2f

            maxOf(0f, (0.7f - avgEyeOpen) * 2f)
        } catch (e: Exception) {
            Log.e("FaceLandmark", "Error calculating eyebrow tension", e)
            0f
        }
    }

    private fun estimateEyeBags(face: Face, leftEyeContour: FaceContour?, rightEyeContour: FaceContour?): Float {
        return try {
            if (leftEyeContour != null && rightEyeContour != null) {
                // Analyze eye contour shape for bags
                val leftPoints = leftEyeContour.points
                val rightPoints = rightEyeContour.points

                if (leftPoints.size >= 6 && rightPoints.size >= 6) {
                    // Calculate eye shape irregularity (simplified)
                    val leftHeight = calculateEyeHeight(leftPoints)
                    val rightHeight = calculateEyeHeight(rightPoints)
                    val avgHeight = (leftHeight + rightHeight) / 2f

                    // Lower height ratio might indicate bags/fatigue
                    val heightRatio = avgHeight / 20f // 20px as reference height
                    return maxOf(0f, minOf(1f, 1f - heightRatio))
                }
            }
            0.2f // Default slight value
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

    interface LandmarkListener {
        fun onLandmarksDetected(landmarks: FaceLandmarks?)
    }
}