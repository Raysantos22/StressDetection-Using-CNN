// MainActivity.kt
package com.StressDetection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.StressDetection.Constants.LABELS_PATH
import com.StressDetection.Constants.MODEL_PATH
import com.StressDetection.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), Detector.DetectorListener, FaceLandmarkDetector.LandmarkListener {
    private lateinit var binding: ActivityMainBinding
    private val isFrontCamera = true

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var detector: Detector? = null
    private var faceLandmarkDetector: FaceLandmarkDetector? = null

    private lateinit var cameraExecutor: ExecutorService
    private val stressAnalyzer = StressAnalyzer()

    // Capture state
    private var isCapturing = false
    private var isCameraPaused = false
    private var capturedBitmap: Bitmap? = null
    private var captureResult: CaptureResult? = null
    private var currentLandmarks: FaceLandmarks? = null

    // Capture timer
    private var captureTimer: Handler? = null
    private var captureCountdown = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraExecutor.execute {
            detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
            faceLandmarkDetector = FaceLandmarkDetector(baseContext, this)
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        bindListeners()
        setupStressLevelDisplay()
    }

    override fun onEmptyDetect() {
        runOnUiThread {
            binding.inferenceTime.text = "No face detected"
            binding.overlay.clear()
            if (!isCapturing && !isCameraPaused) {
                resetStressDisplay()
            }
        }
    }

    private fun setupStressLevelDisplay() {
        binding.apply {
            stressLevel.text = "Stress Level: Monitoring..."
            stressIndicator.text = "📊 Position your face in camera"
            captureButton.isEnabled = false
            resumeButton.visibility = View.GONE
        }
    }

    private fun resetStressDisplay() {
        binding.apply {
            stressLevel.text = "Stress Level: No face detected"
            stressIndicator.text = "❌ Position your face in the camera view"
            stressIndicatorBackground.setBackgroundColor(
                ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray)
            )
            captureButton.isEnabled = false
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun bindListeners() {
        binding.apply {
            captureButton.setOnClickListener {
                startCaptureSequence()
            }

            resumeButton.setOnClickListener {
                resumeCamera()
            }
        }
    }

    private fun startCaptureSequence() {
        if (isCapturing || isCameraPaused) return

        isCapturing = true
        captureCountdown = 3

        binding.apply {
            captureButton.isEnabled = false
            captureStatus.visibility = View.VISIBLE
            captureStatus.text = "Get ready... $captureCountdown"
        }

        captureTimer = Handler(Looper.getMainLooper())
        startCountdown()
    }

    private fun startCountdown() {
        if (captureCountdown > 0) {
            binding.captureStatus.text = "Capturing in... $captureCountdown"
            captureCountdown--

            captureTimer?.postDelayed({
                startCountdown()
            }, 1000)
        } else {
            binding.captureStatus.text = "Capturing... Stay still!"

            // Capture for 3 seconds to get stable readings
            captureTimer?.postDelayed({
                finalizeCaptureAnalysis()
            }, 3000)
        }
    }

    private fun finalizeCaptureAnalysis() {
        if (!isCapturing) return

        // COMPLETELY stop camera to save resources
        stopCameraCompletely()

        // Get final analysis
        val stressData = stressAnalyzer.calculateStressLevel()
        val detailedAnalysis = stressAnalyzer.getDetailedAnalysis()

        captureResult = CaptureResult(
            bitmap = capturedBitmap,
            stressAnalysis = stressData,
            detailedAnalysis = detailedAnalysis,
            timestamp = System.currentTimeMillis(),
            landmarks = currentLandmarks
        )

        isCapturing = false

        binding.apply {
            captureStatus.visibility = View.GONE
            captureButton.isEnabled = false
            resumeButton.visibility = View.VISIBLE
            faceGuide.visibility = View.GONE // Hide guide when showing results

            // Show immediate results
            stressLevel.text = "Captured! Stress Level: ${getStressLevelText(stressData.level)} (${stressData.score}/100)"
            stressIndicator.text = "📸 Analysis Complete - ${getStressEmoji(stressData.level)}"
        }

        // Auto-show results with captured image
        showCaptureResults()
    }

    private fun stopCameraCompletely() {
        try {
            // Stop image analysis
            imageAnalyzer?.clearAnalyzer()

            // Unbind all camera use cases to completely stop camera
            cameraProvider?.unbindAll()

            // Clear preview surface
            preview?.setSurfaceProvider(null)

            isCameraPaused = true
            binding.viewFinder.alpha = 0.5f // Dim the camera view

            Log.d(TAG, "Camera stopped completely to save resources")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }

    private fun restartCameraCompletely() {
        try {
            isCameraPaused = false

            // Re-bind camera use cases
            bindCameraUseCases()

            binding.apply {
                viewFinder.alpha = 1.0f
                faceGuide.visibility = View.VISIBLE // Show guide again
            }

            Log.d(TAG, "Camera restarted")
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting camera", e)
        }
    }

    private fun pauseCamera() {
        isCameraPaused = true
        imageAnalyzer?.clearAnalyzer()
        binding.viewFinder.alpha = 0.7f // Dim the camera view
        stopCameraCompletely()

    }

    private fun resumeCamera() {
        // Completely restart camera
        restartCameraCompletely()

        isCapturing = false
        captureResult = null

        binding.apply {
            resumeButton.visibility = View.GONE
            captureButton.isEnabled = true
        }

        setupStressLevelDisplay()
    }

//    private fun pauseCamera() {
//        // This method kept for compatibility but now calls complete stop
//    }

    private fun setupImageAnalyzer() {
        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            if (!isCameraPaused) {
                val bitmapBuffer = Bitmap.createBitmap(
                    imageProxy.width,
                    imageProxy.height,
                    Bitmap.Config.ARGB_8888
                )
                imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

                val matrix = Matrix().apply {
                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                    if (isFrontCamera) {
                        postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
                    }
                }

                val rotatedBitmap = Bitmap.createBitmap(
                    bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                    matrix, true
                )

                // Store captured bitmap for analysis
                if (isCapturing || isCameraPaused) {
                    capturedBitmap = rotatedBitmap.copy(rotatedBitmap.config ?: Bitmap.Config.ARGB_8888, false)
                }

                detector?.detect(rotatedBitmap)
                faceLandmarkDetector?.detectLandmarks(rotatedBitmap)
            }

            imageProxy.close()
        }
    }

    private fun showCaptureResults() {
        val result = captureResult ?: return

        // Create visual annotated image
        val visualAnalyzer = VisualResultAnalyzer()
        val annotatedBitmap = result.bitmap?.let { bitmap ->
            visualAnalyzer.createAnnotatedResult(bitmap, result.stressAnalysis, result.landmarks)
        }

        // Create custom dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_visual_result, null)

        // Setup dialog content
        setupDialogContent(dialogView, result, annotatedBitmap)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .setOnCancelListener {
                autoResetAndResume()
            }
            .create()

        // Setup dialog buttons
        setupDialogButtons(dialogView, result, dialog)

        dialog.show()

        // Make dialog take most of screen
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            (resources.displayMetrics.heightPixels * 0.85).toInt()
        )
    }

    private fun setupDialogContent(
        dialogView: View,
        result: CaptureResult,
        annotatedBitmap: Bitmap?
    ) {
        val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(result.timestamp))

        // Set timestamp
        dialogView.findViewById<TextView>(R.id.dialogTimestamp).text = "Captured at $timeString"

        // Set annotated image
        val annotatedImageView = dialogView.findViewById<ImageView>(R.id.annotatedImage)
        if (annotatedBitmap != null) {
            annotatedImageView.setImageBitmap(annotatedBitmap)
        } else {
            // Fallback to original image
            result.bitmap?.let { annotatedImageView.setImageBitmap(it) }
        }

        // Set stress level container color and content
        val stressContainer = dialogView.findViewById<LinearLayout>(R.id.stressLevelContainer)
        val stressEmoji = dialogView.findViewById<TextView>(R.id.stressEmoji)
        val stressLevelText = dialogView.findViewById<TextView>(R.id.stressLevelText)
        val stressScore = dialogView.findViewById<TextView>(R.id.stressScore)

        val containerColor = when (result.stressAnalysis.level) {
            1 -> ContextCompat.getColor(this, android.R.color.holo_green_dark)
            2 -> ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            3 -> ContextCompat.getColor(this, android.R.color.holo_red_dark)
            else -> ContextCompat.getColor(this, android.R.color.darker_gray)
        }

        stressContainer.setBackgroundColor(containerColor)
        stressEmoji.text = getStressEmoji(result.stressAnalysis.level)
        stressLevelText.text = "LEVEL ${result.stressAnalysis.level}: ${getStressLevelText(result.stressAnalysis.level).uppercase()} STRESS"
        stressScore.text = "Score: ${result.stressAnalysis.score}/100 points"

        // Set breakdown
        val emotionBreakdown = dialogView.findViewById<TextView>(R.id.emotionBreakdown)
        emotionBreakdown.text = buildString {
            append("🎭 Emotions: ${result.stressAnalysis.emotionScore}/60 pts (${result.stressAnalysis.dominantEmotion})\n")
            append("😤 Facial Tension: ${result.stressAnalysis.facialTensionScore}/25 pts\n")
            append("👁️ Eye Strain: ${result.stressAnalysis.eyeFatigueScore}/15 pts")
        }

        // Set confidence
        val confidenceLevel = dialogView.findViewById<TextView>(R.id.confidenceLevel)
        confidenceLevel.text = "📈 Analysis Confidence: ${getReliabilityScore()}%"

        // Set explanation
        val explanationText = dialogView.findViewById<TextView>(R.id.explanationText)
        explanationText.text = getStressExplanation(result.stressAnalysis)
    }

    private fun setupDialogButtons(dialogView: View, result: CaptureResult, dialog: AlertDialog) {
        val saveButton = dialogView.findViewById<Button>(R.id.saveResultButton)
        val takeAnotherButton = dialogView.findViewById<Button>(R.id.takeAnotherButton)

        saveButton.setOnClickListener {
            saveResults(result)
            dialog.dismiss()
            autoResetAndResume()
        }

        takeAnotherButton.setOnClickListener {
            dialog.dismiss()
            autoResetAndResume()
        }
    }

    private fun getStressExplanation(stressData: StressAnalysisResult): String {
        return buildString {
            when (stressData.level) {
                1 -> {
                    append("✅ LOW STRESS (0-30 points) detected because:\n\n")
                    if (stressData.dominantEmotion == "Happy") {
                        append("😊 Happy emotions reduce stress significantly\n")
                    }
                    append("• Facial muscles appear relaxed\n")
                    append("• Eyes show normal openness and alertness\n")
                    append("• Minimal tension in eyebrows and mouth area\n")
                    append("• Stable emotional state detected\n\n")
                    append("Keep doing what you're doing! 🌟")
                }
                2 -> {
                    append("⚠️ MODERATE STRESS (31-70 points) detected because:\n\n")
                    if (stressData.emotionScore > 25) {
                        append("🎭 Stress emotions detected: ${stressData.dominantEmotion}\n")
                    }
                    if (stressData.facialTensionScore > 10) {
                        append("😤 Facial tension observed in multiple areas\n")
                    }
                    if (stressData.eyeFatigueScore > 5) {
                        append("👁️ Eyes showing signs of strain or fatigue\n")
                    }
                    append("• Some muscle tension in face detected\n")
                    append("• Emotional state indicates worry/anxiety\n\n")
                    append("💡 Take a short break and practice deep breathing")
                }
                3 -> {
                    append("🚨 HIGH STRESS (71+ points) detected because:\n\n")
                    if (stressData.emotionScore > 35) {
                        append("😰 Strong stress emotions: ${stressData.dominantEmotion}\n")
                    }
                    if (stressData.facialTensionScore > 15) {
                        append("😬 Significant facial tension across multiple regions\n")
                    }
                    if (stressData.eyeFatigueScore > 8) {
                        append("😵 Eyes showing high fatigue or severe strain\n")
                    }
                    append("• Multiple stress indicators present\n")
                    append("• Facial muscles show high tension\n")
                    append("• Emotional state indicates distress\n\n")
                    append("🚨 IMMEDIATE ACTION: Step away from stressful activities!\n")
                    append("Try 4-7-8 breathing: Inhale 4, hold 7, exhale 8")
                }
                else -> {
                    append("📊 Unable to determine stress level clearly.\n")
                    append("Try capturing again with better lighting and positioning.")
                }
            }
        }
    }

    private fun autoResetAndResume() {
        // Reset everything and resume camera automatically
        resetAnalysis()
        resumeCamera()
    }

    private fun saveResults(result: CaptureResult) {
        // Here you could implement saving to file or database
        Toast.makeText(this, "Result saved!", Toast.LENGTH_SHORT).show()
    }

    private fun resetAnalysis() {
        stressAnalyzer.reset()
        captureResult = null
        currentLandmarks = null

        binding.apply {
            overlay.clear()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        setupImageAnalyzer()

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            binding.inferenceTime.text = "${inferenceTime}ms"
            binding.overlay.apply {
                setResults(boundingBoxes)
                invalidate()
            }

            if (boundingBoxes.isNotEmpty()) {
                val boundingBox = boundingBoxes[0]

                // Update face guide
                updateFaceGuide(boundingBox)

                val emotions = boundingBoxes.map { it.clsName to it.cnf }
                stressAnalyzer.updateEmotions(emotions)

                if (!isCameraPaused) {
                    updateStressDisplay()
                }
            } else {
                // No face detected
                binding.faceGuide.updateFaceStatus(false, false, 0f)
            }
        }
    }

    private fun updateFaceGuide(boundingBox: BoundingBox) {
        val faceRect = RectF(
            boundingBox.x1 * binding.overlay.width,
            boundingBox.y1 * binding.overlay.height,
            boundingBox.x2 * binding.overlay.width,
            boundingBox.y2 * binding.overlay.height
        )

        val faceSize = (faceRect.width() + faceRect.height()) / 2f
        val isFaceInGuide = binding.faceGuide.checkFaceInGuide(faceRect)

        binding.faceGuide.updateFaceStatus(true, isFaceInGuide, faceSize)
    }

    override fun onLandmarksDetected(landmarks: FaceLandmarks?) {
        runOnUiThread {
            currentLandmarks = landmarks

            landmarks?.let {
                stressAnalyzer.updateLandmarks(it)
                binding.overlay.setLandmarks(it)

                if (!isCameraPaused) {
                    updateStressDisplay()
                }
            }
        }
    }

    private fun updateStressDisplay() {
        val stressData = stressAnalyzer.calculateStressLevel()

        binding.apply {
            stressLevel.text = "Stress Level: ${getStressLevelText(stressData.level)} (${stressData.score}/100)"
            stressIndicator.text = "${getStressEmoji(stressData.level)} ${getStressDescription(stressData.level)}"

            val color = getStressColor(stressData.level)
            stressIndicatorBackground.setBackgroundColor(color)

            // Enable capture button only when face is detected and not capturing
            captureButton.isEnabled = !isCapturing && !isCameraPaused && stressData.level > 0
        }
    }

    private fun getStressLevelText(level: Int): String {
        return when (level) {
            1 -> "Low"
            2 -> "Moderate"
            3 -> "High"
            else -> "Unknown"
        }
    }

    private fun getStressEmoji(level: Int): String {
        return when (level) {
            1 -> "😌"
            2 -> "😐"
            3 -> "😰"
            else -> "📊"
        }
    }

    private fun getStressDescription(level: Int): String {
        return when (level) {
            1 -> "Low Stress - You appear calm and relaxed"
            2 -> "Moderate Stress - Some tension detected"
            3 -> "High Stress - Significant stress indicators present"
            else -> "Analyzing..."
        }
    }

    private fun getStressColor(level: Int): Int {
        return when (level) {
            1 -> ContextCompat.getColor(this, android.R.color.holo_green_light)
            2 -> ContextCompat.getColor(this, android.R.color.holo_orange_light)
            3 -> ContextCompat.getColor(this, android.R.color.holo_red_light)
            else -> ContextCompat.getColor(this, android.R.color.darker_gray)
        }
    }

    private fun getReliabilityScore(): Int {
        return stressAnalyzer.getReliabilityScore()
    }

    override fun onDestroy() {
        super.onDestroy()
        captureTimer?.removeCallbacksAndMessages(null)
        cameraExecutor.shutdown()
        detector?.close()
    }

    companion object {
        private const val TAG = "StressDetection"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA
        ).toTypedArray()
    }
}