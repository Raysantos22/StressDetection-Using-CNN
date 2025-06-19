package com.StressDetection

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val detectorListener: DetectorListener,
) {

    private var interpreter: Interpreter
    private var labels = mutableListOf<String>()

    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0

    private lateinit var imageProcessor: ImageProcessor

    init {
        val compatList = CompatibilityList()

        val options = Interpreter.Options().apply{
            if(compatList.isDelegateSupportedOnThisDevice){
                val delegateOptions = compatList.bestOptionsForThisDevice
                this.addDelegate(GpuDelegate(delegateOptions))
            } else {
                this.setNumThreads(4)
            }
            // Enable logging to debug model loading issues
            this.setUseNNAPI(false)  // Sometimes disabling NNAPI can fix issues
        }

        try {
            val model = FileUtil.loadMappedFile(context, modelPath)
            interpreter = Interpreter(model, options)

            // Log model info for debugging
            Log.d(TAG, "Model loaded: $modelPath")

            val inputShape = interpreter.getInputTensor(0).shape()
            val outputShape = interpreter.getOutputTensor(0).shape()

            Log.d(TAG, "Input shape: ${inputShape.contentToString()}")
            Log.d(TAG, "Output shape: ${outputShape.contentToString()}")

            // Correctly extract dimensions based on input shape
            // YOLOv8 usually expects [1, 3, height, width] or [1, height, width, 3]
            if (inputShape.size == 4) {
                if (inputShape[1] == 3) {
                    // NCHW format: [batch, channels, height, width]
                    tensorHeight = inputShape[2]
                    tensorWidth = inputShape[3]
                } else {
                    // NHWC format: [batch, height, width, channels]
                    tensorHeight = inputShape[1]
                    tensorWidth = inputShape[2]
                }
            }

            Log.d(TAG, "Tensor dimensions: $tensorWidth x $tensorHeight")

            if (outputShape.size >= 3) {
                numChannel = outputShape[1]
                numElements = outputShape[2]
                Log.d(TAG, "Output dimensions: channels=$numChannel, elements=$numElements")
            }

            // Initialize image processor with proper normalization values
            // YOLOv8 typically expects pixels normalized to [0,1]
            imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(tensorHeight, tensorWidth, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0f, 255f))  // Normalize to [0,1]
                .add(CastOp(INPUT_IMAGE_TYPE))
                .build()

            // Load labels
            loadLabels()
            Log.d(TAG, "Labels loaded: ${labels.size} labels")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing detector", e)
            throw e
        }
    }

    private fun loadLabels() {
        try {
            val inputStream: InputStream = context.assets.open(labelPath)
            val reader = BufferedReader(InputStreamReader(inputStream))
            labels.clear()

            var line: String? = reader.readLine()
            while (line != null) {
                if (line.trim().isNotEmpty()) {
                    labels.add(line.trim())
                    Log.d(TAG, "Label added: '$line'")
                }
                line = reader.readLine()
            }

            reader.close()
            inputStream.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error loading labels", e)
        }
    }

    fun restart(isGpu: Boolean) {
        interpreter.close()

        val options = if (isGpu) {
            val compatList = CompatibilityList()
            Interpreter.Options().apply{
                if(compatList.isDelegateSupportedOnThisDevice){
                    val delegateOptions = compatList.bestOptionsForThisDevice
                    this.addDelegate(GpuDelegate(delegateOptions))
                } else {
                    this.setNumThreads(4)
                }
            }
        } else {
            Interpreter.Options().apply{
                this.setNumThreads(4)
            }
        }

        val model = FileUtil.loadMappedFile(context, modelPath)
        interpreter = Interpreter(model, options)
    }

    fun close() {
        interpreter.close()
    }

    fun detect(frame: Bitmap) {
        if (tensorWidth <= 0 || tensorHeight <= 0 || numChannel <= 0 || numElements <= 0) {
            Log.e(TAG, "Invalid tensor dimensions: $tensorWidth x $tensorHeight, channels: $numChannel, elements: $numElements")
            detectorListener.onEmptyDetect()
            return
        }

        try {
            val inferenceTime = SystemClock.uptimeMillis()

            // Process image
            val tensorImage = TensorImage(INPUT_IMAGE_TYPE)
            tensorImage.load(frame)
            val processedImage = imageProcessor.process(tensorImage)

            // Run inference
            val output = TensorBuffer.createFixedSize(intArrayOf(1, numChannel, numElements), OUTPUT_IMAGE_TYPE)

            // Debug image processing
            Log.d(TAG, "Running inference with buffer size: ${processedImage.buffer.capacity()}")

            try {
                interpreter.run(processedImage.buffer, output.buffer)
            } catch (e: Exception) {
                Log.e(TAG, "Inference error", e)
                detectorListener.onEmptyDetect()
                return
            }

            // Process results
            val bestBoxes = bestBox(output.floatArray)
            val totalTime = SystemClock.uptimeMillis() - inferenceTime

            // Log results
            if (bestBoxes.isNullOrEmpty()) {
                Log.d(TAG, "No detections found")
                detectorListener.onEmptyDetect()
            } else {
                Log.d(TAG, "Found ${bestBoxes.size} detections")
                for (box in bestBoxes) {
                    Log.d(TAG, "Detection: ${box.clsName} (${box.cnf})")
                }
                detectorListener.onDetect(bestBoxes, totalTime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during detection", e)
            detectorListener.onEmptyDetect()
        }
    }

    private fun bestBox(array: FloatArray) : List<BoundingBox>? {
        val boundingBoxes = mutableListOf<BoundingBox>()

        try {
            // Debug raw output
            Log.d(TAG, "Output array length: ${array.size}")
            Log.d(TAG, "Labels size: ${labels.size}")

            if (labels.isEmpty()) {
                Log.e(TAG, "No labels loaded!")
                return null
            }

            for (c in 0 until numElements) {
                var maxConf = CONFIDENCE_THRESHOLD
                var maxIdx = -1

                // Find the class with highest confidence
                for (j in 4 until numChannel) {
                    val arrayIdx = c + numElements * j
                    if (arrayIdx < array.size && array[arrayIdx] > maxConf) {
                        maxConf = array[arrayIdx]
                        maxIdx = j - 4
                    }
                }

                // Check valid detection
                if (maxConf > CONFIDENCE_THRESHOLD && maxIdx >= 0 && maxIdx < labels.size) {
                    val clsName = labels[maxIdx]

                    // Get box coordinates
                    val cx = array[c] // center x
                    val cy = array[c + numElements] // center y
                    val w = array[c + numElements * 2] // width
                    val h = array[c + numElements * 3] // height

                    // Convert to corner format
                    val x1 = cx - (w/2F)
                    val y1 = cy - (h/2F)
                    val x2 = cx + (w/2F)
                    val y2 = cy + (h/2F)

                    // Skip invalid boxes
                    if (x1 < 0F || x1 > 1F || y1 < 0F || y1 > 1F ||
                        x2 < 0F || x2 > 1F || y2 < 0F || y2 > 1F) {
                        continue
                    }

                    boundingBoxes.add(
                        BoundingBox(
                            x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                            cx = cx, cy = cy, w = w, h = h,
                            cnf = maxConf, cls = maxIdx, clsName = clsName
                        )
                    )

                    Log.d(TAG, "Added box: $clsName ($maxConf)")
                }
            }

            if (boundingBoxes.isEmpty()) {
                Log.d(TAG, "No valid boxes found")
                return null
            }

            return applyNMS(boundingBoxes)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing detection results", e)
            return null
        }
    }

    private fun applyNMS(boxes: List<BoundingBox>) : MutableList<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while(sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.first()
            selectedBoxes.add(first)
            sortedBoxes.remove(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val nextBox = iterator.next()
                val iou = calculateIoU(first, nextBox)
                if (iou >= IOU_THRESHOLD) {
                    iterator.remove()
                }
            }
        }

        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }

    companion object {
        private const val TAG = "YOLODetector"
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.3F
        private const val IOU_THRESHOLD = 0.5F
    }
}