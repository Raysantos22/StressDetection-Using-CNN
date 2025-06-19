package com.StressDetection

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

class VisualResultAnalyzer {

    private val arrowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint().apply {
        textSize = 32f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(200, 0, 0, 0)
    }

    private val circlePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun createAnnotatedResult(
        originalBitmap: Bitmap,
        stressResult: StressAnalysisResult,
        landmarks: FaceLandmarks?
    ): Bitmap {

        // Create a mutable copy of the bitmap
        val annotatedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotatedBitmap)

        // Draw stress indicators with arrows and explanations
        landmarks?.let { landmarkData ->
            drawStressAnnotations(canvas, stressResult, landmarkData, annotatedBitmap.width, annotatedBitmap.height)
        }

        // Draw overall stress level indicator
        drawOverallStressIndicator(canvas, stressResult, annotatedBitmap.width, annotatedBitmap.height)

        return annotatedBitmap
    }

    private fun drawStressAnnotations(
        canvas: Canvas,
        stressResult: StressAnalysisResult,
        landmarks: FaceLandmarks,
        imageWidth: Int,
        imageHeight: Int
    ) {

        // Draw eye stress indicators
        drawEyeStressAnnotations(canvas, landmarks, stressResult, imageWidth, imageHeight)

        // Draw eyebrow tension indicators
        drawEyebrowTensionAnnotations(canvas, landmarks, stressResult, imageWidth, imageHeight)

        // Draw mouth tension indicators
        drawMouthTensionAnnotations(canvas, landmarks, stressResult, imageWidth, imageHeight)

        // NEW: Draw enhanced facial stress indicators
        drawForeheadWrinkleAnnotations(canvas, landmarks, stressResult, imageWidth, imageHeight)

        drawJawTensionAnnotations(canvas, landmarks, stressResult, imageWidth, imageHeight)

        drawDarkCircleAnnotations(canvas, landmarks, stressResult, imageWidth, imageHeight)

        drawAsymmetryAnnotations(canvas, landmarks, stressResult, imageWidth, imageHeight)

        // Draw emotion indicators
        drawEmotionAnnotations(canvas, stressResult, landmarks, imageWidth, imageHeight)
    }

    private fun drawEyeStressAnnotations(
        canvas: Canvas,
        landmarks: FaceLandmarks,
        stressResult: StressAnalysisResult,
        imageWidth: Int,
        imageHeight: Int
    ) {
        val leftEye = landmarks.landmarkPoints["LEFT_EYE"]
        val rightEye = landmarks.landmarkPoints["RIGHT_EYE"]

        if (leftEye != null && rightEye != null) {
            val leftEyeX = leftEye.x * imageWidth
            val leftEyeY = leftEye.y * imageHeight
            val rightEyeX = rightEye.x * imageWidth
            val rightEyeY = rightEye.y * imageHeight

            // Analyze eye stress levels
            val avgEyeOpenness = (landmarks.leftEyeOpenness + landmarks.rightEyeOpenness) / 2f
            val eyeAsymmetry = kotlin.math.abs(landmarks.leftEyeOpenness - landmarks.rightEyeOpenness)

            // Draw eye openness indicators
            when {
                avgEyeOpenness < 0.3f -> {
                    // Severe squinting
                    drawArrowWithText(
                        canvas, leftEyeX, leftEyeY,
                        leftEyeX - 100f, leftEyeY - 80f,
                        "Severe Squinting\n+4 Stress Points",
                        Color.RED
                    )
                }
                avgEyeOpenness < 0.5f -> {
                    // Moderate squinting
                    drawArrowWithText(
                        canvas, leftEyeX, leftEyeY,
                        leftEyeX - 80f, leftEyeY - 60f,
                        "Eye Strain\n+2 Stress Points",
                        Color.YELLOW
                    )
                }
                avgEyeOpenness > 0.9f -> {
                    // Very wide eyes (surprise/fear)
                    drawArrowWithText(
                        canvas, rightEyeX, rightEyeY,
                        rightEyeX + 80f, rightEyeY - 60f,
                        "Wide Eyes\n+3 Stress Points",
                        Color.YELLOW
                    )
                }
            }

            // Draw eye asymmetry indicator
            if (eyeAsymmetry > 0.2f) {
                val centerX = (leftEyeX + rightEyeX) / 2f
                val centerY = (leftEyeY + rightEyeY) / 2f

                drawArrowWithText(
                    canvas, centerX, centerY,
                    centerX, centerY - 100f,
                    "Eye Asymmetry\n+2 Stress Points",
                    Color.MAGENTA
                )
            }

            // Draw eye bags indicator
            if (landmarks.eyeBagSeverity > 0.4f) {
                drawArrowWithText(
                    canvas, rightEyeX, rightEyeY + 20f,
                    rightEyeX + 60f, rightEyeY + 80f,
                    "Eye Fatigue\n+${(landmarks.eyeBagSeverity * 4).toInt()} Points",
                    Color.BLUE
                )
            }
        }
    }

    private fun drawEyebrowTensionAnnotations(
        canvas: Canvas,
        landmarks: FaceLandmarks,
        stressResult: StressAnalysisResult,
        imageWidth: Int,
        imageHeight: Int
    ) {
        if (landmarks.eyebrowTension > 0.3f) {
            val leftEyebrow = landmarks.landmarkPoints["LEFT_EYEBROW"]
            val rightEyebrow = landmarks.landmarkPoints["RIGHT_EYEBROW"]

            if (leftEyebrow != null || rightEyebrow != null) {
                val eyebrowX = leftEyebrow?.x?.times(imageWidth) ?: (rightEyebrow?.x?.times(imageWidth) ?: 0f)
                val eyebrowY = leftEyebrow?.y?.times(imageHeight) ?: (rightEyebrow?.y?.times(imageHeight) ?: 0f)

                val tensionLevel = when {
                    landmarks.eyebrowTension > 0.7f -> "High Tension"
                    landmarks.eyebrowTension > 0.5f -> "Moderate Tension"
                    else -> "Mild Tension"
                }

                drawArrowWithText(
                    canvas, eyebrowX, eyebrowY,
                    eyebrowX - 120f, eyebrowY - 100f,
                    "Eyebrow $tensionLevel\n+${(landmarks.eyebrowTension * 8).toInt()} Points",
                    Color.RED
                )
            }
        }
    }

    private fun drawMouthTensionAnnotations(
        canvas: Canvas,
        landmarks: FaceLandmarks,
        stressResult: StressAnalysisResult,
        imageWidth: Int,
        imageHeight: Int
    ) {
        if (landmarks.mouthTension > 0.4f) {
            val mouthLeft = landmarks.landmarkPoints["MOUTH_LEFT"]
            val mouthRight = landmarks.landmarkPoints["MOUTH_RIGHT"]

            if (mouthLeft != null && mouthRight != null) {
                val mouthCenterX = (mouthLeft.x + mouthRight.x) * imageWidth / 2f
                val mouthCenterY = (mouthLeft.y + mouthRight.y) * imageHeight / 2f

                val tensionDescription = when {
                    landmarks.mouthTension > 0.7f -> "Tight Lips"
                    else -> "Mouth Tension"
                }

                drawArrowWithText(
                    canvas, mouthCenterX, mouthCenterY,
                    mouthCenterX + 100f, mouthCenterY + 60f,
                    "$tensionDescription\n+${(landmarks.mouthTension * 5).toInt()} Points",
                    Color.YELLOW
                )
            }
        }
    }

    private fun drawEmotionAnnotations(
        canvas: Canvas,
        stressResult: StressAnalysisResult,
        landmarks: FaceLandmarks,
        imageWidth: Int,
        imageHeight: Int
    ) {
        // Draw emotion indicator near the face
        val centerX = imageWidth / 2f
        val topY = imageHeight * 0.15f

        val emotionColor = when (stressResult.dominantEmotion) {
            "Happy" -> Color.GREEN
            "Sad", "Worried" -> Color.BLUE
            "Angry", "Fear" -> Color.RED
            "Anxious", "Overwhelmed" -> Color.YELLOW
            else -> Color.GRAY
        }

        val emotionPoints = when (stressResult.dominantEmotion) {
            "Happy" -> "-15"
            "Normal" -> "0"
            "Sad" -> "+25"
            "Anxious" -> "+40"
            "Angry" -> "+50"
            "Fear" -> "+45"
            "Worried" -> "+35"
            else -> "+0"
        }

        drawArrowWithText(
            canvas, centerX, topY,
            centerX - 150f, topY - 80f,
            "${stressResult.dominantEmotion}\n$emotionPoints Points",
            emotionColor
        )
    }

    // NEW: Enhanced facial stress indicator annotations
    private fun drawForeheadWrinkleAnnotations(
        canvas: Canvas,
        landmarks: FaceLandmarks,
        stressResult: StressAnalysisResult,
        imageWidth: Int,
        imageHeight: Int
    ) {
        if (landmarks.foreheadWrinkles > 0.3f) {
            val leftEyebrow = landmarks.landmarkPoints["LEFT_EYEBROW"]
            val rightEyebrow = landmarks.landmarkPoints["RIGHT_EYEBROW"]

            if (leftEyebrow != null && rightEyebrow != null) {
                val foreheadX = (leftEyebrow.x + rightEyebrow.x) * imageWidth / 2f
                val foreheadY = leftEyebrow.y * imageHeight - 30f

                val wrinkleLevel = when {
                    landmarks.foreheadWrinkles > 0.7f -> "Deep Wrinkles"
                    landmarks.foreheadWrinkles > 0.5f -> "Moderate Wrinkles"
                    else -> "Mild Forehead Tension"
                }

                drawArrowWithText(
                    canvas, foreheadX, foreheadY,
                    foreheadX - 100f, foreheadY - 80f,
                    "$wrinkleLevel\n+${(landmarks.foreheadWrinkles * 6).toInt()} Points",
                    Color.MAGENTA
                )
            }
        }
    }

    private fun drawJawTensionAnnotations(
        canvas: Canvas,
        landmarks: FaceLandmarks,
        stressResult: StressAnalysisResult,
        imageWidth: Int,
        imageHeight: Int
    ) {
        if (landmarks.jawTension > 0.4f) {
            val leftCheek = landmarks.landmarkPoints["LEFT_CHEEK"]
            val rightCheek = landmarks.landmarkPoints["RIGHT_CHEEK"]

            if (leftCheek != null && rightCheek != null) {
                val jawX = (leftCheek.x + rightCheek.x) * imageWidth / 2f
                val jawY = (leftCheek.y + rightCheek.y) * imageHeight / 2f + 40f

                val tensionLevel = when {
                    landmarks.jawTension > 0.7f -> "Severe Jaw Clenching"
                    landmarks.jawTension > 0.5f -> "Moderate Jaw Tension"
                    else -> "Mild Jaw Stress"
                }

                drawArrowWithText(
                    canvas, jawX, jawY,
                    jawX + 120f, jawY + 60f,
                    "$tensionLevel\n+${(landmarks.jawTension * 5).toInt()} Points",
                    Color.CYAN
                )
            }
        }
    }

    private fun drawDarkCircleAnnotations(
        canvas: Canvas,
        landmarks: FaceLandmarks,
        stressResult: StressAnalysisResult,
        imageWidth: Int,
        imageHeight: Int
    ) {
        if (landmarks.darkCircles > 0.4f) {
            val leftEye = landmarks.landmarkPoints["LEFT_EYE"]
            val rightEye = landmarks.landmarkPoints["RIGHT_EYE"]

            if (leftEye != null && rightEye != null) {
                val eyeX = rightEye.x * imageWidth
                val eyeY = rightEye.y * imageHeight + 25f

                val circleLevel = when {
                    landmarks.darkCircles > 0.7f -> "Severe Dark Circles"
                    landmarks.darkCircles > 0.5f -> "Moderate Dark Circles"
                    else -> "Mild Eye Fatigue"
                }

                drawArrowWithText(
                    canvas, eyeX, eyeY,
                    eyeX + 100f, eyeY + 70f,
                    "$circleLevel\n+${(landmarks.darkCircles * 4).toInt()} Points",
                    Color.rgb(128, 0, 128) // Purple
                )
            }
        }
    }

    private fun drawAsymmetryAnnotations(
        canvas: Canvas,
        landmarks: FaceLandmarks,
        stressResult: StressAnalysisResult,
        imageWidth: Int,
        imageHeight: Int
    ) {
        if (landmarks.facialAsymmetry > 0.3f) {
            val noseBase = landmarks.landmarkPoints["NOSE_BASE"]

            if (noseBase != null) {
                val noseX = noseBase.x * imageWidth
                val noseY = noseBase.y * imageHeight

                val asymmetryLevel = when {
                    landmarks.facialAsymmetry > 0.6f -> "High Asymmetry"
                    landmarks.facialAsymmetry > 0.4f -> "Moderate Asymmetry"
                    else -> "Mild Asymmetry"
                }

                drawArrowWithText(
                    canvas, noseX, noseY,
                    noseX - 120f, noseY + 80f,
                    "Facial $asymmetryLevel\n+${(landmarks.facialAsymmetry * 3).toInt()} Points",
                    Color.rgb(255, 165, 0) // Orange
                )
            }
        }
    }

    private fun drawOverallStressIndicator(
        canvas: Canvas,
        stressResult: StressAnalysisResult,
        imageWidth: Int,
        imageHeight: Int
    ) {
        // Draw stress level indicator at the bottom
        val centerX = imageWidth / 2f
        val bottomY = imageHeight - 100f

        val stressColor = when (stressResult.level) {
            1 -> Color.GREEN
            2 -> Color.YELLOW
            3 -> Color.RED
            else -> Color.GRAY
        }

        val stressText = when (stressResult.level) {
            1 -> "😌 LOW STRESS"
            2 -> "😐 MODERATE STRESS"
            3 -> "😰 HIGH STRESS"
            else -> "📊 ANALYZING"
        }

        // Draw background circle
        circlePaint.color = Color.argb(180, 0, 0, 0)
        canvas.drawCircle(centerX, bottomY, 80f, circlePaint)

        // Draw stress level text
        textPaint.color = stressColor
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 28f

        canvas.drawText(stressText, centerX, bottomY - 10f, textPaint)
        canvas.drawText("${stressResult.score}/100", centerX, bottomY + 20f, textPaint)
    }

    private fun drawArrowWithText(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        text: String,
        color: Int
    ) {
        // Set colors
        arrowPaint.color = color
        textPaint.color = color

        // Draw arrow line
        canvas.drawLine(startX, startY, endX, endY, arrowPaint)

        // Calculate arrow head
        val arrowLength = 20f
        val arrowAngle = Math.PI / 6
        val lineAngle = kotlin.math.atan2((endY - startY).toDouble(), (endX - startX).toDouble())

        // Draw arrow head
        val arrowX1 = endX - arrowLength * kotlin.math.cos(lineAngle - arrowAngle).toFloat()
        val arrowY1 = endY - arrowLength * kotlin.math.sin(lineAngle - arrowAngle).toFloat()
        val arrowX2 = endX - arrowLength * kotlin.math.cos(lineAngle + arrowAngle).toFloat()
        val arrowY2 = endY - arrowLength * kotlin.math.sin(lineAngle + arrowAngle).toFloat()

        canvas.drawLine(endX, endY, arrowX1, arrowY1, arrowPaint)
        canvas.drawLine(endX, endY, arrowX2, arrowY2, arrowPaint)

        // Draw text background
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 24f
        val lines = text.split("\n")
        val lineHeight = 30f
        val textWidth = lines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
        val textHeight = lines.size * lineHeight

        // Draw background rectangle
        canvas.drawRoundRect(
            endX - 10f,
            endY - textHeight - 10f,
            endX + textWidth + 10f,
            endY + 10f,
            10f, 10f,
            backgroundPaint
        )

        // Draw text lines
        textPaint.color = Color.WHITE
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, endX, endY - textHeight + (index + 1) * lineHeight, textPaint)
        }
    }
}