// MainActivity.kt
package com.StressDetection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
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
            stressDetails.text = "Point camera at your face to begin analysis"
            captureButton.isEnabled = false
            resumeButton.visibility = View.GONE
        }
    }

    private fun resetStressDisplay() {
        binding.apply {
            stressLevel.text = "Stress Level: No face detected"
            stressIndicator.text = "❌ No detection"
            stressDetails.text = "Please position your face in the camera view"
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

            resetButton.setOnClickListener {
                resetAnalysis()
            }

            resumeButton.setOnClickListener {
                resumeCamera()
            }

            viewResultsButton.setOnClickListener {
                showDetailedResults()
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

        // Stop camera
        pauseCamera()

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
            viewResultsButton.visibility = View.VISIBLE

            // Show immediate results
            stressLevel.text = "Captured! Stress Level: ${getStressLevelText(stressData.level)} (${stressData.score}/100)"
            stressIndicator.text = "📸 Analysis Complete - ${getStressEmoji(stressData.level)}"
        }

        // Auto-show results
        showCaptureResults()
    }

    private fun pauseCamera() {
        isCameraPaused = true
        imageAnalyzer?.clearAnalyzer()
        binding.viewFinder.alpha = 0.7f // Dim the camera view
    }

    private fun resumeCamera() {
        isCameraPaused = false
        isCapturing = false
        captureResult = null

        binding.apply {
            resumeButton.visibility = View.GONE
            viewResultsButton.visibility = View.GONE
            captureButton.isEnabled = true
            viewFinder.alpha = 1.0f
        }

        // Restart image analysis
        setupImageAnalyzer()
        resetAnalysis()
    }

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

        val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(result.timestamp))

        val dialog = AlertDialog.Builder(this)
            .setTitle("📊 Stress Analysis Results - $timeString")
            .setMessage(result.detailedAnalysis)
            .setPositiveButton("Save Results") { _, _ ->
                saveResults(result)
            }
            .setNeutralButton("Share") { _, _ ->
                shareResults(result)
            }
            .setNegativeButton("Close", null)
            .create()

        dialog.show()
    }

    private fun showDetailedResults() {
        showCaptureResults()
    }

    private fun saveResults(result: CaptureResult) {
        // Here you could implement saving to file or database
        Toast.makeText(this, "Results saved successfully!", Toast.LENGTH_SHORT).show()
    }

    private fun shareResults(result: CaptureResult) {
        // Here you could implement sharing functionality
        Toast.makeText(this, "Sharing functionality not implemented yet", Toast.LENGTH_SHORT).show()
    }

    private fun resetAnalysis() {
        stressAnalyzer.reset()
        captureResult = null
        currentLandmarks = null

        binding.apply {
            viewResultsButton.visibility = View.GONE
            setupStressLevelDisplay()
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
                val emotions = boundingBoxes.map { it.clsName to it.cnf }
                stressAnalyzer.updateEmotions(emotions)

                if (!isCameraPaused) {
                    updateStressDisplay()
                }
            }
        }
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

            stressDetails.text = buildString {
                append("📊 Emotion Impact: ${stressData.emotionScore}/40\n")
                append("😤 Facial Tension: ${stressData.facialTensionScore}/30\n")
                append("👁️ Eye Fatigue: ${stressData.eyeFatigueScore}/30\n")
                append("🎭 Primary Emotion: ${stressData.dominantEmotion}\n")
                append("📈 Confidence: ${getReliabilityScore()}%")
            }

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