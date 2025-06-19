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
        // Draw face contour landmarks like in the image - yellow dots
        drawFaceContourLandmarks(canvas, landmarks)

        // Draw stress level indicator
        val faceLeft = boundingBox.x1 * width
        val faceTop = boundingBox.y1 * height
        val faceRight = boundingBox.x2 * width
        val faceBottom = boundingBox.y2 * height
        drawStressIndicator(canvas, landmarks, faceLeft, faceTop, faceRight, faceBottom)
    }

    private fun drawFaceContourLandmarks(canvas: Canvas, landmarks: FaceLandmarks) {
        // Draw yellow dots like in the reference image
        val dotPaint = Paint().apply {
            color = Color.YELLOW
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val dotRadius = 6f

        // Draw landmark points as yellow dots
        landmarks.landmarkPoints.forEach { (landmarkName, point) ->
            val x = point.x * width
            val y = point.y * height

            when (landmarkName) {
                "LEFT_EYE", "RIGHT_EYE" -> {
                    // Draw multiple dots around eyes
                    drawEyeContourDots(canvas, x, y, dotPaint, dotRadius)
                }
                "NOSE_BASE" -> {
                    // Draw nose dots
                    canvas.drawCircle(x, y, dotRadius, dotPaint)
                    canvas.drawCircle(x - 10, y - 5, dotRadius, dotPaint)
                    canvas.drawCircle(x + 10, y - 5, dotRadius, dotPaint)
                }
                "MOUTH_LEFT", "MOUTH_RIGHT", "MOUTH_BOTTOM" -> {
                    canvas.drawCircle(x, y, dotRadius, dotPaint)
                }
                "LEFT_EYEBROW", "RIGHT_EYEBROW" -> {
                    // Draw eyebrow dots
                    drawEyebrowContourDots(canvas, x, y, dotPaint, dotRadius)
                }
            }
        }

        // Draw additional face contour dots if we have enough landmark data
        drawFaceOutlineDots(canvas, landmarks, dotPaint, dotRadius)
    }

    private fun drawEyeContourDots(canvas: Canvas, centerX: Float, centerY: Float, paint: Paint, radius: Float) {
        // Draw dots around eye area like in the image
        canvas.drawCircle(centerX, centerY, radius, paint) // center
        canvas.drawCircle(centerX - 15, centerY, radius, paint) // left
        canvas.drawCircle(centerX + 15, centerY, radius, paint) // right
        canvas.drawCircle(centerX - 10, centerY - 8, radius, paint) // top left
        canvas.drawCircle(centerX + 10, centerY - 8, radius, paint) // top right
        canvas.drawCircle(centerX - 10, centerY + 8, radius, paint) // bottom left
        canvas.drawCircle(centerX + 10, centerY + 8, radius, paint) // bottom right
        canvas.drawCircle(centerX, centerY - 10, radius, paint) // top
        canvas.drawCircle(centerX, centerY + 10, radius, paint) // bottom
    }

    private fun drawEyebrowContourDots(canvas: Canvas, centerX: Float, centerY: Float, paint: Paint, radius: Float) {
        // Draw eyebrow dots
        canvas.drawCircle(centerX - 20, centerY, radius, paint)
        canvas.drawCircle(centerX - 10, centerY - 3, radius, paint)
        canvas.drawCircle(centerX, centerY - 5, radius, paint)
        canvas.drawCircle(centerX + 10, centerY - 3, radius, paint)
        canvas.drawCircle(centerX + 20, centerY, radius, paint)
    }

    private fun drawFaceOutlineDots(canvas: Canvas, landmarks: FaceLandmarks, paint: Paint, radius: Float) {
        // Create face outline dots based on available landmarks
        val leftEye = landmarks.landmarkPoints["LEFT_EYE"]
        val rightEye = landmarks.landmarkPoints["RIGHT_EYE"]
        val noseBase = landmarks.landmarkPoints["NOSE_BASE"]
        val mouthLeft = landmarks.landmarkPoints["MOUTH_LEFT"]
        val mouthRight = landmarks.landmarkPoints["MOUTH_RIGHT"]

        if (leftEye != null && rightEye != null && noseBase != null) {
            val faceWidth = (rightEye.x - leftEye.x) * width * 1.8f
            val faceHeight = faceWidth * 1.3f
            val centerX = (leftEye.x + rightEye.x) * width / 2f
            val centerY = (leftEye.y * height + noseBase.y * height) / 2f

            // Draw oval face outline dots
            val numDots = 20
            for (i in 0 until numDots) {
                val angle = (i * 2 * Math.PI / numDots).toFloat()
                val x = centerX + (faceWidth / 2f * Math.cos(angle.toDouble())).toFloat()
                val y = centerY + (faceHeight / 2f * Math.sin(angle.toDouble())).toFloat()
                canvas.drawCircle(x, y, radius, paint)
            }
        }
    }

    private fun drawStressIndicator(canvas: Canvas, landmarks: FaceLandmarks,
                                    faceLeft: Float, faceTop: Float, faceRight: Float, faceBottom: Float) {

        // Calculate overall stress indication
        val stressLevel = calculateVisualStressLevel(landmarks)

        if (stressLevel > 0) {
            val centerX = (faceLeft + faceRight) / 2
            val indicatorY = faceTop - 30f

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