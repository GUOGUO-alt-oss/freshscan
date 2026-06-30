package com.example.freshscan.ui.screen.report

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.freshscan.ui.theme.LocalSemanticColors
import com.example.freshscan.ui.theme.SerifFamily
import com.example.freshscan.util.TimeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Weekly diet report screen (v4.2).
 *
 * Shows a summary of this week's scanning, eating, and wasting habits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportScreen(
    onNavigateBack: () -> Unit,
    viewModel: WeeklyReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val semanticColors = LocalSemanticColors.current

    // Date range string
    val weekStart = TimeUtils.getWeekStartMs()
    val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
    val dateRange = "${dateFormat.format(Date(weekStart))} — ${dateFormat.format(Date())}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("饮食周报") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("加载中...", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                Column {
                    Text("📊 本周饮食报告", fontFamily = SerifFamily,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(dateRange, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // 2×2 stats grid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        icon = "📷", value = "${uiState.scanCount}次", label = "扫描食材",
                        color = semanticColors.freshnessHigh, modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = "🌱", value = "+${uiState.newProduceCount}种", label = "新解锁果蔬",
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        icon = "🍳", value = "${uiState.mealQueryCount}次", label = "AI膳食查询",
                        color = semanticColors.freshnessMedium, modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = "💸", value = "¥${"%.1f".format(uiState.wastedValue)}", label = "食材浪费",
                        color = semanticColors.freshnessLow, modifier = Modifier.weight(1f)
                    )
                }

                // Top produce
                if (uiState.topProduce.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("⭐ 最常扫描食材", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            uiState.topProduce.forEachIndexed { i, name ->
                                Text("${i + 1}. $name", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }

                // Waste reminder
                if (uiState.wastedValue > 0) {
                    Card(
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = semanticColors.freshnessLow.copy(alpha = 0.08f))
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("💡", fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Text("本周食材浪费 ¥${"%.1f".format(uiState.wastedValue)}，及时关注冰箱保质期可以减少浪费哦！",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                if (uiState.scanCount == 0 && uiState.mealQueryCount == 0) {
                    Card(
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📭", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("本周还没有饮食记录", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("开始扫描食材，获取个性化饮食报告", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: String, value: String, label: String, color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
