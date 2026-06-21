package com.example.freshscan.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.freshscan.domain.model.NutritionFacts
import com.example.freshscan.domain.model.ProduceInfo

@Composable
fun ProduceInfoSheet(
    info: ProduceInfo,
    isAIExtensionLoading: Boolean,
    onBack: () -> Unit,
    onRetryAI: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", modifier = Modifier.size(20.dp))
            }
            Text(
                "${info.displayName}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            AssistChip(
                onClick = {},
                label = { Text(info.category, style = MaterialTheme.typography.labelSmall) }
            )
        }
        if (info.seasonality.isNotBlank()) {
            Text("时令：${info.seasonality}", style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider()

        // Intro (only when non-empty)
        if (info.intro.isNotBlank()) {
            SectionTitle("📖 简介")
            Text(info.intro, style = MaterialTheme.typography.bodyMedium)
        }

        // Nutrition (only when at least calories is non-zero)
        if (info.nutrition.caloriesKcal > 0) {
            SectionTitle("📊 营养成分（每100g）")
            NutritionGrid(info.nutrition)
        }

        // Health Benefits (only when non-empty)
        if (info.healthBenefits.isNotEmpty()) {
            SectionTitle("💪 健康功效")
            info.healthBenefits.forEach { benefit ->
                Text("✅ $benefit", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Storage (only when non-empty)
        if (info.storageTips.isNotBlank()) {
            SectionTitle("📦 保存方法")
            Text(info.storageTips, style = MaterialTheme.typography.bodyMedium)
        }

        // AI Extension section
        HorizontalDivider()
        SectionTitle("🤖 AI 扩展")

        if (isAIExtensionLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI 正在生成...", style = MaterialTheme.typography.bodySmall)
            }
        } else if (info.selectionTips != null || info.pairingSuggestions != null || info.funFact != null) {
            info.selectionTips?.let {
                Text("🔍 挑选技巧", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            info.pairingSuggestions?.let { pairings ->
                Spacer(modifier = Modifier.height(4.dp))
                Text("🥗 搭配建议", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(pairings.joinToString("、"), style = MaterialTheme.typography.bodyMedium)
            }
            info.funFact?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text("💡 你知道吗", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            TextButton(onClick = onRetryAI) {
                Text("加载失败，点击重试")
            }
        }
    }
}

/**
 * Compact produce info card for embedding in detail screens.
 * Shows core info sections (intro, nutrition, benefits, storage, seasonality)
 * without the header bar or AI extension section.
 */
@Composable
fun ProduceInfoCard(
    info: ProduceInfo,
    modifier: Modifier = Modifier
) {
    val hasContent = info.intro.isNotBlank() || info.nutrition.caloriesKcal > 0 ||
            info.healthBenefits.isNotEmpty() || info.storageTips.isNotBlank()

    if (!hasContent) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider()

        Text(
            "📋 食材百科",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (info.intro.isNotBlank()) {
            SectionTitle("📖 简介")
            Text(info.intro, style = MaterialTheme.typography.bodyMedium)
        }

        if (info.nutrition.caloriesKcal > 0) {
            SectionTitle("📊 营养成分（每100g）")
            NutritionGrid(info.nutrition)
        }

        if (info.healthBenefits.isNotEmpty()) {
            SectionTitle("💪 健康功效")
            info.healthBenefits.forEach { benefit ->
                Text("✅ $benefit", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (info.storageTips.isNotBlank()) {
            SectionTitle("📦 保存方法")
            Text(info.storageTips, style = MaterialTheme.typography.bodyMedium)
        }

        if (info.seasonality.isNotBlank()) {
            SectionTitle("🗓️ 时令")
            Text(info.seasonality, style = MaterialTheme.typography.bodyMedium)
        }

        // AI extension fields (if available)
        if (info.selectionTips != null || info.pairingSuggestions != null || info.funFact != null) {
            HorizontalDivider()
            Text(
                "🤖 AI 扩展",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            info.selectionTips?.let {
                Text("🔍 挑选技巧", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            info.pairingSuggestions?.let { pairings ->
                Spacer(modifier = Modifier.height(4.dp))
                Text("🥗 搭配建议", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(pairings.joinToString("、"), style = MaterialTheme.typography.bodyMedium)
            }
            info.funFact?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text("💡 你知道吗", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun NutritionGrid(nutrition: NutritionFacts) {
    val items = listOf(
        "热量" to "${nutrition.caloriesKcal} kcal",
        "蛋白质" to "${nutrition.proteinG}g",
        "脂肪" to "${nutrition.fatG}g",
        "碳水" to "${nutrition.carbsG}g",
        "纤维" to "${nutrition.fiberG}g",
        nutrition.vitaminCMg?.let { "维C" to "${it}mg" },
        nutrition.vitaminAUg?.let { "维A" to "${it}μg" },
        nutrition.potassiumMg?.let { "钾" to "${it}mg" },
        nutrition.glycemicIndex?.let { "升糖指数" to "$it" }
    ).filterNotNull()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (label, value) ->
                    ElevatedCard(
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                // Fill remaining slots to maintain grid alignment
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
