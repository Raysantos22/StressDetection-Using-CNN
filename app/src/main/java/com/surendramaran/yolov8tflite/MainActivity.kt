package com.surendramaran.yolov8tflite

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.surendramaran.yolov8tflite.Constants.LABELS_PATH
import com.surendramaran.yolov8tflite.Constants.MODEL_PATH
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), Detector.DetectorListener, TextToSpeech.OnInitListener {
    private lateinit var binding: ActivityMainBinding
    private var isFrontCamera = false

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var detector: Detector? = null

    private lateinit var cameraExecutor: ExecutorService

    // For sign language capture mode
    private val capturedLetters = StringBuilder()
    private var lastDetectedSign: String? = null
    private var isProcessingCapture = false
    private var isScanning = false

    // Text-to-speech
    private lateinit var textToSpeech: TextToSpeech

    // List of emotions to detect - IMPORTANT: this must match your actual emotions
    private val emotions = listOf("Masaya", "Galit", "Malungkot", "neutral")

    // Current emotion
    private var currentEmotion: String? = null

    // For debugging
    private var debugMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Debug log to see which emotions we're checking for
        if (debugMode) {
            Log.d(TAG, "Emotions to detect: $emotions")
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraExecutor.execute {
            detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
        }

        if (allPermissionsGranted()) {
            setupCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Initialize Text-to-Speech
        textToSpeech = TextToSpeech(this, this)

        setupSignLanguageUI()
    }

    private fun setupSignLanguageUI() {
        binding.apply {
            // Start Scanning button
            startScanningButton.setOnClickListener {
                if (!isScanning) {
                    // Start scanning
                    isScanning = true
                    scanningStatusText.text = "Scanning... (Show sign)"
                    startScanningButton.text = "Stop"

                    // Start the camera analysis
                    startCamera()

                    // Enable the capture button when scanning starts
                    captureButton.isEnabled = true
                    captureButton.alpha = 1.0f
                } else {
                    // Stop scanning
                    isScanning = false
                    scanningStatusText.text = "Scanning stopped"
                    startScanningButton.text = "Start"

                    // Stop the camera analysis
                    stopCameraAnalysis()

                    // Disable the capture button when scanning stops
                    captureButton.isEnabled = false
                    captureButton.alpha = 0.5f
                }
            }

            // Set initial state
            captureButton.isEnabled = false
            captureButton.alpha = 0.5f
            scanningStatusText.text = "Press 'Start' to begin"

            // Flip camera button
            flipCameraButton.setOnClickListener {
                isFrontCamera = !isFrontCamera
                if (isScanning) {
                    // If currently scanning, restart the camera with the new lens
                    startCamera()
                } else {
                    // Just setup the camera preview
                    setupCamera()
                }
                showCaptureConfirmation(if (isFrontCamera) "Front camera active" else "Back camera active")
            }

            // Speak button
            speakButton.setOnClickListener {
                if (capturedLetters.isNotEmpty() || currentEmotion != null) {
                    speakWithEmotion()
                } else {
                    showCaptureError("Nothing to speak")
                }
            }
            spaceButton.setOnClickListener {
                // Only add space if there's some text and the last character isn't already a space
                if (capturedLetters.isNotEmpty() && capturedLetters[capturedLetters.length - 1] != ' ') {
                    capturedLetters.append(" ")

                    // Update UI
                    currentWordText.text = "Word: ${capturedLetters}"

                    // Visual feedback
                    showCaptureConfirmation("Space added")
                } else {
                    showCaptureError("Cannot add space")
                }
            }
            // Clear Word button
            clearWordButton.setOnClickListener {
                // Clear only the word
                capturedLetters.clear()

                // Update UI
                currentWordText.text = "Word: "

                // Reset the hand icon
                handSignIcon.setImageResource(android.R.drawable.ic_menu_help)

                showCaptureConfirmation("Word cleared")
            }

            // Clear Emotion button
            clearEmotionButton.setOnClickListener {
                // Clear only the emotion
                currentEmotion = null

                // Update UI
                currentEmotionText.text = "Emotion: None"

                // Reset the emotion icon
                faceEmotionIcon.setImageResource(android.R.drawable.ic_menu_myplaces)

                showCaptureConfirmation("Emotion cleared")
            }

            // Single capture button
            captureButton.setOnClickListener {
                if (lastDetectedSign != null && !lastDetectedSign!!.isEmpty()) {
                    captureCurrent()
                } else {
                    showCaptureError("No sign detected")
                }
            }

            // Initialize icons
            handSignIcon.setImageResource(android.R.drawable.ic_menu_help)
            faceEmotionIcon.setImageResource(android.R.drawable.ic_menu_myplaces)
        }
    }

    // Check if a sign is an emotion
    private fun isEmotion(sign: String): Boolean {
        // First, trim whitespace which could be affecting comparison
        val trimmedSign = sign.trim()

        // Check against each emotion with exact match
        for (emotion in emotions) {
            if (trimmedSign.equals(emotion, ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    // This method automatically routes the detected sign to the appropriate place
    private fun captureCurrent() {
        if (isProcessingCapture || lastDetectedSign == null) return

        isProcessingCapture = true

        val sign = lastDetectedSign!!.trim()

        // Check if it's an emotion
        val emotionDetected = isEmotion(sign)

        // Route accordingly
        if (emotionDetected) {
            // It's an emotion - add it to the emotion section
            captureEmotion(sign)
        } else {
            // It's a regular sign - add it to the word
            captureCurrentLetter(sign)
        }

        // Re-enable capture after a delay
        binding.captureButton.postDelayed({
            isProcessingCapture = false
        }, 1000)
    }

    private fun showCaptureError(message: String) {
        binding.captureConfirmationText.text = message
        binding.captureConfirmationText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
        binding.captureConfirmationText.visibility = View.VISIBLE

        // Hide the message after a delay
        binding.captureConfirmationText.postDelayed({
            binding.captureConfirmationText.visibility = View.INVISIBLE
        }, 1500) // 1.5 seconds
    }

    private fun captureEmotion(emotion: String) {
        currentEmotion = emotion

        // Update the UI to show the current emotion
        binding.currentEmotionText.text = "Emotion: $emotion"

        // Update the emotion icon
        updateEmotionIcon(emotion)

        // Visual feedback for successful capture
        showCaptureConfirmation("Emotion: $emotion")

        // Note: No automatic speaking here as requested
    }

    private fun updateEmotionIcon(emotion: String) {
        // Set appropriate emotion icon based on the detected emotion
        val resourceId = when(emotion.lowercase()) {
            "masaya" -> R.drawable.ic_emotion_happy  // Happy face
            "galit" -> R.drawable.ic_emotion_angry   // Angry face
            "malungkot" -> R.drawable.ic_emotion_sad // Sad face
            "neutral" -> R.drawable.ic_emotion_neutral // Neutral face
            else -> android.R.drawable.ic_menu_myplaces  // Default icon
        }
        binding.faceEmotionIcon.setImageResource(resourceId)
    }

    private fun captureCurrentLetter(sign: String) {
        // One final check to ensure it's not an emotion
        if (isEmotion(sign)) {
            captureEmotion(sign)
            return
        }

        // Add detected sign to the word without automatic spaces
        capturedLetters.append(sign.trim())

        // Update the UI to show the current word
        binding.currentWordText.text = "Word: ${capturedLetters}"

        // Update the hand sign icon
        updateHandSignIcon(sign)

        // Visual feedback for successful capture
        showCaptureConfirmation("Sign: $sign")
    }
    private fun updateHandSignIcon(sign: String) {
        // Using our custom hand sign icon
        binding.handSignIcon.setImageResource(R.drawable.baseline_sign_language_24)
    }

    private fun speakWithEmotion() {
        if (capturedLetters.isEmpty() && currentEmotion == null) {
            showCaptureError("Nothing to speak")
            return
        }

        var textToSpeak = ""

        if (capturedLetters.isNotEmpty()) {
            // Process the word to be spoken properly
            textToSpeak = capturedLetters.toString().trim()

            // Words are now separated by spaces through the space button
            // So we can speak the text as is
        } else if (currentEmotion != null) {
            // If there are no words to speak, just say the emotion name
            textToSpeak = currentEmotion ?: ""
        }

        // If there's an emotion, adjust the speech parameters
        if (currentEmotion != null) {
            speakTextWithEmotion(textToSpeak, currentEmotion!!)
        } else {
            // No emotion, just speak normally
            speakText(textToSpeak)
        }
    }
    // A helper method to check if a string is a single letter or character
    private fun isSingleLetter(str: String): Boolean {
        val trimmed = str.trim()
        return trimmed.length == 1 && trimmed[0].isLetterOrDigit()
    }
    private fun speakTextWithEmotion(text: String, emotion: String) {
        when (emotion.trim().lowercase()) {
            "masaya" -> {
                // Happy - higher pitch, faster rate
                textToSpeech.setPitch(1.3f)  // Higher pitch
                textToSpeech.setSpeechRate(1.2f)   // Faster rate
            }
            "galit" -> {
                // Angry - lower pitch, faster rate
                textToSpeech.setPitch(0.8f)  // Lower pitch
                textToSpeech.setSpeechRate(1.3f)   // Faster rate
            }
            "malungkot" -> {
                // Sad - lower pitch, slower rate
                textToSpeech.setPitch(0.8f)  // Lower pitch
                textToSpeech.setSpeechRate(0.8f)   // Slower rate
            }
            "neutral" -> {
                // Neutral - normal settings
                textToSpeech.setPitch(1.0f)
                textToSpeech.setSpeechRate(1.0f)
            }
            else -> {
                // Default - normal settings
                textToSpeech.setPitch(1.0f)
                textToSpeech.setSpeechRate(1.0f)
            }
        }

        // Speak the text with the modified parameters
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_with_emotion")

        // Reset to default values after speaking
        textToSpeech.setPitch(1.0f)
        textToSpeech.setSpeechRate(1.0f)
    }

    private fun speakText(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts1")
    }

    private fun showCaptureConfirmation(message: String) {
        // Show a brief confirmation
        binding.captureConfirmationText.text = "Captured: $message"
        binding.captureConfirmationText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        binding.captureConfirmationText.visibility = View.VISIBLE

        // Disable capture button during confirmation
        binding.captureButton.isEnabled = false
        binding.captureButton.alpha = 0.5f

        // Hide the confirmation after a delay
        binding.captureConfirmationText.postDelayed({
            binding.captureConfirmationText.visibility = View.INVISIBLE
            if (isScanning) {
                binding.captureButton.isEnabled = true
                binding.captureButton.alpha = 1.0f
            }
        }, 1500) // 1.5 seconds
    }

    private fun setupCamera() {
        // This only sets up the camera but doesn't start analysis
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Setup the preview with the selected camera
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(if (isFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK)
                .build()

            preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()

            cameraProvider?.bindToLifecycle(
                this,
                cameraSelector,
                preview
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)

        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCamera() {
        // This starts the active scanning/analysis
        bindCameraUseCases()
    }

    private fun stopCameraAnalysis() {
        imageAnalyzer = null
        // Unbind all but keep the preview
        cameraProvider?.unbindAll()

        // Rebind just the preview
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(if (isFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()

        cameraProvider?.bindToLifecycle(
            this,
            cameraSelector,
            preview
        )

        preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)

        // Clear any detected signs
        binding.overlay.clear()
        lastDetectedSign = null
        binding.detectedSignText.text = "No sign detected"
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(if (isFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK)
            .build()

        preview =  Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            // Only analyze if scanning is enabled
            if (isScanning) {
                val bitmapBuffer =
                    Bitmap.createBitmap(
                        imageProxy.width,
                        imageProxy.height,
                        Bitmap.Config.ARGB_8888
                    )
                imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
                imageProxy.close()

                val matrix = Matrix().apply {
                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

                    if (isFrontCamera) {
                        postScale(
                            -1f,
                            1f,
                            imageProxy.width.toFloat(),
                            imageProxy.height.toFloat()
                        )
                    }
                }

                val rotatedBitmap = Bitmap.createBitmap(
                    bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                    matrix, true
                )

                detector?.detect(rotatedBitmap)
            } else {
                imageProxy.close()
            }
        }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch(exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) { setupCamera() }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
        cameraExecutor.shutdown()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()){
            setupCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    // TextToSpeech.OnInitListener implementation
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Text-to-Speech language not supported", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Text-to-Speech initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "SignLanguage"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf (
            Manifest.permission.CAMERA
        ).toTypedArray()
    }

    override fun onEmptyDetect() {
        runOnUiThread {
            if (isScanning) {
                binding.overlay.clear()
                lastDetectedSign = null

                // Update UI to show no sign detected
                binding.detectedSignText.text = "No sign detected"
            }
        }
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            if (isScanning) {
                binding.inferenceTime.text = "${inferenceTime}ms"
                binding.overlay.apply {
                    setResults(boundingBoxes)
                    invalidate()
                }

                // Get the most confident detection
                if (boundingBoxes.isNotEmpty()) {
                    val topDetection = boundingBoxes.maxByOrNull { it.cnf }

                    // Only update if confidence is high enough to avoid flickering
                    if (topDetection != null && topDetection.cnf >= 0.5) {  // Higher confidence threshold
                        val sign = topDetection.clsName.trim()
                        lastDetectedSign = sign

                        // Check if it's an emotion
                        val emotionDetected = isEmotion(sign)

                        // Update UI to show detected sign
                        binding.detectedSignText.text = "Detected sign: $sign"

                        // Highlight if it's an emotion
                        if (emotionDetected) {
                            binding.detectedSignText.setTextColor(
                                ContextCompat.getColor(baseContext, R.color.emotion_highlight)
                            )
                        } else {
                            binding.detectedSignText.setTextColor(
                                ContextCompat.getColor(baseContext, android.R.color.white)
                            )
                        }
                    }
                }
            }
        }
    }
}