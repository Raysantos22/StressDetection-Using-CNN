package com.StressDetection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class FaceGuideOverlay(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var guidePaint = Paint()
    private var textPaint = Paint()
    private var backgroundPaint = Paint()
    private var cornerPaint = Paint()

    private var isFaceDetected = false
    private var isFaceInGuide = false
    private var faceSize = 0f

    // Guide dimensions (percentage of screen)
    private val guideWidthRatio = 0.6f
    private val guideHeightRatio = 0.7f

    init {
        initPaints()
    }

    private fun initPaints() {
        guidePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
            pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        }

        cornerPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }

        textPaint.apply {
            textSize = 32f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        backgroundPaint.apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }

    fun updateFaceStatus(detected: Boolean, inGuide: Boolean, detectedFaceSize: Float = 0f) {
        isFaceDetected = detected
        isFaceInGuide = inGuide
        faceSize = detectedFaceSize
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Calculate guide dimensions
        val guideWidth = width * guideWidthRatio
        val guideHeight = height * guideHeightRatio
        val guideLeft = (width - guideWidth) / 2f
        val guideTop = (height - guideHeight) / 2f
        val guideRight = guideLeft + guideWidth
        val guideBottom = guideTop + guideHeight

        // Draw face guide
        drawFaceGuide(canvas, guideLeft, guideTop, guideRight, guideBottom)

        // Draw status indicators
        drawStatusIndicators(canvas, guideLeft, guideTop, guideRight, guideBottom)

        // Draw instruction text
        drawInstructionText(canvas, guideLeft, guideTop, guideRight, guideBottom)
    }

    private fun drawFaceGuide(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        // Set guide color based on face detection status
        val guideColor = when {
            isFaceInGuide -> Color.GREEN
            isFaceDetected -> Color.YELLOW
            else -> Color.WHITE
        }

        guidePaint.color = guideColor
        cornerPaint.color = guideColor

        // Draw main oval guide
        val ovalRect = RectF(left, top, right, bottom)
        canvas.drawOval(ovalRect, guidePaint)

        // Draw corner markers for better visibility
        val cornerLength = 40f

        // Top-left corner
        canvas.drawLine(left, top + cornerLength, left, top, cornerPaint)
        canvas.drawLine(left, top, left + cornerLength, top, cornerPaint)

        // Top-right corner
        canvas.drawLine(right - cornerLength, top, right, top, cornerPaint)
        canvas.drawLine(right, top, right, top + cornerLength, cornerPaint)

        // Bottom-left corner
        canvas.drawLine(left, bottom - cornerLength, left, bottom, cornerPaint)
        canvas.drawLine(left, bottom, left + cornerLength, bottom, cornerPaint)

        // Bottom-right corner
        canvas.drawLine(right - cornerLength, bottom, right, bottom, cornerPaint)
        canvas.drawLine(right, bottom, right, bottom - cornerLength, cornerPaint)

        // Draw eye guide lines
        val eyeY = top + (bottom - top) * 0.35f
        val leftEyeX = left + (right - left) * 0.3f
        val rightEyeX = left + (right - left) * 0.7f

        guidePaint.strokeWidth = 3f
        canvas.drawCircle(leftEyeX, eyeY, 15f, guidePaint)
        canvas.drawCircle(rightEyeX, eyeY, 15f, guidePaint)
        guidePaint.strokeWidth = 6f

        // Draw mouth guide line
        val mouthY = top + (bottom - top) * 0.75f
        canvas.drawLine(leftEyeX, mouthY, rightEyeX, mouthY, guidePaint)
    }

    private fun drawStatusIndicators(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        // Draw face size indicator
        if (isFaceDetected) {
            val sizeIndicatorY = bottom + 40f
            val sizeBarWidth = 200f
            val sizeBarHeight = 20f
            val sizeBarLeft = (width - sizeBarWidth) / 2f
            val sizeBarRight = sizeBarLeft + sizeBarWidth

            // Background bar
            backgroundPaint.color = Color.argb(100, 255, 255, 255)
            canvas.drawRoundRect(
                sizeBarLeft, sizeIndicatorY,
                sizeBarRight, sizeIndicatorY + sizeBarHeight,
                10f, 10f, backgroundPaint
            )

            // Size indicator
            val normalizedSize = kotlin.math.max(0f, kotlin.math.min(1f, faceSize / 300f)) // Normalize face size
            val sizeIndicatorWidth = sizeBarWidth * normalizedSize

            val sizeColor = when {
                normalizedSize in 0.4f..0.8f -> Color.GREEN  // Good size
                normalizedSize in 0.2f..0.9f -> Color.YELLOW // Acceptable
                else -> Color.RED // Too small or too large
            }

            backgroundPaint.color = sizeColor
            canvas.drawRoundRect(
                sizeBarLeft, sizeIndicatorY,
                sizeBarLeft + sizeIndicatorWidth, sizeIndicatorY + sizeBarHeight,
                10f, 10f, backgroundPaint
            )

            // Size text
            textPaint.color = Color.WHITE
            textPaint.textSize = 24f
            canvas.drawText(
                "Face Size: ${(normalizedSize * 100).toInt()}%",
                width / 2f, sizeIndicatorY + 50f, textPaint
            )
        }
    }

    private fun drawInstructionText(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val instructionY = top - 60f

        textPaint.textSize = 28f

        val instructionText = when {
            isFaceInGuide -> {
                textPaint.color = Color.GREEN
                "✅ Perfect! Ready to capture"
            }
            isFaceDetected -> {
                textPaint.color = Color.YELLOW
                "📏 Adjust position to fit guide"
            }
            else -> {
                textPaint.color = Color.WHITE
                "👤 Position your face in the guide"
            }
        }

        // Draw text background
        val textWidth = textPaint.measureText(instructionText)
        backgroundPaint.color = Color.argb(180, 0, 0, 0)
        canvas.drawRoundRect(
            (width - textWidth) / 2f - 20f, instructionY - 35f,
            (width + textWidth) / 2f + 20f, instructionY + 10f,
            15f, 15f, backgroundPaint
        )

        canvas.drawText(instructionText, width / 2f, instructionY, textPaint)

        // Draw additional tips at bottom
        if (!isFaceInGuide) {
            val tipsY = bottom + 100f
            textPaint.textSize = 20f
            textPaint.color = Color.GRAY

            val tips = when {
                !isFaceDetected -> "💡 Ensure good lighting and face the camera"
                faceSize < 120f -> "📏 Move closer to the camera"
                faceSize > 400f -> "📏 Move away from the camera"
                else -> "🎯 Center your face in the oval guide"
            }

            canvas.drawText(tips, width / 2f, tipsY, textPaint)
        }
    }

    // Check if detected face is within guide bounds
    fun checkFaceInGuide(faceRect: RectF): Boolean {
        val guideWidth = width * guideWidthRatio
        val guideHeight = height * guideHeightRatio
        val guideLeft = (width - guideWidth) / 2f
        val guideTop = (height - guideHeight) / 2f
        val guideRight = guideLeft + guideWidth
        val guideBottom = guideTop + guideHeight

        val guideRect = RectF(guideLeft, guideTop, guideRight, guideBottom)

        // Check if face is reasonably within guide (80% overlap)
        val intersection = RectF()
        intersection.setIntersect(faceRect, guideRect)

        val faceArea = faceRect.width() * faceRect.height()
        val intersectionArea = intersection.width() * intersection.height()

        return if (faceArea > 0) {
            intersectionArea / faceArea > 0.8f
        } else false
    }
}