package com.example.freshscan.data.inference.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * v2 category recognition model configuration (Fruits-360, 260 classes).
 *
 * Loads label mappings from assets/model/labels_v2.txt.
 * Each line format: index,label,display_name,is_cookable
 * Example: 45,Tomato_Cherry_Red,樱桃番茄,true
 *
 * Training environment: see training/fruits360/ for dataset download and training scripts.
 * Model file: assets/model/fruits360_model.tflite (to be placed during M3).
 */
class ModelConfigV2(
    @ApplicationContext private val context: Context
) {
    /** Label info list, indexed by model output position. */
    val labels: List<LabelInfoV2> by lazy { loadLabelsV2() }

    /** Number of output classes (derived from labels file). */
    val numClasses: Int get() = labels.size

    /** Input tensor dimensions. */
    val inputWidth: Int = 224
    val inputHeight: Int = 224
    val inputChannels: Int = 3

    /** Pixel data type: 0 = float32. */
    val pixelDataType: Int = 0

    /**
     * Normalization mean (subtract before feeding to model).
     * Default 0.0f — input is already normalized to [0, 1].
     */
    val normalizationMean: Float = 0.0f

    /**
     * Normalization std (divide by after mean subtraction).
     * 255.0f normalizes uint8 pixel values to [0, 1].
     */
    val normalizationStd: Float = 255.0f

    /**
     * ByteBuffer capacity: H × W × C × sizeof(float32).
     */
    val byteBufferCapacity: Int = inputHeight * inputWidth * inputChannels * 4

    // --- Private helpers ---

    /**
     * Load label mappings from assets/model/labels_v2.txt.
     *
     * Format: index,label,display_name,is_cookable
     * Lines starting with '#' are comments and are skipped.
     */
    private fun loadLabelsV2(): List<LabelInfoV2> {
        return context.assets
            .open("model/labels_v2.txt")
            .bufferedReader()
            .useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }
                    .mapNotNull { line ->
                        val parts = line.split(",")
                        if (parts.size >= 4) {
                            LabelInfoV2(
                                label = parts[1].trim(),
                                displayName = parts[2].trim(),
                                isCookable = parts[3].trim().toBoolean()
                            )
                        } else null
                    }
                    .toList()
            }
    }
}

/**
 * Per-class label information for Fruits-360 categories.
 */
data class LabelInfoV2(
    /** Raw label from model output, e.g. "Tomato_Cherry_Red". */
    val label: String,

    /** Human-readable display name, e.g. "樱桃番茄". */
    val displayName: String,

    /** Whether this item is cookable (true for vegetables, false for fruits). */
    val isCookable: Boolean
)
