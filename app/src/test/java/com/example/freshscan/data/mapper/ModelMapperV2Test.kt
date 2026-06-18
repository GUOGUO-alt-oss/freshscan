package com.example.freshscan.data.mapper

import com.example.freshscan.data.inference.model.LabelInfoV2
import com.example.freshscan.data.inference.model.ModelConfigV2
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelMapperV2Test {

    private lateinit var modelMapperV2: ModelMapperV2
    private lateinit var config: ModelConfigV2

    @Before
    fun setUp() {
        config = mockk(relaxed = true)
        modelMapperV2 = ModelMapperV2(config)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Softmax Tests (via reflection on private method)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `softmax should produce probabilities that sum to 1`() {
        val logits = FloatArray(260) { (5.0f - it * 0.02f).coerceAtLeast(0.01f) }
        val probs = invokeSoftmax(logits)
        assertEquals(1.0f, probs.sum(), 0.001f)
    }

    @Test
    fun `softmax should give highest probability to largest logit`() {
        val logits = FloatArray(260) { 0.1f }
        logits[42] = 10.0f
        val probs = invokeSoftmax(logits)
        val maxIndex = probs.indices.maxByOrNull { probs[it] }!!
        assertEquals(42, maxIndex)
    }

    @Test
    fun `softmax should handle all identical logits`() {
        val logits = FloatArray(260) { 1.0f }
        val probs = invokeSoftmax(logits)
        probs.forEach { prob ->
            assertEquals(1.0f / 260.0f, prob, 0.0001f)
        }
    }

    @Test
    fun `softmax should be numerically stable with large logits`() {
        val logits = FloatArray(260) { 0f }
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
        val logits = FloatArray(260) { -1.0f - it * 0.1f }
        val probs = invokeSoftmax(logits)
        assertTrue(probs[0] > probs[1])
        assertEquals(1.0f, probs.sum(), 0.001f)
    }

    @Test
    fun `softmax should handle extreme negative logits without NaN`() {
        val logits = FloatArray(260) { -1000f - it.toFloat() }
        val probs = invokeSoftmax(logits)
        probs.forEach { prob ->
            assertTrue("Probability should not be NaN", !prob.isNaN())
            assertTrue("Probability should not be Inf", !prob.isInfinite())
        }
        assertEquals(1.0f, probs.sum(), 0.001f)
        assertTrue("First should have highest probability", probs[0] > probs[1])
    }

    @Test
    fun `softmax should handle all-zero logits`() {
        val logits = FloatArray(260) { 0f }
        val probs = invokeSoftmax(logits)
        assertEquals(1.0f, probs.sum(), 0.001f)
        probs.forEach { prob ->
            assertEquals(1.0f / 260.0f, prob, 0.0001f)
        }
    }

    @Test
    fun `softmax should produce monotonically increasing probabilities for increasing logits`() {
        val logits = FloatArray(260) { it.toFloat() * 0.1f }
        val probs = invokeSoftmax(logits)
        // Higher logit → higher probability (monotonic)
        for (i in 0 until probs.size - 1) {
            assertTrue("prob[$i] <= prob[${i + 1}]", probs[i] <= probs[i + 1])
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // mapToLabelInfo Tests — Known results
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `mapToLabelInfo should return Known for strong single-class logit`() {
        // Mock labels: only index 0 matters
        every { config.labels } returns listOf(
            LabelInfoV2("Apple_Red", "红苹果", isCookable = false)
        )
        every { config.numClasses } returns 1

        val logits = FloatArray(1) { 10.0f }
        val result = modelMapperV2.mapToLabelInfo(logits)

        assertTrue("Should be Known result", result is LabelResult.Known)
        val known = result as LabelResult.Known
        assertEquals("Apple_Red", known.label)
        assertEquals("红苹果", known.displayName)
        assertTrue(known.confidence > 0.9f)
        assertEquals(false, known.isCookable)
    }

    @Test
    fun `mapToLabelInfo should return Known for cookable vegetable`() {
        every { config.labels } returns listOf(
            LabelInfoV2("Tomato_Cherry_Red", "樱桃番茄", isCookable = true)
        )
        every { config.numClasses } returns 1

        val logits = FloatArray(1) { 10.0f }
        val result = modelMapperV2.mapToLabelInfo(logits)

        assertTrue("Should be Known result", result is LabelResult.Known)
        val known = result as LabelResult.Known
        assertEquals("Tomato_Cherry_Red", known.label)
        assertEquals(true, known.isCookable)
    }

    @Test
    fun `mapToLabelInfo should correctly identify the strongest class among 260`() {
        // Provide 260 labels; put a strong signal at index 100
        val labels = List(260) { i ->
            LabelInfoV2("Class_$i", "类别$i", isCookable = i % 2 == 0)
        }
        every { config.labels } returns labels
        every { config.numClasses } returns 260

        val logits = FloatArray(260) { 0.05f }
        logits[100] = 10.0f  // Strong signal for class 100

        val result = modelMapperV2.mapToLabelInfo(logits)

        assertTrue("Should be Known", result is LabelResult.Known)
        val known = result as LabelResult.Known
        assertEquals("Class_100", known.label)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // mapToLabelInfo Tests — Unknown results
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `mapToLabelInfo should return Unknown for all-weak logits`() {
        every { config.labels } returns listOf(
            LabelInfoV2("Apple_Red", "红苹果", false)
        )
        every { config.numClasses } returns 1

        // max logit = 1.5, below MIN_LOGIT_FOR_CONFIDENCE (2.0)
        val logits = FloatArray(1) { 1.5f }
        val result = modelMapperV2.mapToLabelInfo(logits)

        assertTrue("Should be Unknown", result is LabelResult.Unknown)
    }

    @Test
    fun `mapToLabelInfo should return Unknown for near-zero logits`() {
        every { config.labels } returns listOf(
            LabelInfoV2("Apple_Red", "红苹果", false)
        )
        every { config.numClasses } returns 1

        val logits = FloatArray(1) { 0.01f }
        val result = modelMapperV2.mapToLabelInfo(logits)

        assertTrue("Should be Unknown for near-zero logits", result is LabelResult.Unknown)
    }

    @Test
    fun `mapToLabelInfo should return Unknown when labels list is empty`() {
        every { config.labels } returns emptyList()
        every { config.numClasses } returns 0

        val logits = FloatArray(1) { 10.0f }  // Strong signal, but no labels
        val result = modelMapperV2.mapToLabelInfo(logits)

        assertTrue("Should be Unknown when labels empty", result is LabelResult.Unknown)
    }

    @Test
    fun `mapToLabelInfo should return Unknown when topIndex out of bounds`() {
        every { config.labels } returns listOf(
            LabelInfoV2("Only_Class", "唯一", false)
        )
        every { config.numClasses } returns 1

        // 2 logits but only 1 label — topIndex could be 1 which is out of bounds
        val logits = FloatArray(2) { 0.05f }
        logits[1] = 10.0f  // Index 1 is out of bounds for a 1-element label list

        val result = modelMapperV2.mapToLabelInfo(logits)

        assertTrue("Should be Unknown for out-of-bounds index", result is LabelResult.Unknown)
    }

    @Test
    fun `mapToLabelInfo should pass raw-logit check but fail confidence threshold`() {
        // With 260 uniform logits at 3.0 (above MIN_LOGIT 2.0), softmax gives
        // ~1/260 ≈ 0.0038 which is below CONFIDENCE_THRESHOLD (0.5)
        every { config.labels } returns List(260) { i ->
            LabelInfoV2("Class_$i", "类别$i", false)
        }
        every { config.numClasses } returns 260

        val logits = FloatArray(260) { 3.0f }  // Above raw-logit check but uniform
        val result = modelMapperV2.mapToLabelInfo(logits)

        assertTrue("Uniform logits above MIN_LOGIT but below CONFIDENCE → Unknown",
            result is LabelResult.Unknown)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // mapToTop5 Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `mapToTop5 should return up to 5 results`() {
        val labels = List(260) { i ->
            LabelInfoV2("Class_$i", "类别$i", isCookable = i % 2 == 0)
        }
        every { config.labels } returns labels
        every { config.numClasses } returns 260

        val logits = FloatArray(260) { i -> i.toFloat() * 0.1f }
        val results = modelMapperV2.mapToTop5(logits)

        assertEquals(5, results.size)
    }

    @Test
    fun `mapToTop5 should be sorted by confidence descending`() {
        val labels = List(260) { i ->
            LabelInfoV2("Class_$i", "类别$i", false)
        }
        every { config.labels } returns labels
        every { config.numClasses } returns 260

        val logits = FloatArray(260) { i -> i.toFloat() * 0.1f }
        val results = modelMapperV2.mapToTop5(logits)

        for (i in 0 until results.size - 1) {
            assertTrue("Top-5 sorted descending at position $i",
                results[i].confidence >= results[i + 1].confidence)
        }
    }

    @Test
    fun `mapToTop5 should return fewer than 5 when labels are fewer`() {
        val labels = List(3) { i ->
            LabelInfoV2("Class_$i", "类别$i", false)
        }
        every { config.labels } returns labels
        every { config.numClasses } returns 3

        val logits = FloatArray(3) { i -> (3 - i).toFloat() }
        val results = modelMapperV2.mapToTop5(logits)

        assertEquals(3, results.size)
    }

    @Test
    fun `mapToTop5 should return empty when labels empty`() {
        every { config.labels } returns emptyList()
        every { config.numClasses } returns 0

        val logits = FloatArray(1) { 10.0f }
        val results = modelMapperV2.mapToTop5(logits)

        assertTrue("Top-5 should be empty when labels empty", results.isEmpty())
    }

    @Test
    fun `mapToTop5 should skip out-of-bounds indices gracefully`() {
        every { config.labels } returns listOf(
            LabelInfoV2("Only", "唯一", false)
        )
        every { config.numClasses } returns 1

        // 5 logits but only 1 label
        val logits = FloatArray(5) { i -> (5 - i).toFloat() }
        val results = modelMapperV2.mapToTop5(logits)

        assertEquals("Only in-bounds indices should be included", 1, results.size)
        assertEquals("Only", results[0].label)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `mapToLabelInfo should handle empty logits array`() {
        every { config.labels } returns emptyList()
        every { config.numClasses } returns 0

        val logits = FloatArray(0)
        val result = modelMapperV2.mapToLabelInfo(logits)

        assertTrue("Empty logits → Unknown", result is LabelResult.Unknown)
    }

    @Test
    fun `mapToLabelInfo should handle FloatArray with NaN values`() {
        every { config.labels } returns listOf(
            LabelInfoV2("Class_0", "类别0", false)
        )
        every { config.numClasses } returns 1

        val logits = FloatArray(1) { Float.NaN }
        val result = modelMapperV2.mapToLabelInfo(logits)

        // maxOrNull of NaN is NaN; NaN < MIN_LOGIT is false (NaN comparisons always false)
        // This is a robustness check — the method should not crash
        assertNotNull("Should not crash with NaN input", result)
    }

    @Test
    fun `softmax should be numerically stable with mixed extreme logits`() {
        // Mix of very positive and very negative logits
        val logits = FloatArray(260) { i ->
            if (i < 10) 500f + i * 10f  // Very positive
            else -500f - i * 10f         // Very negative
        }
        val probs = invokeSoftmax(logits)
        assertEquals(1.0f, probs.sum(), 0.001f)
        // First 10 should have near-zero probability (much smaller than the largest)
        // The largest logit should dominate
        val maxIdx = probs.indices.maxByOrNull { probs[it] }!!
        assertEquals(9, maxIdx)  // index 9 has largest logit (500 + 90 = 590)
    }

    @Test
    fun `mapToLabelInfo should accept logits at exact threshold boundary`() {
        every { config.labels } returns listOf(
            LabelInfoV2("Test", "测试", false)
        )
        every { config.numClasses } returns 1

        // Exact MIN_LOGIT_FOR_CONFIDENCE value (= 2.0): should pass raw-logit check
        val logits = FloatArray(1) { 2.0f }
        val result = modelMapperV2.mapToLabelInfo(logits)

        // Only one class, softmax=1.0 > 0.5 threshold → Known
        assertTrue("Exact threshold logit should pass", result is LabelResult.Known)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Reflection helper to test private softmax (now in MathUtils.kt)
    // ═══════════════════════════════════════════════════════════════════════

    private fun invokeSoftmax(logits: FloatArray): FloatArray {
        return com.example.freshscan.util.MathUtils.softmax(logits)
    }
}
