package com.example.freshscan.data.mapper

import com.example.freshscan.data.inference.model.ModelConfig
import com.example.freshscan.domain.model.FreshnessLevel
import com.example.freshscan.domain.model.FruitCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelMapperTest {

    private lateinit var modelMapper: ModelMapper

    @Before
    fun setUp() {
        modelMapper = ModelMapper(ModelConfig())
    }

    // --- Softmax tests ---

    @Test
    fun `softmax should produce probabilities that sum to 1`() {
        val logits = FloatArray(18) { (2.0f - it * 0.1f).coerceAtLeast(0.01f) }
        val probs = invokeSoftmax(logits)
        assertEquals(1.0f, probs.sum(), 0.001f)
    }

    @Test
    fun `softmax should give highest probability to largest logit`() {
        val logits = FloatArray(18) { 0.1f }
        logits[3] = 5.0f // banana=rotten
        val probs = invokeSoftmax(logits)
        val maxIndex = probs.indices.maxByOrNull { probs[it] }!!
        assertEquals(3, maxIndex)
    }

    @Test
    fun `softmax should handle all identical logits`() {
        val logits = FloatArray(18) { 1.0f }
        val probs = invokeSoftmax(logits)
        probs.forEach { prob ->
            assertEquals(1.0f / 18.0f, prob, 0.001f)
        }
    }

    @Test
    fun `softmax should be numerically stable with large logits`() {
        val logits = FloatArray(18) { 0f }
        logits[0] = 1000f
        val probs = invokeSoftmax(logits)
        assertTrue(probs[0] > 0.99f)
        assertTrue(probs[0] <= 1.0f)
        probs.forEach { prob ->
            assertTrue("Probability should not be NaN", !prob.isNaN())
            assertTrue("Probability should not be Inf", !prob.isInfinite())
        }
        assertEquals(1.0f, probs.sum(), 0.001f)
    }

    @Test
    fun `softmax should handle negative logits`() {
        val logits = FloatArray(18) { -1.0f - it * 0.5f }
        val probs = invokeSoftmax(logits)
        assertTrue(probs[0] > probs[1])
        assertEquals(1.0f, probs.sum(), 0.001f)
    }

    @Test
    fun `softmax should handle extreme negative logits with uniform spread`() {
        val logits = FloatArray(18) { -1000f - it.toFloat() }
        val probs = invokeSoftmax(logits)
        probs.forEach { prob ->
            assertTrue("Probability should not be NaN", !prob.isNaN())
            assertTrue("Probability should not be Inf", !prob.isInfinite())
        }
        assertEquals(1.0f, probs.sum(), 0.001f)
        assertTrue("First should have highest probability", probs[0] > probs[1])
    }

    // --- mapToResult tests ---

    @Test
    fun `mapToResult should classify fresh apple correctly`() {
        val logits = FloatArray(18) { 0.05f }
        logits[0] = 10f
        val result = modelMapper.mapToResult(logits, inferenceTimeMs = 25L)
        assertEquals(FruitCategory.APPLE, result.fruitCategory)
        assertEquals(FreshnessLevel.FRESH, result.freshnessLevel)
        assertTrue(result.confidence > 0.9f)
    }

    @Test
    fun `mapToResult should classify rotten banana correctly`() {
        val logits = FloatArray(18) { 0.05f }
        logits[3] = 10f
        val result = modelMapper.mapToResult(logits, inferenceTimeMs = 30L)
        assertEquals(FruitCategory.BANANA, result.fruitCategory)
        assertEquals(FreshnessLevel.ROTTEN, result.freshnessLevel)
    }

    @Test
    fun `mapToResult should classify fresh orange correctly`() {
        val logits = FloatArray(18) { 0.05f }
        logits[12] = 10f
        val result = modelMapper.mapToResult(logits, inferenceTimeMs = 18L)
        assertEquals(FruitCategory.ORANGE, result.fruitCategory)
        assertEquals(FreshnessLevel.FRESH, result.freshnessLevel)
    }

    @Test
    fun `mapToResult should classify fresh tomato correctly`() {
        val logits = FloatArray(18) { 0.05f }
        logits[16] = 10f
        val result = modelMapper.mapToResult(logits, inferenceTimeMs = 10L)
        assertEquals(FruitCategory.TOMATO, result.fruitCategory)
        assertEquals(FreshnessLevel.FRESH, result.freshnessLevel)
    }

    @Test
    fun `mapToResult should return UNCERTAIN when top confidence is below threshold`() {
        val logits = FloatArray(18) { 0.1f + it * 0.01f }
        val result = modelMapper.mapToResult(logits, inferenceTimeMs = 10L)
        assertEquals(FruitCategory.UNKNOWN, result.fruitCategory)
        assertEquals(FreshnessLevel.UNCERTAIN, result.freshnessLevel)
    }

    @Test
    fun `mapToResult should output Top-3 predictions sorted by confidence`() {
        val logits = FloatArray(18) { 0.05f }
        logits[0] = 8f; logits[2] = 3f; logits[4] = 2f
        val result = modelMapper.mapToResult(logits, inferenceTimeMs = 12L)
        assertEquals(3, result.topPredictions.size)
        val confidences = result.topPredictions.map { it.confidence }
        for (i in 0 until confidences.size - 1) {
            assertTrue("Top-3 sorted descending", confidences[i] >= confidences[i + 1])
        }
    }

    @Test
    fun `mapToResult should handle all 18 indices correctly`() {
        // Test a sampling of key indices
        val testCases = listOf(
            Triple(0, FruitCategory.APPLE, FreshnessLevel.FRESH),
            Triple(1, FruitCategory.APPLE, FreshnessLevel.ROTTEN),
            Triple(4, FruitCategory.BITTER_GOURD, FreshnessLevel.FRESH),
            Triple(6, FruitCategory.CAPSICUM, FreshnessLevel.FRESH),
            Triple(10, FruitCategory.OKRA, FreshnessLevel.FRESH),
            Triple(12, FruitCategory.ORANGE, FreshnessLevel.FRESH),
            Triple(14, FruitCategory.POTATO, FreshnessLevel.FRESH),
            Triple(16, FruitCategory.TOMATO, FreshnessLevel.FRESH),
            Triple(17, FruitCategory.TOMATO, FreshnessLevel.ROTTEN),
        )
        testCases.forEach { (idx, fruit, fresh) ->
            val logits = FloatArray(18) { 0.05f }
            logits[idx] = 10f
            val result = modelMapper.mapToResult(logits, inferenceTimeMs = 5L)
            assertEquals("Idx $idx fruit", fruit, result.fruitCategory)
            assertEquals("Idx $idx freshness", fresh, result.freshnessLevel)
        }
    }

    // --- Raw-logit rejection tests ---

    @Test
    fun `should reject non-fruit with weak logits`() {
        val logits = FloatArray(18) { 0.1f }
        logits[3] = 1.5f // below MIN_LOGIT_FOR_CONFIDENCE (2.0)
        val result = modelMapper.mapToResult(logits, inferenceTimeMs = 10L)
        assertEquals(FruitCategory.UNKNOWN, result.fruitCategory)
        assertEquals(FreshnessLevel.UNCERTAIN, result.freshnessLevel)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun `should accept fruit with strong logits`() {
        val logits = FloatArray(18) { 0.05f }
        logits[2] = 12.0f
        val result = modelMapper.mapToResult(logits, inferenceTimeMs = 10L)
        assertEquals(FruitCategory.BANANA, result.fruitCategory)
        assertTrue(result.confidence > 0.5f)
    }

    @Test
    fun `should reject when all logits are near zero`() {
        val logits = FloatArray(18) { 0.01f }
        val result = modelMapper.mapToResult(logits, inferenceTimeMs = 10L)
        assertEquals(FruitCategory.UNKNOWN, result.fruitCategory)
    }

    @Test
    fun `should accept borderline case above threshold`() {
        val logits = FloatArray(18) { 0.05f }
        logits[8] = 3.0f // above MIN_LOGIT_FOR_CONFIDENCE=2.0
        val result = modelMapper.mapToResult(logits, inferenceTimeMs = 10L)
        assertNotEquals(FruitCategory.UNKNOWN, result.fruitCategory)
    }

    // --- Reflection helper to test private softmax (now in MathUtils.kt) ---

    private fun invokeSoftmax(logits: FloatArray): FloatArray {
        return com.example.freshscan.util.MathUtils.softmax(logits)
    }
}
