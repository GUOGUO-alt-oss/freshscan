package com.example.freshscan.data.mapper

import com.example.freshscan.data.inference.model.ModelConfig
import com.example.freshscan.di.ModelV1
import com.example.freshscan.domain.model.FreshnessLevel
import com.example.freshscan.domain.model.FruitCategory
import com.example.freshscan.domain.model.Prediction
import com.example.freshscan.domain.model.RecognitionResult
import com.example.freshscan.util.Constants
import com.example.freshscan.util.MathUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps raw TFLite output (FloatArray of 18 logits) to a domain [RecognitionResult].
 *
 * Pipeline:
 * 0. Raw-logit check → if max logit < threshold, force UNKNOWN
 * 1. Softmax → probability distribution
 * 2. Top-3 extraction
 * 3. Label index → FruitCategory + FreshnessLevel mapping
 * 4. Confidence threshold check → UNCERTAIN fallback
 * 5. Assemble [RecognitionResult]
 */
@Singleton
class ModelMapper @Inject constructor(
    @ModelV1 private val modelConfig: ModelConfig
) {
    /**
     * Convert raw TFLite output logits into a domain-level RecognitionResult.
     *
     * @param rawOutput FloatArray of 18 logits from TFLite interpreter.
     * @param inferenceTimeMs Time taken for inference in ms.
     * @param thumbnailPath Optional thumbnail path for saved results.
     * @return RecognitionResult with Softmax probabilities applied.
     */
    fun mapToResult(
        rawOutput: FloatArray,
        inferenceTimeMs: Long,
        thumbnailPath: String? = null
    ): RecognitionResult {
        // Step 0: Raw-logit magnitude check — before Softmax
        // If the model barely activates any class, the input is likely
        // not a fruit (e.g., palm, wall, screen). Skip softmax entirely
        // to avoid false confidence from probability normalization.
        val maxRawLogit = rawOutput.maxOrNull() ?: 0f
        if (maxRawLogit < Constants.MIN_LOGIT_FOR_CONFIDENCE) {
            return RecognitionResult(
                fruitCategory = FruitCategory.UNKNOWN,
                freshnessLevel = FreshnessLevel.UNCERTAIN,
                confidence = 0f,
                topPredictions = emptyList(),
                inferenceTimeMs = inferenceTimeMs,
                thumbnailPath = thumbnailPath
            )
        }

        // Step 1: Softmax
        val probabilities = MathUtils.softmax(rawOutput)

        // Step 2: Top-3 (index-probability pairs, sorted descending)
        val top3 = probabilities
            .mapIndexed { index, prob -> Pair(index, prob) }
            .sortedByDescending { it.second }
            .take(3)

        // Step 3: Build Prediction list
        val topPredictions = top3.map { (index, prob) ->
            Prediction(
                label = modelConfig.labels[index],
                displayName = "${indexToFruitCategory(index).displayName}-${indexToFreshness(index).displayName}",
                confidence = prob
            )
        }

        // Step 4: Primary classification
        val (topIndex, topConfidence) = top3.first()

        // Step 5: Confidence threshold check
        val (fruitCategory, freshnessLevel) = if (topConfidence < Constants.CONFIDENCE_THRESHOLD) {
            FruitCategory.UNKNOWN to FreshnessLevel.UNCERTAIN
        } else {
            indexToFruitCategory(topIndex) to indexToFreshness(topIndex)
        }

        // Step 6: Assemble result
        return RecognitionResult(
            fruitCategory = fruitCategory,
            freshnessLevel = freshnessLevel,
            confidence = topConfidence,
            topPredictions = topPredictions,
            inferenceTimeMs = inferenceTimeMs,
            thumbnailPath = thumbnailPath
        )
    }

    // --- Private helpers ---

    // softmax() moved to MathUtils.kt (DRY — shared with ModelMapperV2)

    /**
     * Map a model output index (0-17) to a FruitCategory.
     * index/2 determines the fruit: 0=apple, 1=banana, 2=bittergourd,
     * 3=capsicum, 4=cucumber, 5=okra, 6=orange, 7=potato, 8=tomato.
     */
    private fun indexToFruitCategory(index: Int): FruitCategory = when (index / 2) {
        0 -> FruitCategory.APPLE
        1 -> FruitCategory.BANANA
        2 -> FruitCategory.BITTER_GOURD
        3 -> FruitCategory.CAPSICUM
        4 -> FruitCategory.CUCUMBER
        5 -> FruitCategory.OKRA
        6 -> FruitCategory.ORANGE
        7 -> FruitCategory.POTATO
        8 -> FruitCategory.TOMATO
        else -> FruitCategory.UNKNOWN
    }

    /**
     * Map a model output index (0-17) to a FreshnessLevel.
     * Even indices = FRESH, odd indices = ROTTEN.
     */
    private fun indexToFreshness(index: Int): FreshnessLevel = when {
        index % 2 == 0 -> FreshnessLevel.FRESH
        else -> FreshnessLevel.ROTTEN
    }
}
