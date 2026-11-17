package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.TargetLanguage
import com.example.data.model.TargetPlatform
import com.example.data.model.ViralGrowthKit
import com.example.ui.components.GrowthKitReportView
import com.example.ui.components.GrowthTrendChart
import com.example.ui.studio.StreamUiState
import com.example.ui.studio.StudioViewModel
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrowthKitHistoryScreen(
    viewModel: StudioViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAnalyze: () -> Unit,
    onOpenChatForKit: (ViralGrowthKit) -> Unit,
    onExploreTopic: (String) -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val kitsState by viewModel.savedKitsState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshingHistory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.historySearchQuery.collectAsStateWithLifecycle()
    val taggingKitIds by viewModel.taggingKitIds.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingSyncKitIds by viewModel.pendingSyncKitIds.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val isSyncingPending by viewModel.isSyncingPending.collectAsStateWithLifecycle()

    var selectedKit by remember { mutableStateOf<ViralGrowthKit?>(null) }
    var languageFilter by remember { mutableStateOf<TargetLanguage?>(null) }
    var platformFilter by remember { mutableStateOf<TargetPlatform?>(null) }

    BackHandler(enabled = true) {
        if (selectedKit != null) {
            selectedKit = null
        } else {
            onNavigateBack()
        }
    }

    if (selectedKit != null) {
        val kit = selectedKit!!
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedKit = null },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("history_detail_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to strategy history"
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = kit.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Saved ${formatKitTimestamp(kit.createdAt ?: kit.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.deleteVaultKit(kit.id)
                            selectedKit = null
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("history_detail_delete_button")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete saved kit",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            item {
                GrowthKitReportView(
                    kit = kit,
                    onCopyFeedback = onShowToast,
                    onDiscussInChat = onOpenChatForKit,
                    onExploreTopic = onExploreTopic,
                    onAutoTagKit = { viewModel.tagSavedKitWithTrendingTopics(it) },
                    isTaggingKit = taggingKitIds.contains(kit.id)
                )
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Screen Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("history_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Icon(
                    Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Strategy History",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isOnline) {
                            "Offline Room Cache · Synced with Firestore"
                        } else {
                            "Offline Mode · Viewing Local Room Cache"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOnline) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary
                    )
                }
                IconButton(
                    onClick = { viewModel.refreshKitsHistory() },
                    enabled = !isRefreshing,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("history_refresh_button")
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Sync strategy history from Firestore"
                        )
                    }
                }
            }
        }

        when (val state = kitsState) {
            is StreamUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Fetching saved strategy results...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is StreamUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { viewModel.refreshKitsHistory() },
                        modifier = Modifier.testTag("history_retry_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Retry Firestore Fetch")
                    }
                }
            }
            is StreamUiState.Success -> {
                val allKits = state.data
                val stats by remember(allKits) {
                    derivedStateOf {
                        val total = allKits.size
                        val avg = if (total > 0) allKits.map { it.viralScore }.average().toInt() else 0
                        val best = allKits.maxOfOrNull { it.viralScore }?.toInt() ?: 0
                        Triple(total, avg, best)
                    }
                }

                val filteredKits by remember(allKits, searchQuery, languageFilter, platformFilter) {
                    derivedStateOf {
                        val q = searchQuery.trim().lowercase()
                        allKits.filter { kit ->
                            val matchesLang = languageFilter == null ||
                                kit.language.equals(languageFilter!!.label, ignoreCase = true)
                            val matchesPlat = platformFilter == null ||
                                kit.platform.equals(platformFilter!!.label, ignoreCase = true)
                            val matchesQuery = q.isEmpty() ||
                                kit.title.lowercase().contains(q) ||
                                kit.mediaLabel.lowercase().contains(q) ||
                                kit.curiosityHook.lowercase().contains(q) ||
                                kit.diagnosisCritique.lowercase().contains(q) ||
                                kit.trendingTopics.any { it.lowercase().contains(q) }
                            matchesLang && matchesPlat && matchesQuery
                        }
                    }
                }

                if (allKits.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_vault),
                            contentDescription = "No saved Viral Growth Kits yet",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(22.dp))
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "No Saved Growth Kits Yet",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Analyze your first video, audio clip, or Reel script to save a full Viral Growth Kit to Firestore.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = onNavigateToAnalyze,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("history_empty_analyze_button")
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate Viral Growth Kit")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!isOnline || pendingSyncCount > 0) {
                            item {
                                OfflineSyncStatusBanner(
                                    isOnline = isOnline,
                                    pendingSyncCount = pendingSyncCount,
                                    isSyncing = isSyncingPending,
                                    onSyncNow = { viewModel.syncOfflineKitsNow() }
                                )
                            }
                        }

                        item {
                            HistoryMetricsBanner(
                                totalKits = stats.first,
                                averageScore = stats.second,
                                topScore = stats.third
                            )
                        }

                        item {
                            GrowthTrendChart(
                                kits = allKits,
                                onSelectKit = { selectedKit = it }
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateHistorySearchQuery(it) },
                                placeholder = { Text("Search saved titles, hooks, or clips...") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.updateHistorySearchQuery("") },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("history_search_input")
                            )
                        }

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = languageFilter == null,
                                        onClick = { languageFilter = null },
                                        label = { Text("All Languages") }
                                    )
                                    TargetLanguage.entries.forEach { lang ->
                                        FilterChip(
                                            selected = languageFilter == lang,
                                            onClick = {
                                                languageFilter = if (languageFilter == lang) null else lang
                                            },
                                            label = { Text(lang.label) }
                                        )
                                    }
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TargetPlatform.entries.forEach { plat ->
                                        FilterChip(
                                            selected = platformFilter == plat,
                                            onClick = {
                                                platformFilter = if (platformFilter == plat) null else plat
                                            },
                                            label = { Text(plat.label) }
                                        )
                                    }
                                }
                            }
                        }

                        if (filteredKits.isEmpty()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No saved Viral Growth Kits match your current search or filters.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(18.dp)
                                    )
                                }
                            }
                        } else {
                            items(
                                items = filteredKits,
                                key = { it.id.ifBlank { "${it.title}_${it.createdAt?.seconds ?: 0L}" } }
                            ) { kit ->
                                HistoryStrategyCard(
                                    kit = kit,
                                    isPendingSync = pendingSyncKitIds.contains(kit.id),
                                    onClick = { selectedKit = kit },
                                    onDelete = { viewModel.deleteVaultKit(kit.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMetricsBanner(
    totalKits: Int,
    averageScore: Int,
    topScore: Int
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricColumn(
                value = "$totalKits",
                label = "SAVED KITS",
                accent = MaterialTheme.colorScheme.primary
            )
            MetricColumn(
                value = "$averageScore/100",
                label = "AVG SCORE",
                accent = MaterialTheme.colorScheme.tertiary
            )
            MetricColumn(
                value = "$topScore/100",
                label = "TOP SCORE",
                accent = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun MetricColumn(
    value: String,
    label: String,
    accent: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OfflineSyncStatusBanner(
    isOnline: Boolean,
    pendingSyncCount: Int,
    isSyncing: Boolean,
    onSyncNow: () -> Unit
) {
    val accent = if (!isOnline) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val icon = if (!isOnline) Icons.Rounded.CloudOff else Icons.Rounded.CloudUpload
    val message = when {
        !isOnline && pendingSyncCount > 0 ->
            "Offline Mode · $pendingSyncCount change(s) saved in Room and queued for Firestore sync."
        !isOnline ->
            "Offline Mode · Viewing locally cached Viral Growth Kits from Room."
        else ->
            "$pendingSyncCount offline change(s) ready to sync with Firestore."
    }

    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_offline_sync_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (isOnline && pendingSyncCount > 0) {
                OutlinedButton(
                    onClick = onSyncNow,
                    enabled = !isSyncing,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("history_sync_now_button")
                ) {
                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isSyncing) "Syncing" else "Sync", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryStrategyCard(
    kit: ViralGrowthKit,
    isPendingSync: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val scoreColor = when {
        kit.viralScore >= 80 -> MaterialTheme.colorScheme.secondary
        kit.viralScore >= 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    val mediaIcon = when (kit.mediaKind) {
        "video" -> Icons.Rounded.Videocam
        "audio" -> Icons.Rounded.AudioFile
        else -> Icons.Rounded.Description
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_kit_card_${kit.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = scoreColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${kit.viralScore}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = kit.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            mediaIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${kit.mediaLabel.ifBlank { "Concept Audit" }} · ${formatKitTimestamp(kit.createdAt ?: kit.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete ${kit.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HistoryPill(kit.language, MaterialTheme.colorScheme.primary)
                HistoryPill(kit.platform, MaterialTheme.colorScheme.secondary)
                HistoryPill(kit.modelUsed, MaterialTheme.colorScheme.tertiary)
                if (isPendingSync) {
                    HistoryPill("Pending Sync", MaterialTheme.colorScheme.tertiary)
                }
                kit.trendingTopics.take(3).forEach { topic ->
                    HistoryPill(topic, MaterialTheme.colorScheme.secondary)
                }
            }

            if (kit.curiosityHook.isNotBlank()) {
                Text(
                    text = "Hook: \"${kit.curiosityHook}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (kit.first30sStrategy.isNotBlank()) {
                Text(
                    text = "30s Strategy: ${kit.first30sStrategy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HistoryPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun formatKitTimestamp(timestamp: Timestamp?): String {
    val safeTs = timestamp ?: Timestamp.now()
    val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return formatter.format(safeTs.toDate())
}
