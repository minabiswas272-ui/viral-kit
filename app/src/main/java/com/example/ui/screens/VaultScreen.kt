package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.CreateApkBottomCard
import com.example.R
import com.example.data.model.TargetLanguage
import com.example.data.model.ViralGrowthKit
import com.example.ui.components.GrowthKitReportView
import com.example.ui.components.GrowthTrendChart
import com.example.ui.studio.StreamUiState
import com.example.ui.studio.StudioViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultScreen(
    viewModel: StudioViewModel,
    onShowToast: (String) -> Unit,
    onNavigateToAnalyze: () -> Unit,
    onOpenChatForKit: (ViralGrowthKit) -> Unit,
    onExploreTopic: (String) -> Unit,
    onSwitchAccount: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val kitsState by viewModel.savedKitsState.collectAsStateWithLifecycle()
    val selectedKit by viewModel.selectedVaultKit.collectAsStateWithLifecycle()
    val taggingKitIds by viewModel.taggingKitIds.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingSyncKitIds by viewModel.pendingSyncKitIds.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val isSyncingPending by viewModel.isSyncingPending.collectAsStateWithLifecycle()
    val isSyncingSupabase by viewModel.isSyncingSupabase.collectAsStateWithLifecycle()
    val supabaseStatusNote by viewModel.supabaseStatusNote.collectAsStateWithLifecycle()
    val isSupabaseConfigured = viewModel.isSupabaseConfigured
    var languageFilter by remember { mutableStateOf<TargetLanguage?>(null) }
    var vaultSearchQuery by remember { mutableStateOf("") }
    var kitPendingRename by remember { mutableStateOf<ViralGrowthKit?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var kitPendingDelete by remember { mutableStateOf<ViralGrowthKit?>(null) }

    BackHandler(enabled = selectedKit != null) {
        viewModel.openVaultKit(null)
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
                        onClick = { viewModel.openVaultKit(null) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vault_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Vault list"
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
                            text = kit.mediaLabel.ifBlank { "${kit.language} · ${kit.platform}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            renameDraft = kit.title
                            kitPendingRename = kit
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vault_detail_rename_button")
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Rename strategy kit"
                        )
                    }
                    IconButton(
                        onClick = { kitPendingDelete = kit },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vault_delete_detail_button")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete kit",
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
    } else {
        when (val state = kitsState) {
            is StreamUiState.Loading -> {
                Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is StreamUiState.Error -> {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            is StreamUiState.Success -> {
                val allKits = state.data
                val filteredKits by remember(allKits, languageFilter, vaultSearchQuery) {
                    derivedStateOf {
                        val query = vaultSearchQuery.trim().lowercase()
                        allKits.filter { kit ->
                            val matchesLanguage = languageFilter == null ||
                                kit.language.equals(languageFilter!!.label, ignoreCase = true)
                            val matchesQuery = query.isEmpty() ||
                                kit.title.lowercase().contains(query) ||
                                kit.curiosityHook.lowercase().contains(query) ||
                                kit.mediaLabel.lowercase().contains(query) ||
                                kit.trendingTopics.any { it.lowercase().contains(query) }
                            matchesLanguage && matchesQuery
                        }
                    }
                }

                if (allKits.isEmpty()) {
                    Column(
                        modifier = modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SignedInLibraryHeaderCard(
                            creatorName = viewModel.creatorDisplayName,
                            creatorEmail = viewModel.creatorEmail,
                            kitCount = 0,
                            isOnline = isOnline,
                            pendingSyncCount = pendingSyncCount,
                            isSyncingPending = isSyncingPending,
                            onSyncNow = { viewModel.syncOfflineKitsNow() },
                            onSwitchAccount = onSwitchAccount
                        )

                        SupabaseSyncCard(
                            isConfigured = isSupabaseConfigured,
                            isOnline = isOnline,
                            isSyncing = isSyncingSupabase,
                            statusNote = supabaseStatusNote,
                            hasLocalKits = false,
                            onBackupNow = { viewModel.backupToSupabaseNow() },
                            onRestoreNow = { viewModel.restoreFromSupabaseNow() }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_vault),
                                contentDescription = "Empty Viral Growth Kit Vault",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(22.dp))
                            )
                            Spacer(Modifier.height(18.dp))
                            Text(
                                text = "Your Growth Kit Vault is Empty",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Analyze a Reel, Short, audio track, or script in Bengali, Hinglish, or English—every kit auto-syncs to your Google account.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(18.dp))
                            Button(
                                onClick = onNavigateToAnalyze,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(50.dp)
                                    .testTag("empty_vault_analyze_button")
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Create First Viral Growth Kit")
                            }
                        }

                        CreateApkBottomCard(
                            onShowToast = onShowToast
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            SignedInLibraryHeaderCard(
                                creatorName = viewModel.creatorDisplayName,
                                creatorEmail = viewModel.creatorEmail,
                                kitCount = allKits.size,
                                isOnline = isOnline,
                                pendingSyncCount = pendingSyncCount,
                                isSyncingPending = isSyncingPending,
                                onSyncNow = { viewModel.syncOfflineKitsNow() },
                                onSwitchAccount = onSwitchAccount
                            )
                        }

                        item {
                            SupabaseSyncCard(
                                isConfigured = isSupabaseConfigured,
                                isOnline = isOnline,
                                isSyncing = isSyncingSupabase,
                                statusNote = supabaseStatusNote,
                                hasLocalKits = true,
                                onBackupNow = { viewModel.backupToSupabaseNow() },
                                onRestoreNow = { viewModel.restoreFromSupabaseNow() }
                            )
                        }

                        item {
                            GrowthTrendChart(
                                kits = allKits,
                                onSelectKit = { viewModel.openVaultKit(it) }
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = vaultSearchQuery,
                                onValueChange = { vaultSearchQuery = it },
                                placeholder = { Text("Search strategies, hooks, or trending tags...") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (vaultSearchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { vaultSearchQuery = "" },
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
                                    .testTag("vault_search_input")
                            )
                        }

                        item {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = languageFilter == null,
                                    onClick = { languageFilter = null },
                                    label = { Text("All (${allKits.size})") }
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
                        }

                        if (filteredKits.isEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "No strategy kits match your search or language filter.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                vaultSearchQuery = ""
                                                languageFilter = null
                                            },
                                            modifier = Modifier.testTag("vault_clear_filters_button")
                                        ) {
                                            Text("Reset Filters")
                                        }
                                    }
                                }
                            }
                        } else {
                            items(filteredKits, key = { it.id.ifBlank { it.title + it.viralScore } }) { kit ->
                                VaultKitCard(
                                    kit = kit,
                                    isPendingSync = pendingSyncKitIds.contains(kit.id),
                                    onClick = { viewModel.openVaultKit(kit) },
                                    onRename = {
                                        renameDraft = kit.title
                                        kitPendingRename = kit
                                    },
                                    onDelete = { kitPendingDelete = kit }
                                )
                            }
                        }

                        item {
                            CreateApkBottomCard(
                                onShowToast = onShowToast
                            )
                        }
                    }
                }
            }
        }
    }

    kitPendingRename?.let { targetKit ->
        val trimmed = renameDraft.trim()
        AlertDialog(
            onDismissRequest = { kitPendingRename = null },
            title = { Text("Rename Strategy Kit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Update how this Viral Growth Kit appears in your synced cloud library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { if (it.length <= 160) renameDraft = it },
                        label = { Text("Strategy title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_rename_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameVaultKit(targetKit.id, trimmed)
                        kitPendingRename = null
                    },
                    enabled = trimmed.isNotEmpty(),
                    modifier = Modifier.testTag("vault_rename_confirm_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { kitPendingRename = null },
                    modifier = Modifier.testTag("vault_rename_cancel_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    kitPendingDelete?.let { targetKit ->
        AlertDialog(
            onDismissRequest = { kitPendingDelete = null },
            title = { Text("Delete Strategy Kit?") },
            text = {
                Text(
                    text = "Remove \"${targetKit.title}\" permanently from your private cloud Content Strategy Library?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteVaultKit(targetKit.id)
                        kitPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("vault_delete_confirm_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { kitPendingDelete = null },
                    modifier = Modifier.testTag("vault_delete_cancel_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SignedInLibraryHeaderCard(
    creatorName: String,
    creatorEmail: String?,
    kitCount: Int,
    isOnline: Boolean,
    pendingSyncCount: Int,
    isSyncingPending: Boolean,
    onSyncNow: () -> Unit,
    onSwitchAccount: () -> Unit
) {
    val badgeColor = when {
        !isOnline -> MaterialTheme.colorScheme.tertiary
        pendingSyncCount > 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    val badgeIcon = when {
        !isOnline -> Icons.Rounded.CloudOff
        pendingSyncCount > 0 -> Icons.Rounded.CloudUpload
        else -> Icons.Rounded.Lock
    }
    val badgeText = when {
        !isOnline -> "Offline Cache · $kitCount ${if (kitCount == 1) "kit" else "kits"}"
        pendingSyncCount > 0 -> "$pendingSyncCount pending sync · $kitCount ${if (kitCount == 1) "kit" else "kits"}"
        else -> "Synced + Cached · $kitCount ${if (kitCount == 1) "kit" else "kits"}"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_library_owner_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.VerifiedUser,
                            contentDescription = "Authenticated Google library",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$creatorName's Strategy Library",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!creatorEmail.isNullOrBlank()) {
                        Text(
                            text = creatorEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("vault_sync_status_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            badgeIcon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isOnline && pendingSyncCount > 0) {
                        OutlinedButton(
                            onClick = onSyncNow,
                            enabled = !isSyncingPending,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("vault_sync_now_button")
                        ) {
                            Icon(
                                Icons.Rounded.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isSyncingPending) "Syncing..." else "Sync",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onSwitchAccount,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vault_switch_account_button")
                    ) {
                        Icon(
                            Icons.Rounded.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Switch Account", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultKitCard(
    kit: ViralGrowthKit,
    isPendingSync: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val scoreColor = when {
        kit.viralScore >= 80 -> MaterialTheme.colorScheme.secondary
        kit.viralScore >= 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_kit_card_${kit.id}")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = scoreColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(56.dp)
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

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${kit.language} · ${kit.platform}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = kit.curiosityHook,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (kit.trendingTopics.isNotEmpty()) {
                    Text(
                        text = kit.trendingTopics.take(3).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isPendingSync) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CloudUpload,
                            contentDescription = "Pending Firestore sync",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Cached offline · Pending sync",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onRename,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("vault_rename_button_${kit.id}")
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Rename ${kit.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("vault_delete_button_${kit.id}")
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete ${kit.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SupabaseSyncCard(
    isConfigured: Boolean,
    isOnline: Boolean,
    isSyncing: Boolean,
    statusNote: String?,
    hasLocalKits: Boolean,
    onBackupNow: () -> Unit,
    onRestoreNow: () -> Unit
) {
    val accentColor = if (isConfigured) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val statusLabel = when {
        !isConfigured -> "Setup Needed"
        isSyncing -> "Syncing..."
        !isOnline -> "Offline"
        else -> "Connected"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("supabase_sync_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Storage,
                            contentDescription = "Supabase PostgreSQL sync",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Supabase Postgres Sync",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isConfigured) {
                            "Mirror or restore your Viral Growth Kits with Supabase."
                        } else {
                            "Add SUPABASE_URL & SUPABASE_ANON_KEY in AI Studio Secrets and run supabase/schema.sql."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = accentColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("supabase_status_badge")
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (!statusNote.isNullOrBlank()) {
                Text(
                    text = statusNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isConfigured) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackupNow,
                        enabled = !isSyncing && isOnline && hasLocalKits,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("supabase_backup_button")
                    ) {
                        Icon(
                            Icons.Rounded.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isSyncing) "Syncing..." else "Backup",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    OutlinedButton(
                        onClick = onRestoreNow,
                        enabled = !isSyncing && isOnline,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("supabase_restore_button")
                    ) {
                        Icon(
                            Icons.Rounded.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Restore",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}


