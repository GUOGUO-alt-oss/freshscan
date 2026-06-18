package com.example.freshscan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.freshscan.domain.model.FreshnessLevel
import com.example.freshscan.domain.model.RecognitionResult
import com.example.freshscan.ui.theme.FreshGreen
import com.example.freshscan.ui.theme.RottenRed
import com.example.freshscan.ui.theme.UncertainOrange
import com.example.freshscan.util.FormatUtil

/**
 * Bottom result card displaying recognition results.
 *
 * Shows fruit name, freshness status badge, and confidence.
 * In expanded mode, also shows Top-3 predictions.
 * Tap anywhere on the card to expand/collapse.
 */
@Composable
fun ResultCard(
    result: RecognitionResult,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = when (result.freshnessLevel) {
        FreshnessLevel.FRESH -> FreshGreen
        FreshnessLevel.ROTTEN -> RottenRed
        FreshnessLevel.UNCERTAIN -> UncertainOrange
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onExpandClick() },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Collapsed view: always shown
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${result.fruitCategory.displayName} · ${result.freshnessLevel.displayName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "推理耗时: ${FormatUtil.formatInferenceTime(result.inferenceTimeMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Expand/collapse indicator
                Icon(
                    imageVector = if (isExpanded)
                        Icons.Default.KeyboardArrowDown
                    else
                        Icons.Default.KeyboardArrowUp,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                FreshnessBadge(
                    level = result.freshnessLevel,
                    confidence = result.confidence,
                    compact = false
                )
            }

            // Stability warning for uncertain results
            if (result.freshnessLevel == FreshnessLevel.UNCERTAIN) {
                Text(
                    text = "置信度过低，请调整角度或光线后重试",
                    style = MaterialTheme.typography.bodySmall,
                    color = UncertainOrange,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Expanded view: Top-3 predictions
            if (isExpanded && result.topPredictions.isNotEmpty()) {
                Text(
                    text = "Top-3 预测",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                ConfidenceBar(
                    predictions = result.topPredictions.take(3)
                )
            }
        }
    }
}
