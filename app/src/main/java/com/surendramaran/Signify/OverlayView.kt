package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import java.util.LinkedList
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results = listOf<BoundingBox>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()
    private var debugPaint = Paint()

    private var bounds = Rect()
    private var debugMode = false

    init {
        initPaints()
    }

    fun clear() {
        results = listOf()
        invalidate()
    }

    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
        invalidate()
    }

    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f
        textPaint.typeface = Typeface.DEFAULT_BOLD

        boxPaint.color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE

        debugPaint.color = Color.YELLOW
        debugPaint.style = Paint.Style.STROKE
        debugPaint.strokeWidth = 2F
        debugPaint.textSize = 30f
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        if (results.isEmpty()) {
            // If debug mode is on, draw a grid to help with positioning
            if (debugMode) {
                drawDebugGrid(canvas)
            }
            return
        }

        // Draw bounding boxes and labels
        results.forEach { box ->
            try {
                // Convert normalized coordinates to pixel values
                val left = box.x1 * width
                val top = box.y1 * height
                val right = box.x2 * width
                val bottom = box.y2 * height

                // Draw the bounding box
                canvas.drawRect(left, top, right, bottom, boxPaint)

                // Prepare label text with class name and confidence
                val confidencePercentage = (box.cnf * 100).toInt()
                val drawableText = "${box.clsName} ${confidencePercentage}%"

                // Calculate text dimensions
                textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
                val textWidth = bounds.width()
                val textHeight = bounds.height()

                // Draw background for text
                canvas.drawRect(
                    left,
                    top,
                    left + textWidth + BOUNDING_RECT_TEXT_PADDING,
                    top + textHeight + BOUNDING_RECT_TEXT_PADDING,
                    textBackgroundPaint
                )

                // Draw the label text
                canvas.drawText(drawableText, left, top + bounds.height(), textPaint)

                // If in debug mode, draw additional information
                if (debugMode) {
                    drawDebugInfo(canvas, box, left, top, right, bottom)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing bounding box", e)
            }
        }

        // Draw grid in debug mode
        if (debugMode) {
            drawDebugGrid(canvas)
        }
    }

    private fun drawDebugInfo(canvas: Canvas, box: BoundingBox, left: Float, top: Float, right: Float, bottom: Float) {
        val centerX = (left + right) / 2
        val centerY = (top + bottom) / 2

        // Draw center point
        canvas.drawCircle(centerX, centerY, 5f, debugPaint)

        // Draw normalized coordinates info
        val debugText = "x1=${String.format("%.2f", box.x1)} y1=${String.format("%.2f", box.y1)} " +
                "x2=${String.format("%.2f", box.x2)} y2=${String.format("%.2f", box.y2)}"

        canvas.drawText(
            debugText,
            left,
            bottom + 30f, // Position below the box
            debugPaint
        )
    }

    private fun drawDebugGrid(canvas: Canvas) {
        // Draw horizontal lines
        for (y in 0..10) {
            val yPos = height * y / 10f
            canvas.drawLine(0f, yPos, width.toFloat(), yPos, debugPaint)

            // Add coordinate labels
            canvas.drawText(
                String.format("%.1f", y / 10f),
                10f,
                yPos - 5,
                debugPaint
            )
        }

        // Draw vertical lines
        for (x in 0..10) {
            val xPos = width * x / 10f
            canvas.drawLine(xPos, 0f, xPos, height.toFloat(), debugPaint)

            // Add coordinate labels
            canvas.drawText(
                String.format("%.1f", x / 10f),
                xPos + 5,
                30f,
                debugPaint
            )
        }
    }

    fun setResults(boundingBoxes: List<BoundingBox>) {
        results = boundingBoxes
        invalidate()
    }

    companion object {
        private const val TAG = "OverlayView"
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}