package com.example.freshscan.util

/**
 * Global constants for the FreshScan application.
 */
object Constants {
    // --- Model ---
    /** TFLite model filename in assets/model/ */
    const val MODEL_FILENAME = "fruit_freshness_model.tflite"

    /** Labels filename in assets/model/ */
    const val LABELS_FILENAME = "labels.txt"

    /** Model input size (square). */
    const val MODEL_INPUT_SIZE = 224

    /** Model input channels (RGB). */
    const val MODEL_INPUT_CHANNELS = 3

    /** Normalization factor: pixel / 255.0 */
    const val NORMALIZATION_STD = 255.0f

    /** Confidence threshold below which result is marked UNCERTAIN. */
    const val CONFIDENCE_THRESHOLD = 0.5f

    /**
     * Minimum raw logit magnitude for the top class.
     * If max(rawLogits) < this value, the model hasn't strongly
     * activated any class → likely not a fruit → force UNKNOWN.
     *
     * Tuning notes (updated 2026-06-17 from real-device testing):
     * - v1 model in ideal light: logits 8-15
     * - Real phone camera in indoor light: logits 2-6 (much lower than
     *   training set because real-world backgrounds/lighting differ)
     * - Non-fruit (palm, screen, wall): logits 0-2
     * - Lowered from 4.0 to 2.0 to avoid false negatives on real photos
     *   while still filtering out non-fruit backgrounds.
     */
    const val MIN_LOGIT_FOR_CONFIDENCE = 2.0f

    // --- Inference ---
    /** Number of CPU threads for TFLite interpreter. */
    const val TFLITE_NUM_THREADS = 4

    // --- Inference (v1 CameraX real-time pipeline constants — DEPRECATED in v2) ---
    /** @deprecated v1 CameraX frame interval — v2 uses single-shot inference. */
    @Deprecated("v1 constant — unused in v2 single-shot pipeline", ReplaceWith("N/A"))
    const val INFERENCE_INTERVAL_MS = 300L

    /** @deprecated v1 withTimeout — v2 uses single-shot inference. */
    @Deprecated("v1 constant — unused in v2 single-shot pipeline")
    const val INFERENCE_TIMEOUT_MS = 800L

    /** @deprecated v1 stability buffer — v2 uses single-shot inference. */
    @Deprecated("v1 constant — unused in v2 single-shot pipeline")
    const val STABILITY_FRAME_COUNT = 3

    /** @deprecated v1 timeout warning — v2 uses single-shot inference. */
    @Deprecated("v1 constant — unused in v2 single-shot pipeline")
    const val TIMEOUT_WARNING_THRESHOLD = 3

    /** @deprecated v1 low-power degradation — v2 uses single-shot inference. */
    @Deprecated("v1 constant — unused in v2 single-shot pipeline")
    const val LOW_POWER_TIMEOUT_THRESHOLD = 10

    /** @deprecated v1 low-power interval — v2 uses single-shot inference. */
    @Deprecated("v1 constant — unused in v2 single-shot pipeline")
    const val LOW_POWER_INTERVAL_MS = 500L

    // --- History ---
    /** Maximum number of history records to keep. */
    const val MAX_HISTORY_COUNT = 50

    /** Maximum thumbnail dimension in pixels. */
    const val MAX_THUMBNAIL_SIZE = 256

    /** Thumbnail storage directory under filesDir. */
    const val THUMBNAIL_DIR = "thumbnails"

    /** History database filename. */
    const val DATABASE_NAME = "fruit_freshness.db"

    // --- Logging ---
    /** Log tag prefix for the Logger utility. */
    const val LOG_TAG_PREFIX = "FreshScan"

    // --- Guide ---
    /** DataStore preferences filename for app settings. */
    const val PREFERENCES_NAME = "freshscan_preferences"
}
