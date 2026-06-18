package com.example.freshscan.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.freshscan.domain.model.RecipeCategory

/**
 * v2.0 Taste Profile screen.
 *
 * Allows users to set dietary preferences:
 * - Spice tolerance (0-3 slider)
 * - Salt / Oil levels (3-option toggle)
 * - Excluded ingredients (multi-select FilterChip)
 * - Preferred recipe categories (multi-select FilterChip)
 *
 * Persisted via DataStore Preferences in [TasteProfileViewModel].
 *
 * Wireframe: docs/03-UI设计规格-v2.md §4.5
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TasteProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: TasteProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar on successful save
    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) {
            snackbarHostState.showSnackbar("口味偏好已更新")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("口味档案") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (uiState.isDirty) {
                        Button(
                            onClick = { viewModel.save() },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("保存")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ─── Spice Level ───
            SectionHeader(title = "辣度偏好")
            SpiceSlider(
                value = uiState.spiceLevel,
                onValueChange = { viewModel.updateSpiceLevel(it) },
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // ─── Salt Level ───
            SectionHeader(title = "盐度偏好")
            TripleOptionRow(
                options = listOf("少盐", "正常", "偏咸"),
                selectedIndex = uiState.saltLevel,
                onSelect = { viewModel.updateSaltLevel(it) },
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ─── Oil Level ───
            SectionHeader(title = "油量偏好")
            TripleOptionRow(
                options = listOf("少油", "正常", "偏油"),
                selectedIndex = uiState.oilLevel,
                onSelect = { viewModel.updateOilLevel(it) },
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // ─── Excluded Ingredients ───
            SectionHeader(title = "饮食忌口")
            ExcludedIngredientsSection(
                selectedIngredients = uiState.excludedIngredients,
                onToggle = { viewModel.toggleExcludedIngredient(it) },
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // ─── Preferred Categories ───
            SectionHeader(title = "菜谱偏好")
            PreferredCategoriesSection(
                selectedCategories = uiState.preferredCategories,
                onToggle = { viewModel.togglePreferredCategory(it) },
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Reset to defaults ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = { /* TODO: reset to defaults in VM */ }
                ) {
                    Text("恢复默认")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─── Section Composables ─────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
    )
}

/**
 * Slider for spice tolerance (0-3).
 * Labels: 不辣 / 微辣 / 中辣 / 超辣
 */
@Composable
private fun SpiceSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = listOf("不辣", "微辣", "中辣", "超辣")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == value)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..3f,
            steps = 2, // 4 discrete values → 3 dividers
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Three-option toggle row (e.g., salt: 少盐/正常/偏咸).
 */
@Composable
private fun TripleOptionRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Multi-select FilterChip grid for excluded ingredients.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExcludedIngredientsSection(
    selectedIngredients: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val allIngredients = listOf("花生", "乳糖", "海鲜", "麸质", "坚果", "大豆")

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        allIngredients.forEach { ingredient ->
            FilterChip(
                selected = selectedIngredients.contains(ingredient),
                onClick = { onToggle(ingredient) },
                label = { Text(ingredient) }
            )
        }
    }
}

/**
 * Multi-select FilterChip grid for preferred recipe categories.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferredCategoriesSection(
    selectedCategories: Set<RecipeCategory>,
    onToggle: (RecipeCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RecipeCategory.entries.forEach { category ->
            FilterChip(
                selected = selectedCategories.contains(category),
                onClick = { onToggle(category) },
                label = { Text(category.displayName) }
            )
        }
    }
}

