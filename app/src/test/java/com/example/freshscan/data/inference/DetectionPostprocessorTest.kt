package com.example.freshscan.data.inference

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionPostprocessorTest {

    // ═══════════════════════════════════════════════════════════════════════
    // RectF.iou() Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `iou should return 1 for identical rectangles`() {
        val a = rectF(0f, 0f, 100f, 100f)
        val b = rectF(0f, 0f, 100f, 100f)
        assertEquals(1.0f, a.iou(b), 0.001f)
    }

    @Test
    fun `iou should return 0 for non-overlapping rectangles`() {
        val a = rectF(0f, 0f, 50f, 50f)
        val b = rectF(100f, 100f, 200f, 200f)
        assertEquals(0.0f, a.iou(b), 0.001f)
    }

    @Test
    fun `iou should return 0 for adjacent but not overlapping rectangles`() {
        val a = rectF(0f, 0f, 50f, 50f)
        val b = rectF(50f, 0f, 100f, 50f)
        // Touching at x=50 but not overlapping (interRight==interLeft)
        assertEquals(0.0f, a.iou(b), 0.001f)
    }

    @Test
    fun `iou should compute correct value for partially overlapping rectangles`() {
        // a = 100×100 (area 10000), b = 100×100 shifted by 50x50
        val a = rectF(0f, 0f, 100f, 100f)
        val b = rectF(50f, 50f, 150f, 150f)
        // intersection: (50,50)-(100,100) = 50×50 = 2500
        // union: 10000 + 10000 - 2500 = 17500
        // IoU: 2500 / 17500 = 0.142857...
        assertEquals(2500f / 17500f, a.iou(b), 0.001f)
    }

    @Test
    fun `iou should handle rectangle fully containing another`() {
        val outer = rectF(0f, 0f, 200f, 200f)
        val inner = rectF(50f, 50f, 100f, 100f)
        // intersection = inner area = 2500
        // union = outer area = 40000
        // IoU: 2500 / 40000 = 0.0625
        assertEquals(0.0625f, outer.iou(inner), 0.001f)
    }

    @Test
    fun `iou should be symmetric`() {
        val a = rectF(10f, 20f, 80f, 90f)
        val b = rectF(30f, 40f, 120f, 130f)
        assertEquals(a.iou(b), b.iou(a), 0.0001f)
    }

    @Test
    fun `iou should handle zero-area rectangles`() {
        val a = rectF(0f, 0f, 0f, 0f)
        val b = rectF(0f, 0f, 10f, 10f)
        // area(a)=0, area(b)=100, interArea=0, unionArea=100, IoU=0
        assertEquals(0.0f, a.iou(b), 0.001f)
    }

    @Test
    fun `iou should handle both zero-area rectangles`() {
        val a = rectF(0f, 0f, 0f, 0f)
        val b = rectF(1f, 1f, 1f, 1f)
        // unionArea = 0, should return 0
        assertEquals(0.0f, a.iou(b), 0.001f)
    }

    @Test
    fun `iou should handle negative-width rectangle gracefully`() {
        // right < left: width negative
        val a = rectF(100f, 0f, 0f, 100f)  // effectively 0-100 if normalized
        val b = rectF(50f, 25f, 150f, 75f)
        // Should not crash, returns some value
        val result = a.iou(b)
        assertTrue("Should not be NaN", !result.isNaN())
    }

    @Test
    fun `iou should handle very small overlaps precisely`() {
        val a = rectF(0f, 0f, 100f, 100f)
        val b = rectF(99.9f, 99.9f, 200f, 200f)
        val result = a.iou(b)
        // Tiny overlap: 0.1 × 0.1 = 0.01
        assertTrue("Tiny overlap IoU should be > 0", result > 0f)
        assertTrue("Tiny overlap IoU should be < 0.001", result < 0.001f)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // applyNms() Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `applyNms should return empty list for empty input`() {
        val result = DetectionPostprocessor.applyNms(emptyList())
        assertTrue("NMS of empty list should be empty", result.isEmpty())
    }

    @Test
    fun `applyNms should keep single box`() {
        val boxes = listOf(
            DetectedBox(rectF(0f, 0f, 100f, 100f), 0.9f, 47)
        )
        val result = DetectionPostprocessor.applyNms(boxes)
        assertEquals(1, result.size)
        assertEquals(0.9f, result[0].confidence)
    }

    @Test
    fun `applyNms should keep two non-overlapping boxes`() {
        val boxes = listOf(
            DetectedBox(rectF(0f, 0f, 50f, 50f), 0.9f, 47),
            DetectedBox(rectF(100f, 100f, 150f, 150f), 0.8f, 49)
        )
        val result = DetectionPostprocessor.applyNms(boxes)
        assertEquals(2, result.size)
    }

    @Test
    fun `applyNms should suppress lower-confidence overlapping box`() {
        val boxes = listOf(
            DetectedBox(rectF(0f, 0f, 100f, 100f), 0.95f, 47),   // higher confidence
            DetectedBox(rectF(10f, 10f, 90f, 90f), 0.7f, 47)     // lower, mostly inside
        )
        // IoU ≈ 80×80 / 100×100 = 6400/10000 = 0.64 → > 0.5, should suppress
        val result = DetectionPostprocessor.applyNms(boxes)
        assertEquals(1, result.size)
        assertEquals(0.95f, result[0].confidence)
    }

    @Test
    fun `applyNms should keep overlapping box with low IoU`() {
        val boxes = listOf(
            DetectedBox(rectF(0f, 0f, 100f, 100f), 0.95f, 47),
            DetectedBox(rectF(80f, 80f, 180f, 180f), 0.7f, 49)   // small overlap
        )
        // IoU: inter=20×20=400, union=10000+10000-400=19600, IoU=400/19600≈0.0204 → <0.5
        val result = DetectionPostprocessor.applyNms(boxes)
        assertEquals(2, result.size)
    }

    @Test
    fun `applyNms should keep both when below IoU threshold`() {
        // 100×100 box at (0,0), 100×100 box at (50,0): inter=50×100=5000, union=15000, IoU=0.333
        // Not > 0.5, so both kept
        val boxes = listOf(
            DetectedBox(rectF(0f, 0f, 100f, 100f), 0.9f, 47),
            DetectedBox(rectF(50f, 0f, 150f, 100f), 0.8f, 47)
        )
        val result = DetectionPostprocessor.applyNms(boxes)
        assertEquals(2, result.size)
    }

    @Test
    fun `applyNms should respect custom IoU threshold`() {
        val a = rectF(0f, 0f, 100f, 100f)
        val b = rectF(60f, 60f, 160f, 160f)
        // IoU: inter=40×40=1600, union=20000-1600=18400, IoU≈0.087

        // With threshold 0.05 (strict), box with 0.087 IoU should be suppressed
        val resultStrict = DetectionPostprocessor.applyNms(
            listOf(
                DetectedBox(a, 0.9f, 47),
                DetectedBox(b, 0.7f, 47)
            ),
            iouThreshold = 0.05f
        )
        assertEquals("Strict NMS should suppress overlapping", 1, resultStrict.size)

        // With threshold 0.5 (lenient), box with 0.087 IoU should be kept
        val resultLenient = DetectionPostprocessor.applyNms(
            listOf(
                DetectedBox(a, 0.9f, 47),
                DetectedBox(b, 0.7f, 47)
            ),
            iouThreshold = 0.5f
        )
        assertEquals("Lenient NMS should keep both", 2, resultLenient.size)
    }

    @Test
    fun `applyNms should handle three boxes where middle is suppressed`() {
        val boxes = listOf(
            DetectedBox(rectF(0f, 0f, 100f, 100f), 0.95f, 47),    // keeps, suppresses #2 (index 2)
            DetectedBox(rectF(200f, 200f, 300f, 300f), 0.8f, 49),  // keeps (far away)
            DetectedBox(rectF(10f, 10f, 90f, 90f), 0.6f, 47)      // suppressed by #0
        )
        val result = DetectionPostprocessor.applyNms(boxes, iouThreshold = 0.5f)
        assertEquals(2, result.size)
        assertEquals(0.95f, result[0].confidence)
        assertEquals(0.8f, result[1].confidence)
    }

    @Test
    fun `applyNms should preserve all non-overlapping boxes regardless of confidence order`() {
        val boxes = listOf(
            DetectedBox(rectF(0f, 0f, 50f, 50f), 0.7f, 47),
            DetectedBox(rectF(100f, 100f, 150f, 150f), 0.95f, 49),
            DetectedBox(rectF(200f, 200f, 250f, 250f), 0.6f, 52),
            DetectedBox(rectF(300f, 300f, 350f, 350f), 0.85f, 53)
        )
        // All boxes are non-overlapping — all should be kept
        val result = DetectionPostprocessor.applyNms(boxes)
        assertEquals(4, result.size)
    }

    @Test
    fun `applyNms should handle boxes with negative coordinates`() {
        val boxes = listOf(
            DetectedBox(rectF(-10f, -10f, 100f, 100f), 0.9f, 47),
            DetectedBox(rectF(50f, 50f, 150f, 150f), 0.7f, 47)
        )
        // Should not crash with negative coordinates
        val result = DetectionPostprocessor.applyNms(boxes)
        assertTrue("Should not crash", result.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FOOD_CLASSES constant
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `FOOD_CLASSES should contain expected COCO indices`() {
        val expected = setOf(47, 49, 52, 53, 54, 55)
        assertEquals(expected, DetectionPostprocessor.FOOD_CLASSES)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DetectedBox data class
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `DetectedBox fields should store values correctly`() {
        val r = rectF(10f, 20f, 110f, 120f)
        val box = DetectedBox(r, 0.9f, 47)
        assertEquals(0.9f, box.confidence)
        assertEquals(47, box.cocoClassId)
        assertEquals(10f, box.rect.left)
        assertEquals(20f, box.rect.top)
        assertEquals(110f, box.rect.right)
        assertEquals(120f, box.rect.bottom)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper: create RectF with fields set directly (bypasses JVM stub issue)
    // ═══════════════════════════════════════════════════════════════════════

    companion object {
        /**
         * Create a [RectF] with the given coordinates.
         *
         * Sets fields directly rather than via the constructor because the
         * Android JVM stub (with isReturnDefaultValues=true) does not write
         * constructor parameters to fields.
         */
        private fun rectF(left: Float, top: Float, right: Float, bottom: Float): RectF {
            val r = RectF()
            r.left = left
            r.top = top
            r.right = right
            r.bottom = bottom
            return r
        }
    }
}
