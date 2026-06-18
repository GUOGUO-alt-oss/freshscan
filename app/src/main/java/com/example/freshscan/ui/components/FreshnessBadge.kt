package com.example.freshscan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.freshscan.domain.model.FreshnessLevel
import com.example.freshscan.ui.theme.FreshGreen
import com.example.freshscan.ui.theme.RottenRed
import com.example.freshscan.ui.theme.UncertainOrange
import com.example.freshscan.util.FormatUtil

/**
 * Displays freshness status as a colored badge with emoji, label, and confidence.
 *
 * Color coding: Green (fresh), Red (rotten), Orange (uncertain).
 * Includes accessibility content descriptions.
 */
@Composable
fun FreshnessBadge(
    level: FreshnessLevel,
    confidence: Float,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val (backgroundColor, textColor) = when (level) {
        FreshnessLevel.FRESH -> FreshGreen.copy(alpha = 0.15f) to FreshGreen
        FreshnessLevel.ROTTEN -> RottenRed.copy(alpha = 0.15f) to RottenRed
        FreshnessLevel.UNCERTAIN -> UncertainOrange.copy(alpha = 0.15f) to UncertainOrange
    }

    val description = when (level) {
        FreshnessLevel.FRESH -> "新鲜，置信度${FormatUtil.formatConfidence(confidence)}"
        FreshnessLevel.ROTTEN -> "腐烂，置信度${FormatUtil.formatConfidence(confidence)}"
        FreshnessLevel.UNCERTAIN -> "无法确定新鲜度，置信度${FormatUtil.formatConfidence(confidence)}"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(if (compact) 8.dp else 12.dp))
            .background(backgroundColor)
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 4.dp else 6.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = level.emoji,
            fontSize = if (compact) 14.sp else 18.sp
        )
        Text(
            text = level.displayName,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 12.sp else 14.sp
        )
        if (!compact) {
            Text(
                text = FormatUtil.formatConfidence(confidence),
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
