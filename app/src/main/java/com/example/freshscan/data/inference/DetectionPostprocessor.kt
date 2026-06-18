package com.example.freshscan.data.inference

import android.graphics.RectF
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult

/**
 * Post-processing for EfficientDet detection results:
 *
 * 1. **Confidence filtering** — keep all detections; the 260-class model
 *    is the real fruit/vegetable classifier.
 * 2. **Coordinate denormalization** — convert normalized [0,1] coordinates to pixel coordinates.
 * 3. **NMS deduplication** — remove overlapping boxes with IoU > 0.5, keeping highest confidence.
 *
 * ### Why no COCO class filter?
 *
 * EfficientDet-Lite0 is trained on COCO (80 classes) which only covers a few
 * produce types (apple=47, orange=49, banana=52, broccoli=53, carrot=54).
 * Most fruits/vegetables (tomato, cucumber, pepper, potato, bitter gourd, etc.)
 * are NOT in COCO — EfficientDet either misses them entirely or classifies them
 * under unrelated COCO categories.
 *
 * Filtering to COCO food classes caused the pipeline to discard valid detection
 * boxes and fall back to full-image classification, which severely degraded
 * accuracy on real photos (backgrounds, hands, plates confuse the 260-class model).
 *
 * The current approach: keep ALL EfficientDet detections above the confidence
 * threshold. The 260-class Fruits-360 model acts as the real classifier — it
 * returns [LabelResult.Unknown] for non-fruit boxes, filtering them at the
 * classification stage where it has much more expertise than EfficientDet's
 * narrow COCO vocabulary.
 */
object DetectionPostprocessor {

    /**
     * COCO category indices relevant to fruits and vegetables.
     *
     * No longer used for filtering. Kept for reference only.
     */
    @Deprecated("No longer filtered — 260-class model handles classification")
    val FOOD_CLASSES: Set<Int> = setOf(47, 49, 52, 53, 54, 55)

    /**
     * Extract valid detection boxes from a MediaPipe [ObjectDetectorResult].
     *
     * Pipeline:
     * 1. Keep all detections (no COCO-class filter — 260-class model classifies).
     * 2. Sort by confidence descending.
     * 3. Denormalize bounding box coordinates to pixel space.
     * 4. Apply NMS to remove overlapping boxes.
     *
     * @param result Raw MediaPipe detection result.
     * @param imageWidth Original photo width in pixels.
     * @param imageHeight Original photo height in pixels.
     * @return Pixel-coordinate detection boxes, NMS-deduplicated, confidence-sorted descending.
     */
    fun extractValidBoxes(
        result: ObjectDetectorResult,
        imageWidth: Int,
        imageHeight: Int
    ): List<DetectedBox> {
        return result.detections()
            .sortedByDescending { detection ->
                detection.categories().maxOf { cat -> cat.score() }
            }
            .map { detection ->
                val bbox = detection.boundingBox()  // normalized [0, 1]
                DetectedBox(
                    rect = RectF(
                        bbox.left * imageWidth,
                        bbox.top * imageHeight,
                        bbox.right * imageWidth,
                        bbox.bottom * imageHeight
                    ),
                    confidence = detection.categories().maxOf { it.score() },
                    cocoClassId = detection.categories().first().index()
                )
            }
            .let { applyNms(it) }
    }

    /**
     * Simple greedy NMS (Non-Maximum Suppression).
     *
     * Given a confidence-sorted box list, iterates from highest confidence and
     * suppresses any lower-confidence box with IoU > [iouThreshold].
     *
     * @param boxes Detection boxes, pre-sorted by confidence descending.
     * @param iouThreshold IoU threshold above which boxes are considered overlapping. Default 0.5.
     * @return Kept boxes with overlapping low-confidence boxes removed.
     */
    fun applyNms(boxes: List<DetectedBox>, iouThreshold: Float = 0.5f): List<DetectedBox> {
        if (boxes.isEmpty()) return emptyList()

        val kept = mutableListOf<DetectedBox>()
        val suppressed = BooleanArray(boxes.size) { false }

        for (i in boxes.indices) {
            if (suppressed[i]) continue
            kept.add(boxes[i])
            for (j in i + 1 until boxes.size) {
                if (!suppressed[j] && boxes[i].rect.iou(boxes[j].rect) > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        return kept
    }
}

/**
 * Intermediate representation of a single detection box before classification.
 *
 * @property rect Bounding box in pixel coordinates (relative to the original photo).
 * @property confidence Detection confidence score [0.0, 1.0].
 * @property cocoClassId COCO category index (or -1 if from sliding-window fallback).
 */
data class DetectedBox(
    val rect: RectF,
    val confidence: Float,
    val cocoClassId: Int
)

/**
 * Compute Intersection-over-Union (IoU) between this [RectF] and [other].
 *
 * IoU = area(intersection) / area(union).
 * Returns 0.0 if the rectangles don't overlap or if the union area is zero.
 *
 * @return IoU value in [0.0, 1.0].
 */
fun RectF.iou(other: RectF): Float {
    val interLeft = maxOf(left, other.left)
    val interTop = maxOf(top, other.top)
    val interRight = minOf(right, other.right)
    val interBottom = minOf(bottom, other.bottom)
    if (interLeft >= interRight || interTop >= interBottom) return 0f

    val interArea = (interRight - interLeft) * (interBottom - interTop)
    // Use field arithmetic instead of width()/height() methods for JVM test compatibility
    // (Android stubs return 0f for method calls in unit tests with isReturnDefaultValues).
    val area1 = (right - left) * (bottom - top)
    val area2 = (other.right - other.left) * (other.bottom - other.top)
    val unionArea = area1 + area2 - interArea

    return if (unionArea <= 0f) 0f else interArea / unionArea
}
