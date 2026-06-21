package com.example.freshscan.ui.screen.fridge

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.freshscan.domain.model.FreshnessLevel
import com.example.freshscan.domain.model.FridgeItem
import com.example.freshscan.ui.theme.ConfidenceHigh
import com.example.freshscan.ui.theme.ConfidenceLow
import com.example.freshscan.ui.theme.ConfidenceMedium
import com.example.freshscan.ui.theme.FreshGreen
import com.example.freshscan.ui.theme.RottenRed
import com.example.freshscan.util.FormatUtil

/**
 * "My Fridge" screen showing items currently stored in the user's virtual fridge.
 *
 * Features:
 * - Summary header with item count and expiry warnings
 * - Expiring-soon alert banner
 * - Item list with freshness indicators and expiry countdown
 * - Swipe-to-remove
 * - Empty state with guidance
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit,
    viewModel: FridgeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isDeleting) { /* no-op, just observe */ }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            msg?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearSnackbar()
            }
        }
    }

    // Clear-all confirmation dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("清空冰箱") },
            text = { Text("确定要移除冰箱中所有食材吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearAllDialog = false
                }) {
                    Text("确定", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("我的冰箱") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (!uiState.isEmpty) {
                        TextButton(
                            onClick = { showClearAllDialog = true },
                            enabled = !uiState.isDeleting
                        ) {
                            if (uiState.isDeleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("清空", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isEmpty -> {
                EmptyFridgePlaceholder(
                    onNavigateToCamera = onNavigateToCamera,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Summary header
                    item {
                        FridgeSummaryHeader(uiState = uiState)
                    }

                    // Expiring soon alert
                    if (uiState.expiredCount > 0 || uiState.expiringSoonCount > 0) {
                        item {
                            ExpiryAlertBanner(
                                expiredCount = uiState.expiredCount,
                                expiringSoonCount = uiState.expiringSoonCount
                            )
                        }
                    }

                    // Item list
                    items(
                        items = uiState.items,
                        key = { it.id }
                    ) { item ->
                        SwipeableFridgeItem(
                            item = item,
                            onRemove = { viewModel.removeItem(item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Summary header showing total items, fresh/rotten counts, and expiry warnings.
 */
@Composable
private fun FridgeSummaryHeader(uiState: FridgeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${uiState.totalItems} 种食材",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val freshCount = uiState.items.count {
                        it.freshnessLevel == FreshnessLevel.FRESH
                    }
                    val rottenCount = uiState.items.count {
                        it.freshnessLevel == FreshnessLevel.ROTTEN
                    }
                    if (freshCount > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text("新鲜 $freshCount") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = FreshGreen.copy(alpha = 0.15f)
                            )
                        )
                    }
                    if (rottenCount > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text("腐烂 $rottenCount") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = RottenRed.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Filled.Kitchen,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Alert banner showing items that are expired or expiring soon.
 */
@Composable
private fun ExpiryAlertBanner(expiredCount: Int, expiringSoonCount: Int) {
    val parts = mutableListOf<String>()
    if (expiredCount > 0) parts.add("$expiredCount 种已过期")
    if (expiringSoonCount > 0) parts.add("$expiringSoonCount 种即将过期")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (expiredCount > 0)
                RottenRed.copy(alpha = 0.1f)
            else
                ConfidenceMedium.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = if (expiredCount > 0) RottenRed else ConfidenceMedium,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = parts.joinToString("，") + "，建议尽快处理",
                style = MaterialTheme.typography.bodyMedium,
                color = if (expiredCount > 0) RottenRed else ConfidenceMedium
            )
        }
    }
}

/**
 * Swipe-to-dismiss fridge item wrapper.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableFridgeItem(
    item: FridgeItem,
    onRemove: (FridgeItem) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onRemove(item)
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFFF5252)
                    else -> Color.Transparent
                }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "移除",
                    tint = Color.White
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        content = {
            FridgeItemCard(item = item)
        }
    )
}

/**
 * Individual fridge item card.
 */
@Composable
private fun FridgeItemCard(item: FridgeItem) {
    val isExpired = item.isExpired()
    val isExpiringSoon = item.isExpiringSoon()

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Freshness indicator dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = when {
                            isExpired -> RottenRed
                            isExpiringSoon -> ConfidenceMedium
                            item.freshnessLevel == FreshnessLevel.FRESH -> FreshGreen
                            item.freshnessLevel == FreshnessLevel.ROTTEN -> RottenRed
                            else -> ConfidenceMedium
                        },
                        shape = RoundedCornerShape(50)
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Item info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isExpired) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "已过期",
                            style = MaterialTheme.typography.labelSmall,
                            color = RottenRed,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (isExpiringSoon) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "即将过期",
                            style = MaterialTheme.typography.labelSmall,
                            color = ConfidenceMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "入库: ${FormatUtil.formatTimestamp(item.addedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Right: expiry countdown
            Column(horizontalAlignment = Alignment.End) {
                val daysLeft = item.daysUntilExpiry()
                if (daysLeft != null) {
                    val (text, color) = when {
                        daysLeft < 0 -> "已过期 ${-daysLeft}天" to RottenRed
                        daysLeft == 0 -> "今天过期" to RottenRed
                        daysLeft <= 2 -> "剩余 ${daysLeft}天" to ConfidenceMedium
                        daysLeft <= 7 -> "剩余 ${daysLeft}天" to ConfidenceHigh
                        else -> "剩余 ${daysLeft}天" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Empty fridge state with guidance.
 */
@Composable
private fun EmptyFridgePlaceholder(
    onNavigateToCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Kitchen,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "冰箱还是空的",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "扫描食材后会自动存入冰箱\n帮你追踪新鲜度和保质期",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(onClick = onNavigateToCamera) {
            Icon(
                Icons.Filled.Kitchen,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("去扫描")
        }
    }
}
