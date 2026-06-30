package com.example.freshscan.ui.screen.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.freshscan.ui.theme.LocalSemanticColors
import com.example.freshscan.ui.theme.SerifFamily

/**
 * Full-screen produce collection view (v4.2).
 *
 * Shows ALL 260 produce types in a 3-column grid:
 * - Unlocked: full-color card with emoji, name, scan count, rare gold border
 * - Locked: grayed-out card with lock icon, name only
 *
 * Layout: Header row → Progress card → Collected section → Locked section
 */
@Composable
fun CollectionScreen(
    onNavigateBack: () -> Unit,
    viewModel: CollectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val semanticColors = LocalSemanticColors.current
    val progress = uiState.collectedCount.toFloat() / uiState.totalCount.toFloat().coerceAtLeast(1f)
    val collectedEntries = uiState.entries.filter { it.isCollected }
    val lockedEntries = uiState.entries.filter { !it.isCollected }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Header (same height as TopAppBar on other pages: 56dp) ──
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Text(
                    "果蔬图鉴",
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        // ── Content ──
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "加载中...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── Header: progress bar ──
                item(span = { GridItemSpan(3) }) {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.weight(1f).height(10.dp),
                                color = semanticColors.freshnessHigh,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                "已解锁 ${uiState.collectedCount}/${uiState.totalCount} 种",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = semanticColors.freshnessHigh
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${"%.1f".format(progress * 100)}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ── Collected section ──
                item(span = { GridItemSpan(3) }) {
                    Text(
                        "已解锁 · ${collectedEntries.size}种",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (collectedEntries.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🔍", fontSize = 32.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "还没有采集任何果蔬",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "扫描识别果蔬后自动解锁",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                } else {
                    items(collectedEntries, key = { it.label }) { entry ->
                        CollectedGridCard(entry = entry)
                    }
                }

                // ── Locked section ──
                item(span = { GridItemSpan(3) }) {
                    Text(
                        "未解锁 · ${lockedEntries.size}种",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(lockedEntries, key = { it.label }) { entry ->
                    LockedGridCard(entry = entry)
                }
            }
        }
    }
}

// ─── Grid cards ────────────────────────────────────────────────────────────

@Composable
private fun CollectedGridCard(entry: ProduceEntry) {
    val semanticColors = LocalSemanticColors.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = if (entry.isRare) BorderStroke(2.dp, Color(0xFFFFC107)) else null
    ) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(48.dp).background(
                    semanticColors.freshnessHigh.copy(alpha = 0.1f), CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(categoryEmoji(entry.category), fontSize = 24.sp)
            }

            Spacer(Modifier.height(6.dp))

            Text(
                entry.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(2.dp))

            Text(
                "×${entry.scanCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LockedGridCard(entry: ProduceEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp).alpha(0.55f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(48.dp).background(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f), CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text("🔒", fontSize = 20.sp)
            }

            Spacer(Modifier.height(6.dp))

            Text(
                entry.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(2.dp))

            Text(
                "???",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun categoryEmoji(category: String): String = when {
    category.contains("水果") -> "🍎"
    category.contains("蔬菜") -> "🥬"
    category.contains("菌菇") -> "🍄"
    category.contains("豆类") -> "🫘"
    category.contains("调味") -> "🧄"
    else -> "🌱"
}
