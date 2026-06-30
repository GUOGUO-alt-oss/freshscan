package com.example.freshscan.ui.screen.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.freshscan.domain.model.CollectedProduce
import com.example.freshscan.domain.model.HistoryItem
import com.example.freshscan.domain.model.Recipe
import com.example.freshscan.ui.theme.LocalSemanticColors
import com.example.freshscan.ui.theme.SerifFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v4.2 Home screen — redesigned to feature produce collection and tonight's meal.
 *
 * Layout (top to bottom):
 * 1. TopAppBar: "🍃 鲜识" in Serif + FridgeMiniIndicator on right
 * 2. HeroCard (120dp) — scan CTA
 * 3. SeasonalTipCard (when data available)
 * 4. LastScanCardV2 (when history non-empty)
 * 5. CollectionSection — progress bar + horizontal scroll cards
 * 6. TonightMealSection — fridge-based recipe recommendations Top 3
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAnalysis: () -> Unit,
    onNavigateToRecipe: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToFridge: () -> Unit,
    onNavigateToCollection: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val fridgeCount by viewModel.fridgeCount.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                HomeSideEffect.LaunchCamera -> onNavigateToAnalysis()
                is HomeSideEffect.NavigateToRecipe -> onNavigateToRecipe(effect.recipeId)
                HomeSideEffect.NavigateToCollection -> onNavigateToCollection()
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, actionLabel = "重试", duration = SnackbarDuration.Long)
            viewModel.refresh()
            viewModel.clearError()
        }
    }

    val hasHistory = uiState.lastScanItems.isNotEmpty()
    val hasCollection = uiState.collectionCount > 0

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🍃 鲜识",
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    FridgeMiniIndicator(count = fridgeCount, onClick = onNavigateToFridge)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
            )
        },
        floatingActionButton = {
            FilledTonalButton(
                onClick = { viewModel.onScanClicked() },
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.CameraAlt, "拍照识别", Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("扫描食材")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero card
            item {
                HeroCardV2(onClick = { viewModel.onScanClicked() })
            }

            // Seasonal tip
            uiState.seasonalTip?.let { tip ->
                item { SeasonalTipCard(tip) }
            }

            // Last scan (only when history non-empty)
            if (hasHistory) {
                item {
                    LastScanCardV2(
                        items = uiState.lastScanItems,
                        timestamp = uiState.lastScanTime!!,
                        onClick = onNavigateToHistory
                    )
                }
            }

            // ─── Produce Collection Section (always visible) ───
            item {
                CollectionSection(
                    items = uiState.collectionItems,
                    count = uiState.collectionCount,
                    onViewAll = { viewModel.onViewCollectionClicked() }
                )
            }

            // ─── Tonight's Meal Section ───
            item {
                TonightMealSection(
                    recipes = uiState.tonightRecipes,
                    loading = uiState.tonightLoading,
                    fridgeCount = fridgeCount,
                    onRecipeClick = { viewModel.onTonightRecipeClicked(it.id) },
                    onNavigateToFridge = onNavigateToFridge
                )
            }

            // Bottom spacer for FAB
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

// ─── Reused Components ───────────────────────────────────────────────────────

@Composable
private fun FridgeMiniIndicator(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Kitchen, "冰箱", Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text("${count}件", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HeroCardV2(onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(120.dp),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CameraAlt, null, Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("AI 识别", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text("扫描食材 · 新鲜检测 · 即时推荐", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LastScanCardV2(items: List<HistoryItem>, timestamp: Long, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = items.joinToString(" · ") {
                        "${it.freshnessLevel.emoji} ${it.fruitCategory.displayName}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(formatRelativeTime(timestamp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Text("▸", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun SeasonalTipCard(tip: SeasonalTip) {
    Card(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tip.emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("${tip.month}月应季食材 · ${tip.name}正当季！",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(tip.tip, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ─── v4.2: Produce Collection Section ────────────────────────────────────────

@Composable
private fun CollectionSection(
    items: List<CollectedProduce>,
    count: Int,
    onViewAll: () -> Unit
) {
    val semanticColors = LocalSemanticColors.current

    Column {
        // Section header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("📚 果蔬图鉴", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text("已解锁 $count/260 种", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(8.dp))

        // "查看图鉴" button card
        OutlinedCard(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🍎", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text("${items.size} 种已解锁 · 探索全部果蔬",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                Text("查看图鉴 ▸", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onViewAll))
            }
        }

        Spacer(Modifier.height(8.dp))

        if (items.isEmpty()) {
            // Empty collection — encourage scanning
            OutlinedCard(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🔍", fontSize = 28.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("扫描食材解锁图鉴", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text("每识别一种新果蔬，图鉴就会点亮一张卡片\n共计260种等你来收集", 
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items.take(12), key = { it.label }) { produce ->
                    CollectedProduceCard(produce = produce)
                }
                // Show locked placeholders if fewer than 10 collected
                val lockedCount = (10 - items.size).coerceAtLeast(0)
                if (lockedCount > 0) {
                    items(lockedCount) { LockedPlaceholderCard() }
                }
            }
        }
    }
}

@Composable
private fun CollectedProduceCard(produce: CollectedProduce) {
    val semanticColors = LocalSemanticColors.current
    val borderColor = if (produce.isRare) Color(0xFFFFC107) else Color.Transparent

    Card(
        modifier = Modifier.width(80.dp),
        shape = RoundedCornerShape(12.dp),
        border = if (produce.isRare) BorderStroke(2.dp, borderColor) else null
    ) {
        Column(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emoji or category icon
            Box(
                Modifier.size(40.dp).background(
                    semanticColors.freshnessHigh.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val emoji = when {
                    produce.category.contains("水果") -> "🍎"
                    produce.category.contains("蔬菜") -> "🥬"
                    produce.category.contains("菌菇") -> "🍄"
                    else -> "🌱"
                }
                Text(emoji, fontSize = 20.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(produce.displayName, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LockedPlaceholderCard() {
    Card(
        modifier = Modifier.width(80.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(40.dp).background(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("?", fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(4.dp))
            Text("???", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
        }
    }
}

// ─── v4.2: Tonight's Meal Section ────────────────────────────────────────────

@Composable
private fun TonightMealSection(
    recipes: List<Recipe>,
    loading: Boolean,
    fridgeCount: Int,
    onRecipeClick: (Recipe) -> Unit,
    onNavigateToFridge: () -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🍳 今晚吃什么", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text("基于冰箱库存", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(8.dp))

        when {
            loading -> {
                OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("正在分析冰箱库存...", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            fridgeCount == 0 -> {
                OutlinedCard(
                    onClick = onNavigateToFridge,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🧊", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("冰箱还是空的", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("添加食材到冰箱，获取智能推荐", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            recipes.isEmpty() -> {
                OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🧊", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Text("冰箱食材暂无匹配菜谱，试试扫描更多食材", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> {
                recipes.forEach { recipe ->
                    TonightRecipeCard(recipe = recipe, onClick = { onRecipeClick(recipe) })
                }
            }
        }
    }
}

@Composable
private fun TonightRecipeCard(recipe: Recipe, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Thumbnail
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("file:///android_asset/recipes/${recipe.thumbnailAsset}")
                    .crossfade(true).build(),
                contentDescription = recipe.title,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(recipe.title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(2.dp))
                    Text("${recipe.cookingTimeMin}min", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("${recipe.nutrition.calories}kcal", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(recipe.category.displayName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ─── Utilities ───────────────────────────────────────────────────────────────

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3600_000}小时前"
        else -> {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
