package com.example.freshscan.ui.screen.recipe

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.freshscan.domain.model.CookingStep
import com.example.freshscan.domain.model.RecipeCategory
import com.example.freshscan.domain.model.RecipeDifficulty

/**
 * v2.0 Recipe detail screen.
 *
 * Displays full recipe information: title, meta info (category/difficulty/time),
 * complete ingredient list, and ordered cooking steps with built-in timer support.
 *
 * Actions: favorite toggle, add ingredients to shopping list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToShoppingList: () -> Unit,
    scannedIngredients: Set<String> = emptySet(),
    viewModel: RecipeDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.recipe?.title ?: "菜谱详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // Favorite toggle
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (uiState.isFavorite)
                                Icons.Filled.Favorite
                            else
                                Icons.Filled.FavoriteBorder,
                            contentDescription = if (uiState.isFavorite) "取消收藏" else "收藏",
                            tint = if (uiState.isFavorite)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        when {
            // Loading state
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Recipe not found
            uiState.recipe == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.LocalDining,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "菜谱未找到",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Recipe loaded
            else -> {
                val recipe = uiState.recipe!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    // ── Hero image placeholder ──
                    HeroSection(recipe = recipe)

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Meta info bar ──
                    MetaInfoBar(recipe = recipe)

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Ingredients section ──
                    IngredientsSection(
                        ingredients = recipe.allIngredients,
                        ownedNames = scannedIngredients
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Cooking steps with timer ──
                    StepsSection(
                        steps = recipe.steps,
                        activeTimerStep = uiState.activeTimerStep,
                        timerState = uiState.timerState,
                        timerRemainingSec = uiState.timerRemainingSec,
                        completedSteps = uiState.completedSteps,
                        onStartTimer = viewModel::startTimer,
                        onPauseTimer = viewModel::pauseTimer,
                        onResumeTimer = viewModel::resumeTimer,
                        onResetTimer = viewModel::resetTimer,
                        onToggleStep = viewModel::toggleStepComplete
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Tips section ──
                    if (recipe.tips.isNotBlank()) {
                        TipsSection(tip = recipe.tips)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // ── Nutrition info ──
                    NutritionSection(nutrition = recipe.nutrition)

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Action buttons ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.addToShoppingList() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("加入购物清单")
                        }

                        Button(
                            onClick = { onNavigateToShoppingList() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("查看购物清单")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Tags ──
                    if (recipe.tags.isNotEmpty()) {
                        TagsSection(tags = recipe.tags)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// ─── Subcomponents ───────────────────────────────────────────────────────────

/**
 * Hero image section — loads recipe image from assets with title overlay.
 *
 * Falls back to icon placeholder if the image asset is missing or empty.
 */
@Composable
private fun HeroSection(
    recipe: com.example.freshscan.domain.model.Recipe,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasImage = recipe.imageAsset.isNotBlank()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        if (hasImage) {
            // Load recipe hero image from assets
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/recipes/${recipe.imageAsset}")
                    .crossfade(true)
                    .build(),
                contentDescription = recipe.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Gradient overlay at bottom for title readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )

            // Title overlaid on image
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            // Fallback placeholder when no image asset is available
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.RestaurantMenu,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

/**
 * Meta information bar: category chip, difficulty, and cooking time.
 */
@Composable
private fun MetaInfoBar(
    recipe: com.example.freshscan.domain.model.Recipe,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category chip
        CategoryChip(category = recipe.category)

        Spacer(modifier = Modifier.weight(1f))

        // Difficulty indicator
        Text(
            text = when (recipe.difficulty) {
                RecipeDifficulty.EASY -> "⭐ 简单"
                RecipeDifficulty.MEDIUM -> "⭐⭐ 中等"
                RecipeDifficulty.HARD -> "⭐⭐⭐ 困难"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Cooking time
        Icon(
            imageVector = Icons.Filled.AccessTime,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${recipe.cookingTimeMin}分钟",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Small chip displaying the recipe category.
 */
@Composable
private fun CategoryChip(
    category: RecipeCategory,
    modifier: Modifier = Modifier
) {
    val bgColor = when (category) {
        RecipeCategory.HOME -> Color(0xFFE8F5E9)
        RecipeCategory.QUICK -> Color(0xFFFFF3E0)
        RecipeCategory.DIET -> Color(0xFFE3F2FD)
        RecipeCategory.SOUP -> Color(0xFFFFF8E1)
        RecipeCategory.COLD -> Color(0xFFF3E5F5)
    }
    val textColor = when (category) {
        RecipeCategory.HOME -> Color(0xFF2E7D32)
        RecipeCategory.QUICK -> Color(0xFFE65100)
        RecipeCategory.DIET -> Color(0xFF1565C0)
        RecipeCategory.SOUP -> Color(0xFFF9A825)
        RecipeCategory.COLD -> Color(0xFF7B1FA2)
    }

    Text(
        text = category.displayName,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/**
 * Full ingredient list section with owned/missing markers.
 *
 * @param ingredients The complete ingredient list for the recipe.
 * @param ownedNames Set of ingredient names that the user already has (from scan).
 *                   Green ✅ for owned, gray ⬜ for missing. If empty, all shown neutral.
 */
@Composable
private fun IngredientsSection(
    ingredients: List<com.example.freshscan.domain.model.Ingredient>,
    ownedNames: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "食材清单", count = "${ingredients.size}项")
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ingredients.forEach { ingredient ->
                val isOwned = ownedNames.isEmpty() || ownedNames.any { owned ->
                    owned.contains(ingredient.name) || ingredient.name.contains(owned)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status icon + name
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (ownedNames.isEmpty()) "•" else if (isOwned) "✅" else "⬜",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = ingredient.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ownedNames.isEmpty())
                                MaterialTheme.colorScheme.onSurface
                            else if (isOwned)
                                Color(0xFF2E7D32)  // green = owned
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    // Amount
                    Text(
                        text = ingredient.amount,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ownedNames.isEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else if (isOwned)
                            Color(0xFF2E7D32).copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

/**
 * Cooking steps section with timer controls.
 */
@Composable
private fun StepsSection(
    steps: List<CookingStep>,
    activeTimerStep: Int?,
    timerState: TimerState,
    timerRemainingSec: Int,
    completedSteps: Set<Int>,
    onStartTimer: (Int, Int) -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onToggleStep: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "烹饪步骤", count = "共${steps.size}步")
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            steps.forEach { step ->
                StepCard(
                    step = step,
                    isActiveTimer = activeTimerStep == step.order,
                    timerState = timerState,
                    timerRemainingSec = timerRemainingSec,
                    isCompleted = step.order in completedSteps,
                    onStartTimer = { onStartTimer(step.order, step.timerSec) },
                    onPauseTimer = onPauseTimer,
                    onResumeTimer = onResumeTimer,
                    onResetTimer = onResetTimer,
                    onToggleStep = { onToggleStep(step.order) }
                )
            }
        }
    }
}

/**
 * Single cooking step card with optional timer controls.
 */
@Composable
private fun StepCard(
    step: CookingStep,
    isActiveTimer: Boolean,
    timerState: TimerState,
    timerRemainingSec: Int,
    isCompleted: Boolean,
    onStartTimer: () -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onToggleStep: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isCompleted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            isActiveTimer -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        label = "stepBg"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onToggleStep() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Step number / completion indicator
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (isCompleted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = "${step.order}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Step text + timer info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCompleted)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurface
            )

            // Show remaining time if this is the active timer
            if (isActiveTimer && timerState != TimerState.IDLE) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTime(timerRemainingSec),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (timerState) {
                        TimerState.DONE -> Color(0xFF4CAF50)
                        TimerState.PAUSED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Timer controls
        if (step.timerSec > 0) {
            when {
                // Not the active timer, not completed → show start button
                !isActiveTimer && !isCompleted -> {
                    IconButton(
                        onClick = onStartTimer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = "开始计时",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Active timer running → show pause
                isActiveTimer && timerState == TimerState.RUNNING -> {
                    IconButton(
                        onClick = onPauseTimer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = "暂停",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // Active timer paused → show resume + stop
                isActiveTimer && timerState == TimerState.PAUSED -> {
                    Row {
                        IconButton(
                            onClick = onResumeTimer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "继续",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        IconButton(
                            onClick = onResetTimer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "停止",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Timer done → show checkmark, no action needed
                isActiveTimer && timerState == TimerState.DONE -> {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "已完成",
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

/**
 * Cooking tips section.
 */
@Composable
private fun TipsSection(
    tip: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "烹饪小贴士")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "💡 $tip",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))
                .padding(12.dp)
        )
    }
}

/**
 * Nutritional information section.
 */
@Composable
private fun NutritionSection(
    nutrition: com.example.freshscan.domain.model.Nutrition,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "营养成分（每份）")
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NutritionItem("热量", "${nutrition.calories} kcal")
            NutritionItem("蛋白", "${nutrition.protein}g")
            NutritionItem("碳水", "${nutrition.carbs}g")
            NutritionItem("脂肪", "${nutrition.fat}g")
            NutritionItem("纤维", "${nutrition.fiber}g")
        }
    }
}

/**
 * Single nutrition stat item.
 */
@Composable
private fun NutritionItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Tags display section.
 */
@Composable
private fun TagsSection(
    tags: List<String>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.forEach { tag ->
            Text(
                text = "#$tag",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Section header with optional count badge.
 */
@Composable
private fun SectionHeader(
    title: String,
    count: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (count != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Utilities ──────────────────────────────────────────────────────────────

/**
 * Format seconds to mm:ss display format.
 */
private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
