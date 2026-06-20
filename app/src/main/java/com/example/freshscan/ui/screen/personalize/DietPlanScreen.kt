package com.example.freshscan.ui.screen.personalize

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.freshscan.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietPlanScreen(
    onNavigateBack: () -> Unit,
    onNavigateToShoppingList: () -> Unit,
    viewModel: DietPlanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navigateToShoppingList.collect {
            onNavigateToShoppingList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("饮食计划") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (uiState is DietPlanUiState.Success) {
                        IconButton(onClick = {
                            viewModel.addAllToShoppingList()
                        }) {
                            Icon(Icons.Filled.ShoppingCart, "全部加入购物清单")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is DietPlanUiState.Idle -> {}
            is DietPlanUiState.Generating -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("AI 正在为你定制一周饮食计划...")
                    }
                }
            }
            is DietPlanUiState.Success -> {
                DietPlanContent(
                    plan = state.plan,
                    selectedDayIndex = state.selectedDayIndex,
                    onDaySelected = { viewModel.selectDay(it) },
                    onAddMeal = { viewModel.addMealToShoppingList(it) },
                    modifier = Modifier.padding(padding)
                )
            }
            is DietPlanUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.generatePlan() }) { Text("重试") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DietPlanContent(
    plan: DietPlan,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit,
    onAddMeal: (Meal) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "日均热量：${plan.totalCaloriesAvg} kcal",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(plan.nutritionSummary, style = MaterialTheme.typography.bodySmall)
            }
        }

        // Day tabs
        ScrollableTabRow(selectedTabIndex = selectedDayIndex, modifier = Modifier.fillMaxWidth()) {
            plan.dailyPlans.forEachIndexed { index, day ->
                Tab(
                    selected = selectedDayIndex == index,
                    onClick = { onDaySelected(index) },
                    text = { Text(day.dayLabel) }
                )
            }
        }

        // Selected day content
        val selectedDay = plan.dailyPlans.getOrNull(selectedDayIndex) ?: return
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${selectedDay.dayLabel} · 总热量 ${selectedDay.totalCalories} kcal",
                style = MaterialTheme.typography.titleSmall
            )
            selectedDay.notes?.let {
                Text("$it", style = MaterialTheme.typography.bodySmall)
            }

            selectedDay.meals.forEach { meal ->
                MealCard(meal = meal, onAddToShoppingList = { onAddMeal(meal) })
            }
        }
    }
}

@Composable
private fun MealCard(meal: Meal, onAddToShoppingList: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.type.label, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    meal.recipe.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text("${meal.recipe.cookingTimeMin}min", style = MaterialTheme.typography.bodySmall)
                Text(" · ", style = MaterialTheme.typography.bodySmall)
                Text("${meal.recipe.calories}kcal", style = MaterialTheme.typography.bodySmall)
                Text(
                    " · P:${meal.recipe.proteinG}g C:${meal.recipe.carbsG}g F:${meal.recipe.fatG}g",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Ingredients
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                meal.recipe.ingredients.joinToString(" · ") { "${it.name}${it.amount}" },
                style = MaterialTheme.typography.bodySmall
            )

            // Expandable steps
            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                meal.recipe.steps.forEachIndexed { i, step ->
                    Text("${i + 1}. $step", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Nutrition row
                Spacer(modifier = Modifier.height(8.dp))
                NutritionRow(meal.recipe)

                // Add to shopping list button
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = onAddToShoppingList,
                    label = { Text("加入购物清单") }
                )
            }
        }
    }
}

@Composable
private fun NutritionRow(recipe: DietRecipe) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        NutritionChip("热量", "${recipe.calories}kcal")
        NutritionChip("蛋白质", "${recipe.proteinG}g")
        NutritionChip("碳水", "${recipe.carbsG}g")
        NutritionChip("脂肪", "${recipe.fatG}g")
    }
}

@Composable
private fun NutritionChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
