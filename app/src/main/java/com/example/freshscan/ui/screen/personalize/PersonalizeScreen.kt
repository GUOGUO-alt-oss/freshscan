package com.example.freshscan.ui.screen.personalize

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.freshscan.domain.model.*

/**
 * v3.0 Personalize screen.
 *
 * Full Material3 form: Scaffold + TopAppBar + scrollable sections.
 * Sections:
 * - Taste preferences (spice, salt, oil, excluded ingredients, preferred categories, cook time)
 * - Body metrics (gender, age, height, weight)
 * - Goals & activity (activity level, health goal, meals/day)
 * - Dietary constraints (allergies, calorie target)
 *
 * Saves to Room via [PersonalizeViewModel] and navigates to DietPlan on completion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDietPlan: () -> Unit,
    viewModel: PersonalizeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.navigateToDietPlan.collect { onNavigateToDietPlan() }
    }

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) {
            snackbarHostState.showSnackbar("保存成功")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个性化定制") },
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
                        TextButton(
                            onClick = { viewModel.save() },
                            enabled = !uiState.isSaving
                        ) {
                            Text("保存")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Taste Preferences ──
            SectionHeader("口味偏好")
            SpiceSlider(uiState.spiceLevel) { viewModel.updateSpiceLevel(it) }
            TripleOptionRow("盐度", listOf("少盐", "正常", "偏咸"), uiState.saltLevel) { viewModel.updateSaltLevel(it) }
            TripleOptionRow("油量", listOf("少油", "正常", "偏油"), uiState.oilLevel) { viewModel.updateOilLevel(it) }

            HorizontalDivider()

            ExcludedIngredientsSection(uiState.excludedIngredients) { viewModel.toggleExcludedIngredient(it) }
            PreferredCategoriesSection(uiState.preferredCategories) { viewModel.togglePreferredCategory(it) }
            NumberSliderRow("最长烹饪时间", uiState.maxCookingTimeMin, 10..120, "分钟") { viewModel.updateMaxCookingTime(it) }

            HorizontalDivider()

            // ── Body Metrics ──
            SectionHeader("身体数据")
            GenderToggle(uiState.gender) { viewModel.updateGender(it) }
            OutlinedTextField(
                value = if (uiState.age == 0) "" else uiState.age.toString(),
                onValueChange = { it.toIntOrNull()?.let { v -> viewModel.updateAge(v) } },
                label = { Text("年龄") },
                suffix = { Text("岁") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = if (uiState.heightCm == 0) "" else uiState.heightCm.toString(),
                onValueChange = { it.toIntOrNull()?.let { v -> viewModel.updateHeightCm(v) } },
                label = { Text("身高") },
                suffix = { Text("cm") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = if (uiState.weightKg == 0f) "" else uiState.weightKg.toString(),
                onValueChange = { it.toFloatOrNull()?.let { v -> viewModel.updateWeightKg(v) } },
                label = { Text("体重") },
                suffix = { Text("kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            // ── Goals & Activity ──
            SectionHeader("目标与活动")
            ActivityLevelRow(uiState.activityLevel) { viewModel.updateActivityLevel(it) }
            GoalChips(uiState.goal) { viewModel.updateGoal(it) }
            NumberSliderRow("每日餐数", uiState.mealsPerDay, 2..5, "餐") { viewModel.updateMealsPerDay(it) }

            HorizontalDivider()

            // ── Dietary Constraints ──
            SectionHeader("饮食约束")
            AllergyChips(uiState.allergies) { viewModel.toggleAllergy(it) }
            OutlinedTextField(
                value = uiState.calorieTarget?.toString() ?: "",
                onValueChange = { viewModel.updateCalorieTarget(it.toIntOrNull()) },
                label = { Text("热量目标（可选，留空由 AI 计算）") },
                suffix = { Text("kcal") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // ── Action ──
            Button(
                onClick = { viewModel.onStartCustomization() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("开始定制", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Reusable Components ──

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SpiceSlider(value: Int, onUpdate: (Int) -> Unit) {
    val labels = listOf("不辣", "微辣", "中辣", "超辣")
    Column {
        Text("辣度: ${labels[value]}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onUpdate(it.toInt()) },
            valueRange = 0f..3f,
            steps = 2
        )
    }
}

@Composable
private fun TripleOptionRow(label: String, options: List<String>, selected: Int, onUpdate: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(60.dp))
        options.forEachIndexed { index, option ->
            FilterChip(
                selected = selected == index,
                onClick = { onUpdate(index) },
                label = { Text(option) }
            )
        }
    }
}

@Composable
private fun ExcludedIngredientsSection(selected: Set<String>, onToggle: (String) -> Unit) {
    val commonIngredients = listOf("花生", "乳糖", "海鲜", "辣椒", "大蒜", "香菜", "芹菜", "蘑菇")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("饮食忌口:", style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            commonIngredients.take(4).forEach { ing ->
                FilterChip(selected = ing in selected, onClick = { onToggle(ing) }, label = { Text(ing) })
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            commonIngredients.drop(4).forEach { ing ->
                FilterChip(selected = ing in selected, onClick = { onToggle(ing) }, label = { Text(ing) })
            }
        }
    }
}

@Composable
private fun PreferredCategoriesSection(selected: Set<RecipeCategory>, onToggle: (RecipeCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("菜谱偏好:", style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecipeCategory.entries.forEach { cat ->
                FilterChip(
                    selected = cat in selected,
                    onClick = { onToggle(cat) },
                    label = { Text(cat.displayName) }
                )
            }
        }
    }
}

@Composable
private fun GenderToggle(selected: Gender, onUpdate: (Gender) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("性别: ", style = MaterialTheme.typography.bodyMedium)
        Gender.entries.forEach { gender ->
            FilterChip(
                selected = selected == gender,
                onClick = { onUpdate(gender) },
                label = { Text(gender.label) }
            )
        }
    }
}

@Composable
private fun ActivityLevelRow(selected: ActivityLevel, onUpdate: (ActivityLevel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("活动量:", style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityLevel.entries.forEach { level ->
                FilterChip(
                    selected = selected == level,
                    onClick = { onUpdate(level) },
                    label = { Text(level.label) }
                )
            }
        }
    }
}

@Composable
private fun GoalChips(selected: HealthGoal, onUpdate: (HealthGoal) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("健康目标:", style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HealthGoal.entries.take(4).forEach { goal ->
                FilterChip(
                    selected = selected == goal,
                    onClick = { onUpdate(goal) },
                    label = { Text(goal.label) }
                )
            }
        }
        Row {
            FilterChip(
                selected = selected == HealthGoal.MANAGE_BLOOD_SUGAR,
                onClick = { onUpdate(HealthGoal.MANAGE_BLOOD_SUGAR) },
                label = { Text(HealthGoal.MANAGE_BLOOD_SUGAR.label) }
            )
        }
    }
}

@Composable
private fun NumberSliderRow(label: String, value: Int, range: IntRange, suffix: String, onUpdate: (Int) -> Unit) {
    Column {
        Text("$label: $value$suffix", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onUpdate(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}

@Composable
private fun AllergyChips(selected: Set<String>, onToggle: (String) -> Unit) {
    val commonAllergies = listOf("花生", "海鲜", "牛奶", "鸡蛋", "大豆", "小麦", "坚果", "芝麻")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("过敏原:", style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            commonAllergies.take(4).forEach { allergy ->
                FilterChip(selected = allergy in selected, onClick = { onToggle(allergy) }, label = { Text(allergy) })
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            commonAllergies.drop(4).forEach { allergy ->
                FilterChip(selected = allergy in selected, onClick = { onToggle(allergy) }, label = { Text(allergy) })
            }
        }
    }
}
