package com.example.freshscan.ui.screen.analysis

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.freshscan.R
import com.example.freshscan.domain.model.DetectedItem
import com.example.freshscan.domain.model.FreshnessLevel
import com.example.freshscan.domain.model.Recipe
import com.example.freshscan.ui.components.ParticleScan
import com.example.freshscan.ui.components.ProduceInfoSheet
import java.io.File

/**
 * v2.0 Analysis screen — photo capture → AI analysis → results display.
 *
 * Lifecycle:
 * 1. Auto-launches system camera on Idle state
 * 2. Shows loading/animating states during inference
 * 3. Displays detected items in Results, or guidance in Empty/Error
 *
 * Full state machine: docs/06-详细设计文档-v2.md §4.1
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRecipe: (String) -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedItemInfo by viewModel.selectedItemInfo.collectAsStateWithLifecycle()
    val isInfoLoading by viewModel.isInfoLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Hold the URI that was passed to the camera launcher.
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // System camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingPhotoUri
        if (success && uri != null) {
            viewModel.startAnalysis(uri)
        } else {
            // User cancelled the camera — return to home
            onNavigateBack()
        }
    }

    // Consume one-shot side effects from the ViewModel
    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is AnalysisSideEffect.Retake -> {
                    // Will be handled by the screenState-based LaunchedEffect below
                }
                is AnalysisSideEffect.NavigateToRecipe -> {
                    onNavigateToRecipe(effect.recipeId)
                }
            }
        }
    }

    // Auto-launch camera whenever the screen enters Idle state.
    // Keyed on screenState so it re-fires on "retake" transitions back to Idle.
    var cameraLaunchAttempts by remember { mutableStateOf(0) }
    LaunchedEffect(uiState.screenState) {
        if (uiState.screenState == AnalysisScreenState.Idle && cameraLaunchAttempts < 3) {
            cameraLaunchAttempts++
            // Clean up leftover capture temp files before creating a new one
            context.cacheDir.listFiles { f -> f.name.startsWith("freshscan_capture_") && f.name.endsWith(".jpg") }
                ?.forEach { it.delete() }
            val file = File(
                context.cacheDir,
                "freshscan_capture_${System.currentTimeMillis()}.jpg"
            ).apply { parentFile?.mkdirs() }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            pendingPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    LaunchedEffect(uiState.screenState) {
        if (uiState.screenState != AnalysisScreenState.Idle) {
            cameraLaunchAttempts = 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analysis_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState.screenState) {

                // ── Idle: waiting for camera to launch ──
                AnalysisScreenState.Idle -> {
                    Text(
                        text = stringResource(R.string.analysis_launching_camera),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // ── Loading: first-time model initialization ──
                AnalysisScreenState.Loading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.analysis_loading_ai),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── Animating: particle scan animation + inference ──
                AnalysisScreenState.Animating -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Particle scan animation (full screen, ~50 particles at 60fps)
                        ParticleScan(
                            isActive = true,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Text overlay on top of particles
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.analysis_ai_analyzing),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.analysis_identifying),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // ── Results: BottomSheet shown as overlay ──
                is AnalysisScreenState.Results -> {
                    // Dimmed background while BottomSheet is visible
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.15f))
                    ) {
                        Text(
                            text = stringResource(R.string.analysis_detected_items, uiState.items.size),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 24.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // ── Empty: no detections ──
                AnalysisScreenState.Empty -> {
                    EmptyContent(
                        onRetry = { viewModel.retry() },
                        onRetake = { viewModel.retake() }
                    )
                }

                // ── Error: analysis failed ──
                is AnalysisScreenState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.retry() },
                        onRetake = { viewModel.dismissError() }
                    )
                }
            }
        }
    }

    // ── BottomSheet overlay (only when results are ready) ──
    if (uiState.screenState is AnalysisScreenState.Results) {
        AnalysisBottomSheet(
            sheetState = uiState.sheetState,
            items = uiState.items,
            recipes = uiState.recipes,
            selectedItemInfo = selectedItemInfo,
            isInfoLoading = isInfoLoading,
            onRecipeClick = onNavigateToRecipe,
            onRetake = { viewModel.retake() },
            onStateChange = { viewModel.setSheetState(it) },
            onFindRecipes = { viewModel.findRecipes() },
            onItemClicked = { viewModel.onItemClicked(it) },
            onClearSelectedItem = { viewModel.clearSelectedItem() },
            onRetryAI = { viewModel.retryAIExtension() },
            onDismiss = onNavigateBack
        )
    }
}

// ─── BottomSheet ────────────────────────────────────────────────────────────

/**
 * ModalBottomSheet with 3 visual states (COLLAPSED / HALF / FULL).
 *
 * Material3 [ModalBottomSheet] natively supports two visible states:
 * - **PartiallyExpanded** (~half screen) → maps to [SheetState.COLLAPSED]
 * - **Expanded** (full height) → maps to [SheetState.HALF]
 *
 * **FULL** is achieved when the inner [LazyColumn] content scrolls beyond
 * the sheet's natural height — the user scrolls within the list to reveal
 * more items (typically recipes).
 *
 * CRITICAL: Do NOT attach a custom [NestedScrollConnection] to the sheet.
 * ModalBottomSheet has its own internal nested-scroll logic that correctly
 * delegates between sheet-drag and content-scroll. Adding an external
 * connection BREAKS drag gestures entirely.
 *
 * See: 06-详细设计文档-v2.md §7.2
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisBottomSheet(
    sheetState: SheetState,
    items: List<DetectedItem>,
    recipes: List<Recipe>,
    selectedItemInfo: com.example.freshscan.domain.model.ProduceInfo?,
    isInfoLoading: Boolean,
    onRecipeClick: (String) -> Unit,
    onRetake: () -> Unit,
    onStateChange: (SheetState) -> Unit,
    onFindRecipes: () -> Unit,
    onItemClicked: (DetectedItem) -> Unit,
    onClearSelectedItem: () -> Unit,
    onRetryAI: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modalSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false  // allow PartiallyExpanded (COLLAPSED) state
    )

    // Keep ViewModel in sync with ModalBottomSheet's internal state
    // so the SheetState in AnalysisUiState reflects reality
    LaunchedEffect(modalSheetState.currentValue) {
        val mapped = when (modalSheetState.currentValue) {
            SheetValue.Hidden -> SheetState.COLLAPSED
            SheetValue.PartiallyExpanded -> SheetState.COLLAPSED
            SheetValue.Expanded -> SheetState.HALF
        }
        onStateChange(mapped)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalSheetState,
        // NO nestedScroll modifier — let ModalBottomSheet handle gestures internally
        dragHandle = { /* default drag handle */ }
    ) {
        if (selectedItemInfo != null) {
            // ── Produce Info Sheet (shown when user taps a detected item) ──
            ProduceInfoSheet(
                info = selectedItemInfo!!,
                isAIExtensionLoading = isInfoLoading,
                onBack = onClearSelectedItem,
                onRetryAI = onRetryAI,
                modifier = Modifier.navigationBarsPadding()
            )
        } else {
            // ── Normal results content ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    // Minimum height ensures the sheet is tall enough to trigger
                    // drag gestures. Without this, short content won't differentiate
                    // between COLLAPSED and HALF states.
                    .defaultMinSize(minHeight = 400.dp)
            ) {
                // ── Header: item count + freshness summary ──
                ResultsHeader(
                    items = items,
                    onFindRecipes = onFindRecipes,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── Detected items list ──
                // When items are few, we still want the LazyColumn to take space
                // so drag gestures have surface area to work with.
                if (items.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 4.dp
                        )
                    ) {
                        items(items, key = { it.id }) { item ->
                            DetectedItemCard(
                                item = item,
                                onClick = { onItemClicked(item) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Recipe section (if any recipes loaded) ──
                if (recipes.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.analysis_recipe_count, recipes.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 4.dp
                        )
                    ) {
                        items(recipes, key = { it.id }) { recipe ->
                            RecipeCard(recipe = recipe, onClick = { onRecipeClick(recipe.id) })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Action buttons (always visible at bottom) ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetake,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.analysis_retake_photo))
                    }
                    Button(
                        onClick = onFindRecipes,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.analysis_find_recipes))
                    }
                }
            }
        }
    }
}

/**
 * Result header shown at the top of the BottomSheet in all states.
 */
@Composable
private fun ResultsHeader(
    items: List<DetectedItem>,
    onFindRecipes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val freshCount = items.count { it.freshnessLevel == FreshnessLevel.FRESH }
    val rottenCount = items.count { it.freshnessLevel == FreshnessLevel.ROTTEN }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.analysis_detected_items, items.size),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (freshCount > 0) {
                    Text(
                        text = stringResource(R.string.analysis_fresh_count, freshCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (rottenCount > 0) {
                    Text(
                        text = stringResource(R.string.analysis_rotten_count, rottenCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Recipe card with thumbnail image, title, difficulty, and cooking time.
 */
@Composable
private fun RecipeCard(
    recipe: Recipe,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail image (or placeholder icon)
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            if (recipe.thumbnailAsset.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("file:///android_asset/recipes/${recipe.thumbnailAsset}")
                        .crossfade(true)
                        .build(),
                    contentDescription = recipe.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.RestaurantMenu,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when (recipe.difficulty) {
                    com.example.freshscan.domain.model.RecipeDifficulty.EASY -> stringResource(R.string.difficulty_easy)
                    com.example.freshscan.domain.model.RecipeDifficulty.MEDIUM -> stringResource(R.string.difficulty_medium)
                    com.example.freshscan.domain.model.RecipeDifficulty.HARD -> stringResource(R.string.difficulty_hard)
                } + " · ${recipe.cookingTimeMin}分钟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Single detected item card showing category, freshness, and confidence.
 */
@Composable
private fun DetectedItemCard(
    item: DetectedItem,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick?.invoke() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Freshness emoji indicator
        Text(
            text = item.freshnessLevel.emoji,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${item.freshnessLevel.displayName} · 置信度 ${(item.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Empty state: no fruits/vegetables detected.
 */
@Composable
private fun EmptyContent(
    onRetry: () -> Unit,
    onRetake: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "😕",
            style = MaterialTheme.typography.displayMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.analysis_not_detected),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.analysis_adjust_angle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetake) {
                Text(stringResource(R.string.analysis_retake_photo))
            }
            Button(onClick = onRetry) {
                Text(stringResource(R.string.analysis_retry_analysis))
            }
        }
    }
}

/**
 * Error state: analysis failed with an error message.
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onRetake: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "⚠️",
            style = MaterialTheme.typography.displayMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetake) {
                Text(stringResource(R.string.analysis_retake_photo))
            }
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}
