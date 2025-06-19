package com.StressDetection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results = listOf<BoundingBox>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()
    private var landmarkPaint = Paint()
    private var tensionPaint = Paint()
    private var eyePaint = Paint()
    private var eyebrowPaint = Paint()
    private var mouthPaint = Paint()

    private var bounds = Rect()
    private var faceLandmarks: FaceLandmarks? = null

    init {
        initPaints()
    }

    fun clear() {
        results = listOf()
        faceLandmarks = null
        invalidate()
    }

    private fun initPaints() {
        textBackgroundPaint.apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            textSize = 40f
            alpha = 180
        }

        textPaint.apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            textSize = 40f
            isAntiAlias = true
        }

        landmarkPaint.apply {
            color = Color.CYAN
            strokeWidth = 4F
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        eyePaint.apply {
            strokeWidth = 6F
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        eyebrowPaint.apply {
            strokeWidth = 8F
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        mouthPaint.apply {
            strokeWidth = 6F
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        tensionPaint.apply {
            strokeWidth = 4F
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        boxPaint.apply {
            color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
            strokeWidth = 8F
            style = Paint.Style.STROKE
        }
    }

    fun setLandmarks(landmarks: FaceLandmarks?) {
        faceLandmarks = landmarks
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // Draw face detection boxes and landmarks
        results.forEach { boundingBox ->
            drawFaceBox(canvas, boundingBox)
            faceLandmarks?.let { landmarks ->
                drawDetailedLandmarks(canvas, boundingBox, landmarks)
            }
        }
    }

    private fun drawFaceBox(canvas: Canvas, boundingBox: BoundingBox) {
        val left = boundingBox.x1 * width
        val top = boundingBox.y1 * height
        val right = boundingBox.x2 * width
        val bottom = boundingBox.y2 * height

        // Draw main bounding box
        canvas.drawRect(left, top, right, bottom, boxPaint)

        // Draw face detection label
        val drawableText = "Face Detected"
        textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
        val textWidth = bounds.width()
        val textHeight = bounds.height()

        canvas.drawRect(
            left,
            top,
            left + textWidth + BOUNDING_RECT_TEXT_PADDING,
            top + textHeight + BOUNDING_RECT_TEXT_PADDING,
            textBackgroundPaint
        )
        canvas.drawText(drawableText, left, top + bounds.height(), textPaint)
    }

    private fun drawDetailedLandmarks(canvas: Canvas, boundingBox: BoundingBox, landmarks: FaceLandmarks) {
        val faceLeft = boundingBox.x1 * width
        val faceTop = boundingBox.y1 * height
        val faceRight = boundingBox.x2 * width
        val faceBottom = boundingBox.y2 * height

        // Draw actual landmark points
        drawLandmarkPoints(canvas, landmarks)

        // Draw eye openness indicators
        drawEyeIndicators(canvas, landmarks)

        // Draw tension indicators
        drawTensionIndicators(canvas, landmarks, faceLeft, faceTop, faceRight, faceBottom)

        // Draw stress level indicator on face
        drawStressIndicator(canvas, landmarks, faceLeft, faceTop, faceRight, faceBottom)
    }

    private fun drawLandmarkPoints(canvas: Canvas, landmarks: FaceLandmarks) {
        landmarks.landmarkPoints.forEach { (landmarkName, point) ->
            val x = point.x * width
            val y = point.y * height

            when (landmarkName) {
                "LEFT_EYE", "RIGHT_EYE" -> {
                    // Draw eye landmarks with openness indication
                    val eyeOpenness = if (landmarkName == "LEFT_EYE") landmarks.leftEyeOpenness else landmarks.rightEyeOpenness
                    eyePaint.color = when {
                        eyeOpenness > 0.7f -> Color.GREEN
                        eyeOpenness > 0.4f -> Color.YELLOW
                        else -> Color.RED
                    }
                    val radius = 8f + (eyeOpenness * 8f)
                    canvas.drawCircle(x, y, radius, eyePaint)

                    // Draw smaller center point
                    landmarkPaint.color = Color.WHITE
                    canvas.drawCircle(x, y, 3f, landmarkPaint)
                }
                "NOSE_BASE" -> {
                    landmarkPaint.color = Color.BLUE
                    canvas.drawCircle(x, y, 6f, landmarkPaint)
                }
                "MOUTH_LEFT", "MOUTH_RIGHT", "MOUTH_BOTTOM" -> {
                    // Color mouth based on tension
                    val color = when {
                        landmarks.mouthTension > 0.6f -> Color.RED
                        landmarks.mouthTension > 0.3f -> Color.YELLOW
                        else -> Color.GREEN
                    }
                    landmarkPaint.color = color
                    canvas.drawCircle(x, y, 5f, landmarkPaint)
                }
                "LEFT_EYEBROW", "RIGHT_EYEBROW" -> {
                    // Color eyebrows based on tension
                    val color = when {
                        landmarks.eyebrowTension > 0.6f -> Color.RED
                        landmarks.eyebrowTension > 0.3f -> Color.YELLOW
                        else -> Color.GREEN
                    }
                    eyebrowPaint.color = color
                    canvas.drawCircle(x, y, 4f, eyebrowPaint)

                    // Draw tension line
                    if (landmarks.eyebrowTension > 0.3f) {
                        canvas.drawLine(x - 15, y, x + 15, y, eyebrowPaint)
                    }
                }
            }
        }
    }

    private fun drawEyeIndicators(canvas: Canvas, landmarks: FaceLandmarks) {
        // Draw eye bag severity indicators
        if (landmarks.eyeBagSeverity > 0.3f) {
            landmarks.landmarkPoints["LEFT_EYE"]?.let { leftEye ->
                val x = leftEye.x * width
                val y = leftEye.y * height + 20f

                landmarkPaint.color = when {
                    landmarks.eyeBagSeverity > 0.7f -> Color.RED
                    landmarks.eyeBagSeverity > 0.5f -> Color.YELLOW
                    else -> Color.YELLOW
                }
                canvas.drawCircle(x, y, 4f, landmarkPaint)
            }

            landmarks.landmarkPoints["RIGHT_EYE"]?.let { rightEye ->
                val x = rightEye.x * width
                val y = rightEye.y * height + 20f

                landmarkPaint.color = when {
                    landmarks.eyeBagSeverity > 0.7f -> Color.RED
                    landmarks.eyeBagSeverity > 0.5f -> Color.YELLOW
                    else -> Color.YELLOW
                }
                canvas.drawCircle(x, y, 4f, landmarkPaint)
            }
        }
    }

    private fun drawTensionIndicators(canvas: Canvas, landmarks: FaceLandmarks,
                                      faceLeft: Float, faceTop: Float, faceRight: Float, faceBottom: Float) {

        // Draw mouth tension line
        if (landmarks.mouthTension > 0.4f) {
            landmarks.landmarkPoints["MOUTH_LEFT"]?.let { leftMouth ->
                landmarks.landmarkPoints["MOUTH_RIGHT"]?.let { rightMouth ->
                    val leftX = leftMouth.x * width
                    val leftY = leftMouth.y * height
                    val rightX = rightMouth.x * width
                    val rightY = rightMouth.y * height

                    mouthPaint.color = if (landmarks.mouthTension > 0.7f) Color.RED else Color.YELLOW
                    canvas.drawLine(leftX, leftY, rightX, rightY, mouthPaint)
                }
            }
        }

        // Draw overall tension frame
        if (landmarks.overallFacialTension > 0.5f) {
            tensionPaint.color = when {
                landmarks.overallFacialTension > 0.8f -> Color.RED
                landmarks.overallFacialTension > 0.6f -> Color.YELLOW
                else -> Color.YELLOW
            }
            tensionPaint.strokeWidth = 6f + (landmarks.overallFacialTension * 6f)

            val padding = 10f
            canvas.drawRect(
                faceLeft + padding,
                faceTop + padding,
                faceRight - padding,
                faceBottom - padding,
                tensionPaint
            )
        }
    }

    private fun drawStressIndicator(canvas: Canvas, landmarks: FaceLandmarks,
                                    faceLeft: Float, faceTop: Float, faceRight: Float, faceBottom: Float) {

        // Calculate overall stress indication
        val stressLevel = calculateVisualStressLevel(landmarks)

        if (stressLevel > 0) {
            val centerX = (faceLeft + faceRight) / 2
            val indicatorY = faceTop - 20f

            val stressText = when (stressLevel) {
                1 -> "😌"
                2 -> "😐"
                3 -> "😰"
                else -> "📊"
            }

            textPaint.textSize = 60f
            val textWidth = textPaint.measureText(stressText)

            // Background for stress indicator
            textBackgroundPaint.alpha = 200
            canvas.drawRect(
                centerX - textWidth/2 - 10,
                indicatorY - 50f,
                centerX + textWidth/2 + 10,
                indicatorY + 10f,
                textBackgroundPaint
            )

            canvas.drawText(stressText, centerX - textWidth/2, indicatorY - 10f, textPaint)
            textPaint.textSize = 40f // Reset text size
        }
    }

    private fun calculateVisualStressLevel(landmarks: FaceLandmarks): Int {
        val avgEyeOpenness = (landmarks.leftEyeOpenness + landmarks.rightEyeOpenness) / 2f
        val tensionScore = landmarks.eyebrowTension * 0.3f +
                landmarks.mouthTension * 0.3f +
                landmarks.overallFacialTension * 0.4f

        val eyeFatigueScore = (1f - avgEyeOpenness) + landmarks.eyeBagSeverity

        val totalStress = (tensionScore + eyeFatigueScore * 0.5f)

        return when {
            totalStress > 0.7f -> 3
            totalStress > 0.4f -> 2
            totalStress > 0.1f -> 1
            else -> 0
        }
    }

    fun setResults(boundingBoxes: List<BoundingBox>) {
        results = boundingBoxes
        invalidate()
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}