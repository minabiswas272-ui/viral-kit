package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GroundingSource
import com.example.data.model.LatestTrendAlert
import com.example.data.model.TrendAlertFocus
import com.example.data.model.TrendAlertInterval
import com.example.notifications.TrendNotificationHelper
import com.example.ui.components.openSafeExternalUri
import com.example.ui.studio.QuickRewriteUiState
import com.example.ui.studio.StudioViewModel
import com.example.ui.studio.TrendUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrendsScreen(
    viewModel: StudioViewModel,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val trendQuery by viewModel.trendQuery.collectAsStateWithLifecycle()
    val trendState by viewModel.trendState.collectAsStateWithLifecycle()
    val quickDraft by viewModel.quickDraftInput.collectAsStateWithLifecycle()
    val quickRewriteState by viewModel.quickRewriteState.collectAsStateWithLifecycle()
    val language by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val platform by viewModel.selectedPlatform.collectAsStateWithLifecycle()

    val alertPrefs by viewModel.alertPreferences.collectAsStateWithLifecycle()
    val alertIndustryDraft by viewModel.alertIndustryDraft.collectAsStateWithLifecycle()
    val latestAlert by viewModel.latestTrendAlert.collectAsStateWithLifecycle()
    val isCheckingAlertNow by viewModel.isCheckingAlertNow.collectAsStateWithLifecycle()
    val alertError by viewModel.alertError.collectAsStateWithLifecycle()
    val fcmToken by viewModel.fcmToken.collectAsStateWithLifecycle()
    val fcmSubscribedTopic by viewModel.fcmSubscribedTopic.collectAsStateWithLifecycle()
    val isSyncingFcm by viewModel.isSyncingFcm.collectAsStateWithLifecycle()
    val fcmSyncNote by viewModel.fcmSyncNote.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    var pendingPermissionAction by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (granted) {
            when (action) {
                "enable_periodic" -> viewModel.setPeriodicAlertsEnabled(true)
                "check_now" -> viewModel.sendGroundedAlertNow()
                "enable_strategy_push" -> viewModel.setStrategyCompletePushEnabled(true)
                "enable_niche_fcm" -> viewModel.setFcmNichePushEnabled(true)
            }
        } else {
            when (action) {
                "enable_periodic" -> onShowToast("Notification permission is required to deliver periodic trend alerts.")
                "enable_strategy_push" -> onShowToast("Enable notification permission to receive strategy completion alerts.")
                "enable_niche_fcm" -> onShowToast("Enable notification permission to receive FCM niche trend pushes.")
                "check_now" -> {
                    viewModel.sendGroundedAlertNow()
                }
            }
        }
    }

    val presetTopics = listOf(
        "Trending Indian Reels audio & hooks",
        "Kolkata Bengali food & lifestyle Shorts trends",
        "Tech & AI creators viral Reel formats",
        "YouTube Shorts retention algorithm updates"
    )

    val presetIndustries = listOf(
        "Indian Tech & AI Creators",
        "Personal Finance & Investing",
        "Fitness & Nutrition",
        "Street Food & Travel Vlogs"
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Periodic Google Search Grounding Notification Monitor
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (alertPrefs.enabled) {
                                Icons.Rounded.NotificationsActive
                            } else {
                                Icons.Rounded.NotificationsOff
                            },
                            contentDescription = null,
                            tint = if (alertPrefs.enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Viral Audio & Content Gap Alerts",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (alertPrefs.enabled) {
                                    "Scheduled ${alertPrefs.interval.label} · Search Grounding Active"
                                } else {
                                    "Periodic Google Search Grounding alerts"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (alertPrefs.enabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Switch(
                            checked = alertPrefs.enabled,
                            onCheckedChange = { wantEnable ->
                                if (!wantEnable) {
                                    viewModel.setPeriodicAlertsEnabled(false)
                                } else if (
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !TrendNotificationHelper.hasNotificationPermission(context)
                                ) {
                                    pendingPermissionAction = "enable_periodic"
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.setPeriodicAlertsEnabled(true)
                                }
                            },
                            modifier = Modifier.testTag("alert_enable_switch")
                        )
                    }

                    OutlinedTextField(
                        value = alertIndustryDraft,
                        onValueChange = { viewModel.updateAlertIndustry(it) },
                        label = { Text("Your Industry / Creator Niche") },
                        placeholder = { Text("e.g. Indian Tech & AI Creators") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alert_industry_input")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetIndustries.forEach { niche ->
                            AssistChip(
                                onClick = { viewModel.updateAlertIndustry(niche) },
                                label = { Text(niche, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Text(
                        text = "ALERT FOCUS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TrendAlertFocus.entries.forEach { focus ->
                            FilterChip(
                                selected = alertPrefs.focus == focus,
                                onClick = { viewModel.selectAlertFocus(focus) },
                                label = { Text(focus.label, style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = if (focus == TrendAlertFocus.AUDIO_TRENDS) {
                                    {
                                        Icon(
                                            Icons.Rounded.GraphicEq,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                modifier = Modifier.testTag("alert_focus_chip_${focus.name.lowercase()}")
                            )
                        }
                    }

                    Text(
                        text = "CHECK CADENCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TrendAlertInterval.entries.forEach { interval ->
                            FilterChip(
                                selected = alertPrefs.interval == interval,
                                onClick = { viewModel.selectAlertInterval(interval) },
                                label = { Text(interval.label, style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("alert_interval_chip_${interval.hours}h")
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !TrendNotificationHelper.hasNotificationPermission(context)
                            ) {
                                pendingPermissionAction = "check_now"
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.sendGroundedAlertNow()
                            }
                        },
                        enabled = !isCheckingAlertNow,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("alert_check_now_button")
                    ) {
                        if (isCheckingAlertNow) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Scanning Search Trends & Sending Alert...")
                        } else {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Send Grounded Alert Now")
                        }
                    }

                    if (!alertError.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = alertError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    latestAlert?.let { alert ->
                        LatestGroundedAlertCard(
                            alert = alert,
                            onCopy = {
                                clipboard.setText(
                                    AnnotatedString("${alert.headline}\n\n${alert.alertSummary}\n\n${alert.fullBrief}")
                                )
                                onShowToast("Trend alert copied")
                            },
                            onOpenUri = { uri ->
                                if (!openSafeExternalUri(uriHandler, uri)) {
                                    onShowToast("Blocked unsafe source link")
                                }
                            }
                        )
                    }
                }
            }
        }

        // 1b. Firebase Cloud Messaging Push Delivery Card
        item {
            FcmPushSettingsCard(
                strategyCompleteEnabled = alertPrefs.strategyCompletePushEnabled,
                nicheTopicEnabled = alertPrefs.fcmNichePushEnabled,
                subscribedTopic = fcmSubscribedTopic,
                fcmToken = fcmToken,
                isSyncing = isSyncingFcm,
                syncNote = fcmSyncNote,
                onToggleStrategyComplete = { wantEnable ->
                    if (!wantEnable) {
                        viewModel.setStrategyCompletePushEnabled(false)
                    } else if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !TrendNotificationHelper.hasNotificationPermission(context)
                    ) {
                        pendingPermissionAction = "enable_strategy_push"
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setStrategyCompletePushEnabled(true)
                    }
                },
                onToggleNicheTopic = { wantEnable ->
                    if (!wantEnable) {
                        viewModel.setFcmNichePushEnabled(false)
                    } else if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !TrendNotificationHelper.hasNotificationPermission(context)
                    ) {
                        pendingPermissionAction = "enable_niche_fcm"
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setFcmNichePushEnabled(true)
                    }
                },
                onSyncFcm = { viewModel.syncFcmRegistration(silent = false) },
                onCopyToken = { token ->
                    clipboard.setText(AnnotatedString(token))
                    onShowToast("FCM device token copied")
                }
            )
        }

        // 2. Live Google Search Grounding Trend Radar (gemini-4-hydrogen + googleSearch)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.TravelExplore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live Trend Radar",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Google Search Grounding · gemini-4-hydrogen · ${language.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = trendQuery,
                        onValueChange = { viewModel.updateTrendQuery(it) },
                        label = { Text("Search Creator Niche or Viral Format") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trend_query_input")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetTopics.forEach { preset ->
                            AssistChip(
                                onClick = { viewModel.searchLiveTrends(preset) },
                                label = { Text(preset, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.searchLiveTrends() },
                        enabled = trendState !is TrendUiState.Loading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("search_trends_button")
                    ) {
                        if (trendState is TrendUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onSecondary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Searching Live Creator Trends...")
                        } else {
                            Icon(Icons.Rounded.TravelExplore, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Fetch Grounded Trends (${platform.label})")
                        }
                    }

                    when (val state = trendState) {
                        is TrendUiState.Error -> {
                            Surface(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        is TrendUiState.Success -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "VERIFIED TREND BRIEF",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(state.result.summaryText))
                                                onShowToast("Trend brief copied")
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                Icons.Rounded.ContentCopy,
                                                contentDescription = "Copy trend brief",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = state.result.summaryText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (state.result.sources.isNotEmpty()) {
                                        GroundingSourcesSection(
                                            sources = state.result.sources,
                                            onOpenUri = { uri ->
                                                if (!openSafeExternalUri(uriHandler, uri)) {
                                                    onShowToast("Blocked unsafe source link")
                                                }
                                            }
                                        )
                                    }

                                    if (!state.result.searchEntryPointHtml.isNullOrBlank()) {
                                        SearchEntryPointWebView(htmlContent = state.result.searchEntryPointHtml)
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }

        // 3. Low-Latency Flash-Lite Hook Lab (gemini-3.1-flash-lite)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Instant Hook & Title Lab",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Low-Latency Engine · gemini-3.1-flash-lite · ${language.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = quickDraft,
                        onValueChange = { viewModel.updateQuickDraftInput(it) },
                        label = { Text("Draft Hook, Video Idea, or Weak Title") },
                        placeholder = { Text("e.g. Today I am showing how I edit Reels in CapCut...") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_hook_input")
                    )

                    Button(
                        onClick = { viewModel.runQuickHookRewrite() },
                        enabled = quickRewriteState !is QuickRewriteUiState.Loading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("quick_rewrite_button")
                    ) {
                        if (quickRewriteState is QuickRewriteUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onTertiary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Rewriting in Flash-Lite...")
                        } else {
                            Icon(Icons.Rounded.Bolt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Instant Rewrite (${language.label})")
                        }
                    }

                    when (val rw = quickRewriteState) {
                        is QuickRewriteUiState.Error -> {
                            Text(
                                text = rw.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        is QuickRewriteUiState.Success -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "RAPID HOOK VARIATIONS",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(rw.output))
                                                onShowToast("Hooks copied")
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                Icons.Rounded.ContentCopy,
                                                contentDescription = "Copy rapid hook variations",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = rw.output,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun LatestGroundedAlertCard(
    alert: LatestTrendAlert,
    onCopy: () -> Unit,
    onOpenUri: (String) -> Unit
) {
    val formattedTime = remember(alert.generatedAtMillis) {
        if (alert.generatedAtMillis > 0L) {
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                .format(Date(alert.generatedAtMillis))
        } else {
            "Just now"
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("latest_grounded_alert_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "LATEST ALERT · ${alert.focus.label.uppercase()} · $formattedTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copy grounded alert",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = alert.headline,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = alert.alertSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            if (alert.fullBrief.isNotBlank()) {
                Text(
                    text = alert.fullBrief,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (alert.sources.isNotEmpty()) {
                GroundingSourcesSection(
                    sources = alert.sources,
                    onOpenUri = onOpenUri
                )
            }
            if (!alert.searchEntryPointHtml.isNullOrBlank()) {
                SearchEntryPointWebView(htmlContent = alert.searchEntryPointHtml)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroundingSourcesSection(
    sources: List<GroundingSource>,
    onOpenUri: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "SOURCES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sources.forEach { source ->
                AssistChip(
                    onClick = { onOpenUri(source.uri) },
                    label = {
                        Text(
                            text = source.title.take(36),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Open source ${source.title}",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun SearchEntryPointWebView(htmlContent: String) {
    val uriHandler = LocalUriHandler.current
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                    domStorageEnabled = false
                    @Suppress("DEPRECATION")
                    databaseEnabled = false
                    @Suppress("DEPRECATION")
                    saveFormData = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        openSafeExternalUri(uriHandler, request?.url?.toString())
                        return true
                    }
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://www.google.com",
                htmlContent,
                "text/html",
                "UTF-8",
                null
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    )
}

@Composable
private fun FcmPushSettingsCard(
    strategyCompleteEnabled: Boolean,
    nicheTopicEnabled: Boolean,
    subscribedTopic: String?,
    fcmToken: String?,
    isSyncing: Boolean,
    syncNote: String?,
    onToggleStrategyComplete: (Boolean) -> Unit,
    onToggleNicheTopic: (Boolean) -> Unit,
    onSyncFcm: () -> Unit,
    onCopyToken: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fcm_push_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Firebase Cloud Messaging (Push)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (!fcmToken.isNullOrBlank()) {
                            "Device registered · Ready for strategy & niche pushes"
                        } else {
                            "Awaiting FCM registration token"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!fcmToken.isNullOrBlank()) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Strategy Analysis Complete",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Notify as soon as your Viral Growth Kit audit finishes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = strategyCompleteEnabled,
                            onCheckedChange = onToggleStrategyComplete,
                            modifier = Modifier.testTag("fcm_strategy_complete_switch")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.TravelExplore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trending Topic Niche Topic Push",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = subscribedTopic?.let { "Subscribed to FCM topic: $it" }
                                    ?: "Subscribe to live niche topic alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = nicheTopicEnabled,
                            onCheckedChange = onToggleNicheTopic,
                            modifier = Modifier.testTag("fcm_niche_topic_switch")
                        )
                    }
                }
            }

            if (!syncNote.isNullOrBlank()) {
                Text(
                    text = syncNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onSyncFcm,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("fcm_sync_button")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Syncing...")
                    } else {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sync FCM Token")
                    }
                }

                if (!fcmToken.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onCopyToken(fcmToken) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("fcm_copy_token_button")
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Token")
                    }
                }
            }
        }
    }
}

