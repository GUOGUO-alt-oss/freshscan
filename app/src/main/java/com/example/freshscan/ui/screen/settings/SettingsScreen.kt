package com.example.freshscan.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.freshscan.BuildConfig

/**
 * v2.0 Settings screen (the "My" tab in bottom navigation).
 *
 * Sections:
 * - Recognition mode: toggle classic (v1) mode
 * - Recipe preferences: navigate to taste profile + shopping list
 * - Data management: clear history
 * - About: version info and model details
 *
 * Wireframe: docs/03-UI设计规格-v2.md §4.7
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToFavorites: () -> Unit,
    onNavigateToShoppingList: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToPersonalize: () -> Unit,
    onNavigateToWeeklyReport: () -> Unit = {},
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isClassicMode by viewModel.isClassicMode.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.clearHistoryResult.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
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
            // ─── Recognition Mode ───
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column {
                    SectionHeader(title = "识别模式")
                    SettingsSwitchRow(
                        icon = Icons.Filled.History,
                        title = "经典模式 (v1)",
                        subtitle = "切换回 v1 实时相机识别",
                        checked = isClassicMode,
                        onCheckedChange = { viewModel.toggleClassicMode(it) }
                    )
                }
            }

            // ─── v4.1: Recipe Preferences ───
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column {
                    SectionHeader(title = "菜谱偏好")
                    SettingsRow(
                        icon = Icons.Filled.Bookmark,
                        title = "我的收藏",
                        subtitle = "查看收藏的菜谱和AI推荐",
                        onClick = onNavigateToFavorites
                    )
                    SettingsRow(
                        icon = Icons.Filled.ShoppingCart,
                        title = "购物清单",
                        subtitle = "管理需要购买的食材",
                        onClick = onNavigateToShoppingList
                    )
                }
            }

            // ─── v4.1: Health Profile ───
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column {
                    SectionHeader(title = "健康画像")
                    SettingsRow(
                        icon = Icons.Filled.Person,
                        title = "个性化定制",
                        subtitle = "口味偏好 · 身体数据 · AI 饮食计划",
                        onClick = onNavigateToPersonalize
                    )
                }
            }

            // ─── v4.2: Weekly Report ───
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column {
                    SectionHeader(title = "饮食报告")
                    SettingsRow(
                        icon = Icons.Filled.Assessment,
                        title = "本周饮食报告",
                        subtitle = "查看本周扫描、膳食和浪费统计",
                        onClick = onNavigateToWeeklyReport
                    )
                }
            }

            // ─── v4.1: History (demoted from tab) ───
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column {
                    SectionHeader(title = "历史记录")
                    SettingsRow(
                        icon = Icons.Filled.History,
                        title = "识别历史",
                        subtitle = "查看扫描记录和菜谱推荐",
                        onClick = onNavigateToHistory
                    )
                }
            }

            // ─── Data Management ───
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column {
                    SectionHeader(title = "数据管理")
                    SettingsRow(
                        icon = Icons.Filled.Delete,
                        title = "清除历史记录",
                        subtitle = "删除所有扫描记录",
                        onClick = { showClearDialog = true }
                    )
                }
            }

            // ─── About ───
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column {
                    SectionHeader(title = "关于")
                    SettingsRow(
                        icon = Icons.Filled.Info,
                        title = "鲜识 v${BuildConfig.VERSION_NAME}",
                        subtitle = "模型: EfficientDet + MobileNetV3\n数据集: Fruits-360 (260类)",
                        onClick = {}
                    )
                }
            }
        }
    }

    // Clear history confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除历史记录") },
            text = { Text("确定要删除所有扫描记录和膳食查询历史吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clearHistory()
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * Section header with title.
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

/**
 * Single row in the settings list with a chevron.
 */
@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * Settings row with a Switch toggle instead of a chevron.
 */
@Composable
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
