package com.example.freshscan.data.inference

import android.content.Context
import android.graphics.Bitmap
import com.example.freshscan.util.Logger
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EfficientDet-Lite0 detection engine wrapper using MediaPipe Tasks Vision API.
 *
 * Input: [Bitmap] (auto-resized to model input size 320×320 by MediaPipe).
 * Output: [ObjectDetectorResult] containing Detection list with normalized
 *         coordinates [0,1], COCO category indices, and confidence scores.
 *
 * Lifecycle:
 * - Model is loaded lazily on first [detect] call via [ensureLoaded].
 * - Call [close] to release the detector (e.g., when leaving analysis page).
 * - The ~200ms first-load latency is hidden behind the 2s particle animation.
 *
 * **Important:** MediaPipe Tasks Vision bundles its own TFLite runtime internally.
 * Avoid symbol conflicts with the standalone `org.tensorflow:tensorflow-lite` dependency
 * used by [TFLiteClassifier]. Both can coexist as long as they don't share
 * TFLite classes across the MediaPipe ↔ standalone boundary.
 *
 * **Degradation path:** EfficientDet-Lite0 is trained on COCO (80 classes), which
 * only partially covers fruits/vegetables. If [detect] returns zero valid boxes,
 * the pipeline falls back to sliding-window detection (see [DetectionPostprocessor]
 * and the sliding-window logic in AnalysisViewModel).
 */
@Singleton
class EfficientDetEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var objectDetector: ObjectDetector? = null
    private val loadLock = Any()

    // --- Detection configuration ---

    companion object {
        /** Minimum confidence score to include a detection. */
        const val SCORE_THRESHOLD = 0.4f

        /** Maximum number of detection boxes returned. */
        const val MAX_RESULTS = 10

        /** Model filename in assets/model/. */
        const val MODEL_PATH = "efficientdet_lite0.tflite"
    }

    /**
     * Initialize the MediaPipe ObjectDetector (lazy loading).
     *
     * Safe to call multiple times — subsequent calls are no-ops.
     * First call takes ~200ms (masked by the 2s particle animation).
     *
     * @throws IllegalStateException if the model file is missing or corrupt.
     */
    fun ensureLoaded() {
        synchronized(loadLock) {
            if (objectDetector != null) return
            Logger.i("EfficientDet", "Loading model: $MODEL_PATH")
            objectDetector = ObjectDetector.createFromOptions(
                context,
                ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath(MODEL_PATH)
                            .build()
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .setScoreThreshold(SCORE_THRESHOLD)
                    .setMaxResults(MAX_RESULTS)
                    .build()
            )
            Logger.i("EfficientDet", "Model loaded successfully")
        }
    }

    /**
     * Run object detection on a [Bitmap].
     *
     * @param bitmap Input photo (any size; MediaPipe internally resizes to 320×320).
     * @return [ObjectDetectorResult] containing zero or more Detection instances.
     * @throws IllegalStateException if model loading fails.
     */
    fun detect(bitmap: Bitmap): ObjectDetectorResult {
        ensureLoaded()
        val mpImage = BitmapImageBuilder(bitmap).build()
        return objectDetector!!.detect(mpImage)
    }

    /**
     * Release the MediaPipe ObjectDetector resources.
     *
     * Safe to call multiple times. After calling [close], the next [detect]
     * will reload the model.
     */
    fun close() {
        objectDetector?.close()
        objectDetector = null
        Logger.i("EfficientDet", "Detector closed")
    }
}
