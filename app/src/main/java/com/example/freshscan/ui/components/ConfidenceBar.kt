package com.example.freshscan.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.freshscan.domain.model.Prediction
import com.example.freshscan.ui.theme.ConfidenceHigh
import com.example.freshscan.ui.theme.ConfidenceLow
import com.example.freshscan.ui.theme.ConfidenceMedium
import com.example.freshscan.util.FormatUtil

/**
 * Horizontal confidence bar showing a single Prediction.
 *
 * Used in the detail screen for Top-3 display. Color-coded:
 * - rank 1: primary green
 * - rank 2: secondary
 * - rank 3: tertiary
 */
@Composable
fun ConfidenceBar(
    predictions: List<Prediction>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        predictions.forEachIndexed { index, prediction ->
            val barColor = when (index) {
                0 -> ConfidenceHigh
                1 -> ConfidenceMedium
                2 -> ConfidenceLow
                else -> MaterialTheme.colorScheme.outline
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .semantics {
                        contentDescription = "预测${index + 1}: ${prediction.displayName}, 置信度${FormatUtil.formatConfidence(prediction.confidence)}"
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rank label
                Text(
                    text = "${index + 1}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = barColor,
                    modifier = Modifier.width(20.dp),
                    textAlign = TextAlign.Center
                )

                // Prediction label
                Text(
                    text = prediction.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(100.dp)
                )

                // Progress bar
                LinearProgressIndicator(
                    progress = { prediction.confidence.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = barColor,
                    trackColor = barColor.copy(alpha = 0.15f)
                )

                // Percentage
                Text(
                    text = FormatUtil.formatConfidence(prediction.confidence),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
