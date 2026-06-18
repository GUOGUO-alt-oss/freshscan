package com.example.freshscan.ui.screen.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.freshscan.data.inference.DetectedBox
import com.example.freshscan.data.inference.DetectionPostprocessor
import com.example.freshscan.data.inference.EfficientDetEngine
import com.example.freshscan.data.inference.TFLiteClassifier
import com.example.freshscan.data.mapper.LabelResult
import com.example.freshscan.data.mapper.ModelMapper
import com.example.freshscan.data.mapper.ModelMapperV2
import com.example.freshscan.data.recipe.RecipeEngine
import com.example.freshscan.di.ModelV2
import com.example.freshscan.di.FreshnessModel
import com.example.freshscan.domain.model.DetectedItem
import com.example.freshscan.domain.model.FreshnessLevel
import com.example.freshscan.R
import com.example.freshscan.domain.model.FruitCategory
import com.example.freshscan.domain.model.RecipeCategory
import com.example.freshscan.domain.model.TasteProfile
import com.example.freshscan.domain.repository.HistoryRepository
import com.example.freshscan.util.ImagePreprocessor
import com.example.freshscan.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Analysis screen — manages the full inference lifecycle.
 *
 * State machine:
 *   Idle → Loading → Animating → (Results | Empty | Error)
 *                              ↑        │
 *                              └────────┘ (retake / dismissError)
 *
 * Inference pipeline (3-stage):
 *   1. Multi-object detection via EfficientDet-Lite0 (or sliding-window fallback)
 *   2. Per-box 260-class category classification (falls back to v1 18-class model)
 *   3. Per-box 18-class freshness classification
 *
 * Degradation path:
 *   When EfficientDet fails or returns no boxes, the pipeline degrades to
 *   single-box inference using the v1 18-class model on the full image.
 *   This is the M2 primary path until the 260-class model is trained in M3.
 *
 * Error recovery:
 *   - Permission denied → Error with guidance message
 *   - Model load failure → Error with model-specific message
 *   - OOM → Error with memory warning
 *   - Generic exception → Error with retry option
 *
 * Process Death recovery:
 *   SavedStateHandle preserves photoUri; on re-creation the pipeline
 *   resumes automatically from the saved URI.
 */
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val efficientDet: EfficientDetEngine,
    @ModelV2 private val classifier260: TFLiteClassifier,
    @FreshnessModel private val classifierFreshness: TFLiteClassifier,
    private val modelMapper260: ModelMapperV2,
    private val modelMapperFreshness: ModelMapper,
    private val imagePreprocessor: ImagePreprocessor,
    private val historyRepository: HistoryRepository,
    private val recipeEngine: RecipeEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    /** One-shot side-effect channel (e.g., navigation events). */
    private val _sideEffects = Channel<AnalysisSideEffect>(Channel.BUFFERED)
    val sideEffects: Flow<AnalysisSideEffect> = _sideEffects.receiveAsFlow()

    init {
        // Process Death recovery: if a photo URI was saved before the process
        // was killed, restore it and resume analysis automatically.
        val savedUriString = savedStateHandle.get<String>("photoUri")
        if (savedUriString != null) {
            val uri = Uri.parse(savedUriString)
            _uiState.update { it.copy(photoUri = uri) }
            startAnalysis(uri)
        }
    }

    // ─── Public API ────────────────────────────────────────────────────────

    /**
     * Start analysis for a captured photo.
     *
     * Called from ActivityResultCallback when the system camera returns a photo URI,
     * or from Process Death recovery in [init].
     *
     * State transitions: Idle → Loading (first time) / Animating (cached) → Results/Empty/Error.
     *
     * @param photoUri URI of the captured photo (content:// or file://).
     */
    fun startAnalysis(photoUri: Uri) {
        savedStateHandle["photoUri"] = photoUri.toString()
        _uiState.update {
            it.copy(
                photoUri = photoUri,
                screenState = if (isModelReady()) AnalysisScreenState.Animating
                             else AnalysisScreenState.Loading,
                isAnalyzing = true,
                errorMessage = null,
                items = emptyList()
            )
        }

        viewModelScope.launch {
            try {
                val pipelineStartMs = System.currentTimeMillis()

                // Lazy-load freshness model (first call: ~200ms, masked by animation)
                ensureFreshnessLoaded()
                _uiState.update { it.copy(screenState = AnalysisScreenState.Animating) }

                // Load bitmap from URI with OOM protection
                val bitmap = loadBitmap(photoUri)
                    ?: throw IllegalStateException(context.getString(R.string.error_photo_missing))

                // Run 3-stage inference pipeline
                val items = runInference(bitmap)
                val inferenceTimeMs = System.currentTimeMillis() - pipelineStartMs

                // Enforce minimum animation display time (~2s per design spec §3.7).
                // If inference finished faster than the minimum, delay the
                // state transition so the particle animation has time to play.
                val minAnimationMs = 2000L
                val elapsedSinceStart = System.currentTimeMillis() - pipelineStartMs
                if (elapsedSinceStart < minAnimationMs) {
                    delay(minAnimationMs - elapsedSinceStart)
                }

                // Determine result state
                val screenState = if (items.isEmpty()) {
                    AnalysisScreenState.Empty
                } else {
                    AnalysisScreenState.Results(items.size)
                }

                _uiState.update {
                    it.copy(
                        items = items,
                        screenState = screenState,
                        isAnalyzing = false,
                        errorMessage = null
                    )
                }

                // Recycle bitmap to free memory
                if (bitmap.isMutable) bitmap.recycle()

                // Auto-save to history (only when we have results)
                if (items.isNotEmpty()) {
                    saveToHistory(items, inferenceTimeMs)
                }

            } catch (e: Exception) {
                Logger.e("AnalysisVM", "Analysis failed", e)
                _uiState.update {
                    it.copy(
                        screenState = AnalysisScreenState.Error(
                            mapErrorToMessage(e)
                        ),
                        isAnalyzing = false
                    )
                }
            }
        }
    }

    /**
     * Find recipes matching the detected items.
     * Called when the user taps [找菜谱] in the BottomSheet.
     *
     * Delegates to [RecipeEngine.recommend] which matches detected ingredients
     * against preset recipes and ranks by match count + taste profile weighting.
     *
     * @param category Optional recipe category filter applied after ranking.
     */
    fun findRecipes(category: RecipeCategory? = null) {
        viewModelScope.launch {
            try {
                val items = _uiState.value.items
                val profile = loadTasteProfile()
                val result = recipeEngine.recommend(items, profile)

                var recipes = result.recipes
                if (category != null) {
                    recipes = recipes.filter { it.category == category }
                }

                _uiState.update {
                    it.copy(
                        recipes = recipes,
                        recipeNote = result.note,
                        selectedCategory = category,
                        sheetState = if (recipes.isNotEmpty()) SheetState.FULL
                                     else SheetState.COLLAPSED
                    )
                }
            } catch (e: Exception) {
                Logger.e("AnalysisVM", "Recipe recommendation failed", e)
                _uiState.update {
                    it.copy(
                        recipeNote = context.getString(R.string.recipe_temp_unavailable)
                    )
                }
            }
        }
    }

    /**
     * Reset state for a new photo capture.
     * Transitions back to Idle, which triggers the camera launcher in AnalysisScreen.
     */
    fun retake() {
        savedStateHandle.remove<Unit>("photoUri")
        _uiState.update { AnalysisUiState() }
        viewModelScope.launch {
            _sideEffects.send(AnalysisSideEffect.Retake)
        }
    }

    /**
     * Dismiss the error state and return to Idle (re-launches camera).
     */
    fun dismissError() {
        _uiState.update {
            it.copy(
                screenState = AnalysisScreenState.Idle,
                errorMessage = null
            )
        }
    }

    /**
     * Retry analysis with the same photo URI (error recovery path).
     */
    fun retry() {
        val uri = _uiState.value.photoUri ?: return
        _uiState.update { it.copy(screenState = AnalysisScreenState.Loading, errorMessage = null) }
        startAnalysis(uri)
    }

    /**
     * Update the BottomSheet expansion state.
     */
    fun setSheetState(state: SheetState) {
        _uiState.update { it.copy(sheetState = state) }
    }

    override fun onCleared() {
        super.onCleared()
        savedStateHandle.remove<Unit>("photoUri")
        // Close models immediately — lazy loading ensures fast reload on next visit.
        // Peak memory reduction outweighs the ~200ms reload cost (masked by animation).
        closeAllModels()
    }

    // ─── Model Lifecycle ───────────────────────────────────────────────────

    /** Check if all required models are already loaded. */
    private fun isModelReady(): Boolean =
        classifierFreshness.isLoaded  // v1 model is the minimum requirement

    /**
     * Ensure the freshness classifier is loaded (the minimum requirement).
     *
     * Other models (EfficientDet, 260-class classifier) are loaded on-demand:
     * - [EfficientDetEngine.detect] internally calls its own [EfficientDetEngine.ensureLoaded]
     * - 260-class classifier is loaded via [classifyBox] when first needed
     *
     * This avoids peak memory from all three models (~16MB) loading at once,
     * which could trigger OOM on low-end devices (minSdk=24).
     */
    private suspend fun ensureFreshnessLoaded() = withContext(Dispatchers.Default) {
        try {
            classifierFreshness.ensureLoaded()
            Logger.i("AnalysisVM", "Freshness classifier loaded")
        } catch (e: Exception) {
            Logger.e("AnalysisVM", "Failed to load freshness classifier", e)
            throw IllegalStateException(context.getString(R.string.error_ai_engine_failed), e)
        }
    }

    /** Release all model resources. Safe to call multiple times. */
    private fun closeAllModels() {
        efficientDet.close()
        classifier260.close()
        classifierFreshness.close()
        Logger.i("AnalysisVM", "All models closed")
    }

    // ─── Bitmap Loading ────────────────────────────────────────────────────

    /**
     * Load a Bitmap from a content URI with OOM protection.
     *
     * Strategy:
     * 1. Sample down with inSampleSize=2 (halves resolution, quarters memory)
     * 2. If still > 1920px on the long edge, scale down further
     * 3. Returns null if the URI is unreadable
     *
     * @return Decoded bitmap, or null if the file is missing or unreadable.
     */
    private suspend fun loadBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 2  // Halve resolution for memory safety
                inPreferredConfig = Bitmap.Config.ARGB_8888  // sRGB-like, avoids wide-gamut
            }

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext null

            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Limit max dimension to 1920px to prevent OOM
            // Bitmap.createScaledBitmap with filter=true forces ARGB_8888 output
            bitmap?.let { bm ->
                val maxDim = maxOf(bm.width, bm.height)
                if (maxDim > 1920) {
                    val scale = 1920f / maxDim
                    val scaled = Bitmap.createScaledBitmap(
                        bm,
                        (bm.width * scale).toInt(),
                        (bm.height * scale).toInt(),
                        true  // filter=true → forces ARGB_8888 output
                    )
                    if (scaled != bm) bm.recycle()
                    scaled
                } else if (bm.config != Bitmap.Config.ARGB_8888) {
                    // Force ARGB_8888 even when no resize needed
                    val normalized = bm.copy(Bitmap.Config.ARGB_8888, false)
                    bm.recycle()
                    normalized
                } else {
                    bm
                }
            }
        } catch (e: Exception) {
            Logger.e("AnalysisVM", "Failed to load bitmap from URI", e)
            null
        }
    }

    // ─── 3-Stage Inference Pipeline ────────────────────────────────────────

    /**
     * Run the full 3-stage inference pipeline on [Dispatchers.Default].
     *
     * Stage 1: Multi-object detection (EfficientDet → sliding-window fallback)
     * Stage 2: Per-box 260-class category classification (falls back to v1)
     * Stage 3: Per-box 18-class freshness classification
     *
     * @param bitmap Input photo (already size-limited by [loadBitmap]).
     * @return List of detected items with category, freshness, and bounding boxes.
     */
    private suspend fun runInference(bitmap: Bitmap): List<DetectedItem> =
        withContext(Dispatchers.Default) {

            // ═══ Stage 1: Multi-object detection ═══
            val detectionBoxes = detectBoxes(bitmap)

            if (detectionBoxes.isEmpty()) return@withContext emptyList()

            // ═══ Stage 2+3: Classify each box ═══
            detectionBoxes.mapNotNull { box -> classifyBox(bitmap, box) }
        }

    /**
     * Stage 1: Detect objects in the photo.
     *
     * Tries EfficientDet first. If it fails or returns no valid food boxes,
     * degrades to a single full-image box (v1 model handles the rest).
     *
     * @return List of detection boxes in pixel coordinates.
     */
    private fun detectBoxes(bitmap: Bitmap): List<DetectedBox> {
        // Try EfficientDet
        try {
            val result = efficientDet.detect(bitmap)
            val boxes = DetectionPostprocessor.extractValidBoxes(
                result, bitmap.width, bitmap.height
            )
            if (boxes.isNotEmpty()) {
                Logger.i("AnalysisVM", "EfficientDet found ${boxes.size} box(es)")
                return boxes
            }
            Logger.i("AnalysisVM", "EfficientDet returned no food boxes, degrading")
        } catch (e: Exception) {
            Logger.w("AnalysisVM", "EfficientDet detection failed, degrading", e)
        }

        // Degradation: single full-image box → v1 model classifies the whole image
        Logger.i("AnalysisVM", "Using single-box degradation path")
        return listOf(
            DetectedBox(
                rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                confidence = 1.0f,
                cocoClassId = -1  // -1 = not from COCO (degradation path)
            )
        )
    }

    /**
     * Stage 2+3: Classify a single crop using both models.
     *
     * Stage 2 (category): 260-class Fruits-360 model → [LabelResult].
     *   Falls back to v1 18-class model if 260-class returns Unknown or isn't loaded.
     * Stage 3 (freshness): 18-class freshness model → [FreshnessLevel].
     *   Always runs; defaults to [FreshnessLevel.FRESH] if the 18-class model
     *   can't determine freshness for items outside its 9-class training set.
     *
     * @param bitmap Full photo (for coordinate normalization).
     * @param box Detection box in pixel coordinates.
     * @return DetectedItem with category, freshness, and normalized bbox, or null.
     */
    private suspend fun classifyBox(bitmap: Bitmap, box: DetectedBox): DetectedItem? {
        val crop = cropBitmap(bitmap, box.rect) ?: return null

        // ── Stage 2: 260-class category classification ──
        // Load on-demand (not eagerly in ensureFreshnessLoaded) to reduce peak memory.
        val labelResult: LabelResult = try {
            classifier260.ensureLoaded()
            val input260 = imagePreprocessor.bitmapToTensorBuffer(crop)
            val logits260 = classifier260.classify(input260.buffer)
            modelMapper260.mapToLabelInfo(logits260)
        } catch (e: Exception) {
            Logger.w("AnalysisVM", "260-class classification failed, falling back to v1", e)
            LabelResult.Unknown
        }

        // ── Stage 3: 18-class freshness classification ──
        val freshnessResult = try {
            val inputFreshness = imagePreprocessor.bitmapToTensorBuffer(crop)
            val logits18 = classifierFreshness.classify(inputFreshness.buffer)
            modelMapperFreshness.mapToResult(logits18, 0L)
        } catch (e: Exception) {
            Logger.w("AnalysisVM", "Freshness classification failed", e)
            null
        }

        // Recycle the crop bitmap immediately
        crop.recycle()

        // ── Assemble DetectedItem ──
        return when (labelResult) {
            is LabelResult.Known -> {
                // 260-class identified the item → use its category data
                // Freshness: use 18-class result if available, else default to FRESH
                val freshness = when {
                    freshnessResult != null &&
                    freshnessResult.freshnessLevel != FreshnessLevel.UNCERTAIN ->
                        freshnessResult.freshnessLevel
                    else -> FreshnessLevel.FRESH
                }
                DetectedItem(
                    id = UUID.randomUUID().toString(),
                    label = labelResult.label,
                    displayName = labelResult.displayName,
                    freshnessLevel = freshness,
                    confidence = labelResult.confidence,
                    bbox = RectF(
                        box.rect.left / bitmap.width.toFloat(),
                        box.rect.top / bitmap.height.toFloat(),
                        box.rect.right / bitmap.width.toFloat(),
                        box.rect.bottom / bitmap.height.toFloat()
                    ),
                    isCookable = labelResult.isCookable
                )
            }
            is LabelResult.Unknown -> {
                // 260-class not available or not confident → fall back to v1 model
                if (freshnessResult == null ||
                    freshnessResult.fruitCategory == FruitCategory.UNKNOWN ||
                    freshnessResult.freshnessLevel == FreshnessLevel.UNCERTAIN) {
                    return null
                }
                DetectedItem(
                    id = UUID.randomUUID().toString(),
                    label = freshnessResult.fruitCategory.name.lowercase(),
                    displayName = freshnessResult.fruitCategory.displayName,
                    freshnessLevel = freshnessResult.freshnessLevel,
                    confidence = freshnessResult.confidence,
                    bbox = RectF(
                        box.rect.left / bitmap.width.toFloat(),
                        box.rect.top / bitmap.height.toFloat(),
                        box.rect.right / bitmap.width.toFloat(),
                        box.rect.bottom / bitmap.height.toFloat()
                    ),
                    isCookable = freshnessResult.fruitCategory.isCookable()
                )
            }
        }
    }

    // ─── Bitmap Utilities ──────────────────────────────────────────────────

    /**
     * Crop a detection box region from the photo.
     *
     * Clamps coordinates to image bounds and filters out boxes that are
     * too small (< 20px in either dimension) for meaningful classification.
     *
     * @param bitmap Source photo.
     * @param rect Crop region in pixel coordinates.
     * @return Cropped bitmap, or null if the region is too small.
     */
    private fun cropBitmap(bitmap: Bitmap, rect: RectF): Bitmap? {
        val rawLeft = rect.left.toInt().coerceIn(0, bitmap.width)
        val rawTop = rect.top.toInt().coerceIn(0, bitmap.height)
        val rawRight = rect.right.toInt().coerceIn(0, bitmap.width)
        val rawBottom = rect.bottom.toInt().coerceIn(0, bitmap.height)

        val rawWidth = rawRight - rawLeft
        val rawHeight = rawBottom - rawTop
        if (rawWidth < 20 || rawHeight < 20) return null

        // Add 15% margin to each side (30% total expansion) so the crop
        // resembles Fruits-360 training distribution (fruit centered with
        // context around it). Tight EfficientDet boxes lack the surrounding
        // margin the 260-class model expects.
        val marginX = (rawWidth * 0.15f).toInt()
        val marginY = (rawHeight * 0.15f).toInt()

        val left = (rawLeft - marginX).coerceIn(0, bitmap.width)
        val top = (rawTop - marginY).coerceIn(0, bitmap.height)
        val right = (rawRight + marginX).coerceIn(0, bitmap.width)
        val bottom = (rawBottom + marginY).coerceIn(0, bitmap.height)

        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    // ─── History Persistence ────────────────────────────────────────────────

    /**
     * Persist detected items to Room history.
     *
     * All items from a single scan share a common [sessionId] so they can
     * be grouped in the history view. Save failures are logged but never
     * surfaced to the user — history is best-effort, not critical path.
     *
     * @param items Detected items from the 3-stage inference pipeline.
     * @param inferenceTimeMs Total pipeline inference time in milliseconds.
     */
    private fun saveToHistory(items: List<DetectedItem>, inferenceTimeMs: Long) {
        viewModelScope.launch {
            val sessionId = java.util.UUID.randomUUID().toString()
            val result = historyRepository.saveDetectedItems(
                items = items,
                sessionId = sessionId,
                inferenceTimeMs = inferenceTimeMs
            )
            result.fold(
                onSuccess = {
                    Logger.i("AnalysisVM", "History saved: ${items.size} items, session=$sessionId")
                },
                onFailure = { e ->
                    Logger.e("AnalysisVM", "Failed to save history", e)
                }
            )
        }
    }

    // ─── Taste Profile ─────────────────────────────────────────────────────

    /**
     * Load user taste profile for recipe ranking.
     *
     * Returns a default profile for now. Full DataStore-backed taste profile
     * support will be wired in a future milestone (dependent on DataStore
     * dependency setup and TasteProfileScreen implementation).
     *
     * The [RecipeEngine.recommend] handles null profile gracefully — it
     * simply skips category preference weighting.
     */
    private suspend fun loadTasteProfile(): TasteProfile = TasteProfile()

    // ─── Error Mapping ─────────────────────────────────────────────────────

    /**
     * Map an exception to a user-friendly error message.
     *
     * Recognizes common failure modes and provides actionable guidance.
     * Uses string resources for all user-facing messages.
     */
    private fun mapErrorToMessage(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("permission", ignoreCase = true) ||
            msg.contains("Permission") ->
                context.getString(R.string.error_permission_denied)

            msg.contains("无法加载照片") ->
                context.getString(R.string.error_photo_missing)

            msg.contains("AI 引擎加载失败") || msg.contains("模型") ->
                context.getString(R.string.error_ai_engine_failed)

            msg.contains("OutOfMemory", ignoreCase = true) ||
            msg.contains("OOM") ->
                context.getString(R.string.error_oom)

            msg.contains("FileProvider", ignoreCase = true) ->
                context.getString(R.string.error_camera_unavailable)

            else ->
                context.getString(R.string.error_analysis_failed,
                    e.message ?: context.getString(R.string.error_unknown))
        }
    }
}

// ─── Side Effects ──────────────────────────────────────────────────────────

/**
 * One-shot side-effect events emitted by [AnalysisViewModel].
 *
 * These are consumed by the UI layer to trigger navigation or other
 * non-state actions that should only happen once.
 */
sealed interface AnalysisSideEffect {
    /** Command the UI to launch the system camera for a retake. */
    data object Retake : AnalysisSideEffect

    /** Command the UI to navigate to a recipe detail page. */
    data class NavigateToRecipe(val recipeId: String) : AnalysisSideEffect
}

// ─── Private Extensions ────────────────────────────────────────────────────

/**
 * Determine whether a fruit/vegetable category is cookable.
 *
 * v1 model categories mapped to the v2 isCookable concept:
 * vegetables → true (can be cooked), fruits → false (usually eaten raw).
 */
private fun FruitCategory.isCookable(): Boolean = when (this) {
    FruitCategory.APPLE -> false
    FruitCategory.BANANA -> false
    FruitCategory.ORANGE -> false
    FruitCategory.BITTER_GOURD -> true
    FruitCategory.CAPSICUM -> true
    FruitCategory.CUCUMBER -> true
    FruitCategory.OKRA -> true
    FruitCategory.POTATO -> true
    FruitCategory.TOMATO -> true
    FruitCategory.UNKNOWN -> false
}
