package com.example.freshscan.ui.screen.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.freshscan.domain.model.FreshnessLevel
import com.example.freshscan.domain.model.RecognitionResult
import com.example.freshscan.ui.components.ConfidenceBar
import com.example.freshscan.ui.theme.FreshGreen
import com.example.freshscan.ui.theme.RottenRed
import com.example.freshscan.ui.theme.UncertainOrange
import com.example.freshscan.util.FormatUtil

/**
 * Recognition detail screen showing Top-3 predictions, explanation, and recommendations.
 *
 * Layout:
 * - TopBar with back navigation
 * - Top-1 large display (emoji + fruit name + freshness + confidence)
 * - Inference time
 * - Top-3 confidence bars
 * - Explanation text (confidence-dependent)
 * - Recommendation card (freshness-dependent)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("识别详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val result = uiState.result
        val error = uiState.error

        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "加载中...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (result != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top-1 display area
                DetailTopResult(result = result)

                Spacer(modifier = Modifier.height(24.dp))

                // Divider section: Top-3 predictions
                if (result.topPredictions.isNotEmpty()) {
                    Text(
                        text = "预测分布",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ConfidenceBar(predictions = result.topPredictions.take(3))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Explanation section
                ExplanationSection(confidence = result.confidence)

                Spacer(modifier = Modifier.height(16.dp))

                // Recommendation section
                RecommendationSection(
                    freshnessLevel = result.freshnessLevel,
                    fruitCategoryName = result.fruitCategory.displayName
                )
            }
        } else if (error != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(32.dp)
                )
                Button(onClick = { viewModel.reload() }) {
                    Text("重试")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "未找到识别结果",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

/**
 * Top display area: large emoji, fruit name, freshness, confidence.
 */
@Composable
private fun DetailTopResult(result: RecognitionResult) {
    val accentColor = when (result.freshnessLevel) {
        FreshnessLevel.FRESH -> FreshGreen
        FreshnessLevel.ROTTEN -> RottenRed
        FreshnessLevel.UNCERTAIN -> UncertainOrange
    }

    Text(
        text = result.freshnessLevel.emoji,
        fontSize = 64.sp,
        modifier = Modifier.size(80.dp),
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "${result.fruitCategory.displayName} · ${result.freshnessLevel.displayName}",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = FormatUtil.formatConfidence(result.confidence),
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold,
        color = accentColor,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "推理耗时: ${FormatUtil.formatInferenceTime(result.inferenceTimeMs)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Confidence-dependent explanation text.
 */
@Composable
private fun ExplanationSection(confidence: Float) {
    val explanation = when {
        confidence >= 0.95f -> "AI 模型对此判断非常确定。该果蔬的外观特征与训练数据中的标准样本高度匹配。"
        confidence >= 0.85f -> "AI 模型对此判断较有把握。建议结合肉眼观察果蔬表面的颜色、纹理做辅助判断。"
        confidence >= 0.70f -> "AI 模型对此判断有一定不确定性。可能原因：光线不佳、果蔬姿态异常、或该品种外观特征不够明显。"
        else -> "AI 模型对此判断不确定，结果仅供参考。建议变换角度或光照条件后重新识别。"
    }

    Text(
        text = "解释",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = explanation,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Freshness-dependent recommendation card.
 */
@Composable
private fun RecommendationSection(
    freshnessLevel: FreshnessLevel,
    fruitCategoryName: String
) {
    val (title, suggestions, accentColor) = when (freshnessLevel) {
        FreshnessLevel.FRESH -> Triple(
            "保存建议",
            listOf(
                "尽快食用以获取最佳口感",
                "建议冷藏保存"
            ),
            FreshGreen
        )
        FreshnessLevel.ROTTEN -> Triple(
            "食用建议",
            listOf(
                "该果蔬已出现腐烂迹象，不建议食用",
                "腐烂部分即使切除，毒素可能已扩散",
                "建议丢弃或用于堆肥"
            ),
            RottenRed
        )
        FreshnessLevel.UNCERTAIN -> Triple(
            "建议",
            listOf(
                "请在更好光照下重新识别",
                "尝试变换拍摄角度",
                "可用手触摸检查果蔬表面硬度"
            ),
            UncertainOrange
        )
    }

    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = accentColor,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(4.dp))
    suggestions.forEach { suggestion ->
        Text(
            text = "• $suggestion",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}
