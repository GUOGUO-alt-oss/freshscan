package com.example.freshscan.data.mapper

import com.example.freshscan.data.inference.model.ModelConfigV2
import com.example.freshscan.di.ModelV2
import com.example.freshscan.util.Constants
import com.example.freshscan.util.MathUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps Fruits-360 MobileNetV3-260 raw logits to human-readable label information.
 *
 * Key differences from v1 [ModelMapper]:
 * - Output is [LabelResult] (label + displayName + isCookable) instead of
 *   FruitCategory + FreshnessLevel. Freshness is determined independently
 *   by the v1 18-class freshness model.
 * - Top-5 instead of Top-3 (260 classes need broader candidate set for
 *   downstream recipe matching).
 * - No freshness index mapping (freshness is handled by [ModelMapper]).
 *
 * Pipeline:
 * 0. Raw-logit check → if max logit < [Constants.MIN_LOGIT_FOR_CONFIDENCE], return Unknown
 * 1. Numerically stable Softmax
 * 2. Top-1 index extraction
 * 3. Confidence threshold check → [Constants.CONFIDENCE_THRESHOLD]
 * 4. Label lookup in [ModelConfigV2.labels]
 * 5. Assemble [LabelResult.Known] or [LabelResult.Unknown]
 */
@Singleton
class ModelMapperV2 @Inject constructor(
    @ModelV2 private val config: ModelConfigV2
) {
    /**
     * Map raw TFLite output logits to a single [LabelResult].
     *
     * Only returns [LabelResult.Known] if both the raw-logit check and the
     * confidence threshold are satisfied.
     *
     * @param rawOutput FloatArray of [config.numClasses] raw logits.
     * @return [LabelResult.Known] with label info, or [LabelResult.Unknown] if
     *         thresholds are not met.
     */
    fun mapToLabelInfo(rawOutput: FloatArray): LabelResult {
        // Step 0: Raw-logit magnitude check — prevents false confidence from
        // non-produce inputs (hand, wall, table surface) that happen to weakly
        // activate some class.
        val maxRawLogit = rawOutput.maxOrNull() ?: 0f
        if (maxRawLogit < Constants.MIN_LOGIT_FOR_CONFIDENCE) {
            return LabelResult.Unknown
        }

        // Step 1: Numerically stable softmax
        val probs = MathUtils.softmax(rawOutput)

        // Step 2: Top-1
        val (topIndex, topConfidence) = probs
            .mapIndexed { i, p -> i to p }
            .maxByOrNull { it.second } ?: (0 to 0f)

        // Step 3: Confidence threshold
        if (topConfidence < Constants.CONFIDENCE_THRESHOLD) {
            return LabelResult.Unknown
        }

        // Step 4: Label lookup
        val labelInfo = config.labels.getOrNull(topIndex)
            ?: return LabelResult.Unknown

        // Step 5: Assemble
        return LabelResult.Known(
            label = labelInfo.label,
            displayName = labelInfo.displayName,
            confidence = topConfidence,
            isCookable = labelInfo.isCookable
        )
    }

    /**
     * Map raw logits to Top-5 predictions (for debugging and candidate inspection).
     *
     * Useful for:
     * - Debugging model output distribution
     * - Recipe ingredient matching (broader candidate set)
     * - On-screen display of alternative predictions
     *
     * @param rawOutput FloatArray of [config.numClasses] raw logits.
     * @return Up to 5 [LabelResult.Known] entries sorted by confidence descending.
     */
    fun mapToTop5(rawOutput: FloatArray): List<LabelResult.Known> {
        val probs = MathUtils.softmax(rawOutput)
        return probs
            .mapIndexed { i, p -> i to p }
            .sortedByDescending { it.second }
            .take(5)
            .mapNotNull { (i, p) ->
                config.labels.getOrNull(i)?.let {
                    LabelResult.Known(it.label, it.displayName, p, it.isCookable)
                }
            }
    }

    // softmax() moved to MathUtils.kt (DRY — shared with ModelMapper)
}

/**
 * Sealed interface for label mapping results.
 *
 * Unlike v1's [RecognitionResult] which couples category + freshness + confidence,
 * v2 separates category identification (this) from freshness (v1 [ModelMapper]).
 */
sealed interface LabelResult {
    /** Model did not produce a confident result — treat as unrecognized. */
    data object Unknown : LabelResult

    /**
     * Confident prediction.
     *
     * @property label Raw Fruits-360 label, e.g. "Tomato_Cherry_Red".
     * @property displayName Human-readable Chinese display name, e.g. "樱桃番茄".
     * @property confidence Softmax probability [0.0, 1.0].
     * @property isCookable Whether this item is cookable (true = vegetable, false = fruit).
     */
    data class Known(
        val label: String,
        val displayName: String,
        val confidence: Float,
        val isCookable: Boolean
    ) : LabelResult
}
