package com.example.freshscan.ui.screen.personalize

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.freshscan.domain.model.MealSuggestion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealQueryScreen(
    onNavigateBack: () -> Unit,
    viewModel: MealQueryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle(initialValue = emptyList())
    var queryText by rememberSaveable { mutableStateOf("") }

    BottomSheetScaffold(
        scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberStandardBottomSheetState(
                initialValue = SheetValue.PartiallyExpanded
            )
        ),
        sheetPeekHeight = 120.dp,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        topBar = {
            TopAppBar(
                title = { Text("AI 膳食推荐") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        sheetContent = {
            // ── History Bottom Sheet ──
            Column(modifier = Modifier.fillMaxWidth()) {
                // Drag handle
                BottomSheetDefaults.DragHandle(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                // Sheet header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "历史记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (history.isNotEmpty()) {
                        Text(
                            "(${history.size})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { viewModel.clearHistory() }) {
                            Text("清空", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无历史记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history, key = { it.id }) { item ->
                            HistoryItemCard(
                                suggestion = item,
                                onDelete = { viewModel.deleteHistoryItem(item.id) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    ) { padding ->
        // ── Main content: Query + Result ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Query area
            QueryArea(
                queryText = queryText,
                onQueryChange = { queryText = it },
                onQuery = {
                    if (queryText.isNotBlank()) {
                        viewModel.queryMeal(queryText.trim())
                    }
                },
                isQuerying = uiState is MealQueryUiState.Querying
            )

            // Result area
            when (val state = uiState) {
                is MealQueryUiState.Querying -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("AI 正在推荐菜品...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is MealQueryUiState.Result -> {
                    ResultCard(
                        suggestion = state.suggestion,
                        onAddToShoppingList = { viewModel.addToShoppingList(state.suggestion) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                is MealQueryUiState.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                state.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = {
                                if (queryText.isNotBlank()) viewModel.queryMeal(queryText.trim())
                            }) {
                                Text("重试")
                            }
                        }
                    }
                }
                is MealQueryUiState.Idle -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QueryArea(
    queryText: String,
    onQueryChange: (String) -> Unit,
    onQuery: () -> Unit,
    isQuerying: Boolean
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = queryText,
            onValueChange = onQueryChange,
            label = { Text("想吃点什么？") },
            placeholder = { Text("例如：减脂午餐、高蛋白晚餐、清淡的汤...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isQuerying
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quick tags
            val quickTags = listOf("减脂午餐", "高蛋白晚餐", "快手早餐", "清淡素食")
            quickTags.forEach { tag ->
                SuggestionChip(
                    onClick = {
                        onQueryChange(tag)
                    },
                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onQuery,
            modifier = Modifier.fillMaxWidth(),
            enabled = queryText.isNotBlank() && !isQuerying
        ) {
            if (isQuerying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("AI 推荐")
        }
    }
}

@Composable
private fun ResultCard(
    suggestion: MealSuggestion,
    onAddToShoppingList: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(suggestion.id) { mutableStateOf(true) }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    suggestion.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = onAddToShoppingList,
                    label = { Text("加入购物") },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "查询：${suggestion.query}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Nutrition chips
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NutritionChip("${suggestion.calories}kcal", "热量")
                NutritionChip("${suggestion.proteinG}g", "蛋白质")
                NutritionChip("${suggestion.carbsG}g", "碳水")
                NutritionChip("${suggestion.fatG}g", "脂肪")
                NutritionChip("${suggestion.cookingTimeMin}min", "时间")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Ingredients
            Text("食材", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                suggestion.ingredients.joinToString("、") { "${it.name} ${it.amount}" },
                style = MaterialTheme.typography.bodyMedium
            )

            // Expandable steps
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起步骤" else "展开步骤")
            }

            AnimatedVisibility(visible = expanded && suggestion.steps.isNotEmpty()) {
                Column {
                    suggestion.steps.forEachIndexed { i, step ->
                        Text(
                            "${i + 1}. $step",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    suggestion: MealSuggestion,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        suggestion.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${suggestion.query} · ${suggestion.calories}kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        "食材：${suggestion.ingredients.joinToString("、") { "${it.name}${it.amount}" }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (suggestion.steps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        suggestion.steps.forEachIndexed { i, step ->
                            Text("${i + 1}. $step", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "烹饪${suggestion.cookingTimeMin}分钟 · P:${suggestion.proteinG}g C:${suggestion.carbsG}g F:${suggestion.fatG}g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NutritionChip(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
