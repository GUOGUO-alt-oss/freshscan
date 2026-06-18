package com.example.freshscan.util

import kotlin.math.exp

/**
 * Math utility functions shared across mappers and inference pipelines.
 *
 * Extracted from [com.example.freshscan.data.mapper.ModelMapper] and
 * [com.example.freshscan.data.mapper.ModelMapperV2] to eliminate
 * duplicated Softmax implementations (DRY principle).
 */
object MathUtils {

    /**
     * Numerically stable Softmax: subtracts max logit before exp to avoid overflow.
     *
     * Also protects against NaN when all logits are extremely negative (expSum ≈ 0),
     * falling back to a uniform distribution in that case.
     *
     * @param logits Raw logit values from model output.
     * @return Probability distribution summing to 1.0.
     */
    fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size) { i ->
            exp((logits[i] - maxLogit).toDouble()).toFloat()
        }
        val sum = exps.sum()
        // Protect against NaN when all exps are 0 (extreme negative logits)
        return if (sum == 0f || sum.isNaN()) {
            FloatArray(logits.size) { 1f / logits.size }
        } else {
            FloatArray(logits.size) { i -> exps[i] / sum }
        }
    }
}
