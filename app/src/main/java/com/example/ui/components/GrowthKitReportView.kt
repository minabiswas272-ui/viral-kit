package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PacingSegment
import com.example.data.model.ViralGrowthKit
import com.example.export.GrowthKitExporter
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrowthKitReportView(
    kit: ViralGrowthKit,
    onCopyFeedback: (String) -> Unit,
    onDiscussInChat: ((ViralGrowthKit) -> Unit)? = null,
    onExploreTopic: ((String) -> Unit)? = null,
    onAutoTagKit: ((ViralGrowthKit) -> Unit)? = null,
    isTaggingKit: Boolean = false,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showRawJson by remember { mutableStateOf(false) }
    var isExportingPdf by remember { mutableStateOf(false) }
    var isExportingImage by remember { mutableStateOf(false) }

    val scoreColor = when {
        kit.viralScore >= 80 -> MaterialTheme.colorScheme.secondary
        kit.viralScore >= 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Viral Score & Diagnosis Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
                        CircularProgressIndicator(
                            progress = { (kit.viralScore.coerceIn(0, 100) / 100f) },
                            modifier = Modifier.size(76.dp),
                            color = scoreColor,
                            trackColor = scoreColor.copy(alpha = 0.16f),
                            strokeWidth = 7.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${kit.viralScore}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = scoreColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "/100",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BadgePill(text = kit.language, color = MaterialTheme.colorScheme.primary)
                            BadgePill(text = kit.platform, color = MaterialTheme.colorScheme.secondary)
                            BadgePill(text = kit.modelUsed, color = MaterialTheme.colorScheme.tertiary)
                        }
                        Text(
                            text = "Retention Diagnosis",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Text(
                    text = kit.diagnosisCritique,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isExportingImage || isExportingPdf) return@Button
                            scope.launch {
                                isExportingImage = true
                                try {
                                    val artifact = GrowthKitExporter.exportKitToShareImage(context, kit)
                                    if (!GrowthKitExporter.launchShareSheet(context, artifact, kit)) {
                                        onCopyFeedback("No sharing app available on device")
                                    }
                                } catch (e: Exception) {
                                    onCopyFeedback("Failed to create share image: ${e.localizedMessage ?: "error"}")
                                } finally {
                                    isExportingImage = false
                                }
                            }
                        },
                        enabled = !isExportingImage && !isExportingPdf,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("share_image_button")
                    ) {
                        if (isExportingImage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Rendering...")
                        } else {
                            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share Image")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (isExportingPdf || isExportingImage) return@OutlinedButton
                            scope.launch {
                                isExportingPdf = true
                                try {
                                    val artifact = GrowthKitExporter.exportKitToPdf(context, kit)
                                    if (!GrowthKitExporter.launchShareSheet(context, artifact, kit)) {
                                        onCopyFeedback("No app available to share PDF")
                                    }
                                } catch (e: Exception) {
                                    onCopyFeedback("Failed to export PDF: ${e.localizedMessage ?: "error"}")
                                } finally {
                                    isExportingPdf = false
                                }
                            }
                        },
                        enabled = !isExportingPdf && !isExportingImage,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("export_pdf_button")
                    ) {
                        if (isExportingPdf) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Building PDF...")
                        } else {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export PDF")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(kit.rawJson))
                            onCopyFeedback("Full JSON Growth Kit copied")
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("copy_json_button")
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy JSON")
                    }

                    OutlinedButton(
                        onClick = { showRawJson = !showRawJson },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_json_button")
                    ) {
                        Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (showRawJson) "Hide JSON" else "View JSON")
                    }
                }

                AnimatedVisibility(visible = showRawJson) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = kit.rawJson,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // 1b. Auto-Extracted Trending Industry Topics (Google Search Grounding)
        if (kit.trendingTopics.isNotEmpty() || onAutoTagKit != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Rounded.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trending Industry Topics",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Auto-tagged via Google Search Grounding",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (kit.trendingTopics.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(kit.trendingTopics.joinToString(", ")))
                                    onCopyFeedback("Trending topic tags copied")
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy trending topics",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (kit.trendingTopics.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            kit.trendingTopics.forEach { topic ->
                                AssistChip(
                                    onClick = {
                                        if (onExploreTopic != null) {
                                            onExploreTopic(topic)
                                        } else {
                                            clipboard.setText(AnnotatedString(topic))
                                            onCopyFeedback("Copied topic: $topic")
                                        }
                                    },
                                    label = { Text(topic, style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.TravelExplore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                )
                            }
                        }
                    } else if (onAutoTagKit != null) {
                        OutlinedButton(
                            onClick = { onAutoTagKit(kit) },
                            enabled = !isTaggingKit,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("auto_tag_kit_button")
                        ) {
                            if (isTaggingKit) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Discovering Live Industry Topics...")
                            } else {
                                Icon(Icons.Rounded.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Tag with Live Trending Topics")
                            }
                        }
                    }

                    if (kit.trendingTagSources.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            kit.trendingTagSources.forEach { src ->
                                AssistChip(
                                    onClick = {
                                        if (!openSafeExternalUri(uriHandler, src.uri)) {
                                            onCopyFeedback("Blocked unsafe source link")
                                        }
                                    },
                                    label = { Text(src.title.take(30), style = MaterialTheme.typography.labelSmall) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.OpenInNew,
                                            contentDescription = "Open source ${src.title}",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. First 30 Seconds Content Strategy
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    icon = Icons.Rounded.Timeline,
                    tint = MaterialTheme.colorScheme.tertiary,
                    title = "First 30-Second Engagement Strategy",
                    onCopy = {
                        clipboard.setText(AnnotatedString(kit.first30sStrategy))
                        onCopyFeedback("30s strategy copied")
                    }
                )
                Text(
                    text = kit.first30sStrategy,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 3. High-Retention Hooks (First 3 Seconds)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(
                    icon = Icons.Rounded.AutoAwesome,
                    tint = MaterialTheme.colorScheme.primary,
                    title = "High-Retention Hooks (0:00 - 0:03)",
                    onCopy = {
                        val all = "Curiosity: ${kit.curiosityHook}\n\nContrarian: ${kit.contrarianHook}\n\nDirect Value: ${kit.directValueHook}"
                        clipboard.setText(AnnotatedString(all))
                        onCopyFeedback("All 3 hooks copied")
                    }
                )
                HookItemCard("Curiosity / Mystery Hook", kit.curiosityHook, MaterialTheme.colorScheme.primary) {
                    clipboard.setText(AnnotatedString(kit.curiosityHook))
                    onCopyFeedback("Curiosity hook copied")
                }
                HookItemCard("Contrarian / Shocking Hook", kit.contrarianHook, MaterialTheme.colorScheme.tertiary) {
                    clipboard.setText(AnnotatedString(kit.contrarianHook))
                    onCopyFeedback("Contrarian hook copied")
                }
                HookItemCard("Direct Value / Problem-Solution", kit.directValueHook, MaterialTheme.colorScheme.secondary) {
                    clipboard.setText(AnnotatedString(kit.directValueHook))
                    onCopyFeedback("Direct Value hook copied")
                }
            }
        }

        // 4. Timestamped B-Roll & Visual Pacing Guide
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(
                    icon = Icons.Rounded.MovieFilter,
                    tint = MaterialTheme.colorScheme.secondary,
                    title = "Timestamped B-Roll & Pacing Guide",
                    onCopy = {
                        val guideText = kit.pacingSegments.joinToString("\n\n") { seg ->
                            "[${seg.timestamp}]\nB-Roll: ${seg.bRollSuggestion}\nText: ${seg.onScreenText}\nCut/SFX: ${seg.cutAndSfx}"
                        }
                        clipboard.setText(AnnotatedString(guideText))
                        onCopyFeedback("Pacing guide copied")
                    }
                )
                kit.pacingSegments.forEach { segment ->
                    PacingSegmentRow(segment)
                }
            }
        }

        // 5. Optimized Captions & Hashtags
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(
                    icon = Icons.Rounded.Tag,
                    tint = MaterialTheme.colorScheme.primary,
                    title = "Instagram Reel Caption & Hashtags",
                    onCopy = {
                        val igFull = "${kit.instagramCaption}\n\n${kit.hashtags.joinToString(" ")}"
                        clipboard.setText(AnnotatedString(igFull))
                        onCopyFeedback("Instagram caption & hashtags copied")
                    }
                )
                Text(
                    text = kit.instagramCaption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    kit.hashtags.forEach { tag ->
                        AssistChip(
                            onClick = {
                                clipboard.setText(AnnotatedString(tag))
                                onCopyFeedback("Copied $tag")
                            },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        // 6. YouTube Shorts Title & Description
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    icon = Icons.Rounded.OndemandVideo,
                    tint = MaterialTheme.colorScheme.primary,
                    title = "YouTube Shorts SEO",
                    onCopy = {
                        val yt = "${kit.youtubeTitle}\n\n${kit.youtubeDescription}"
                        clipboard.setText(AnnotatedString(yt))
                        onCopyFeedback("YouTube Shorts title & description copied")
                    }
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "HIGH-CTR TITLE (${kit.youtubeTitle.length}/60 CHARS)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = kit.youtubeTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Text(
                    text = kit.youtubeDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 7. Thumbnail Text Ideas
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    icon = Icons.Rounded.PhotoLibrary,
                    tint = MaterialTheme.colorScheme.tertiary,
                    title = "Thumbnail Text Overlays (3-4 Words)",
                    onCopy = {
                        clipboard.setText(AnnotatedString(kit.thumbnailIdeas.joinToString("\n")))
                        onCopyFeedback("Thumbnail ideas copied")
                    }
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    kit.thumbnailIdeas.forEach { idea ->
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                clipboard.setText(AnnotatedString(idea))
                                onCopyFeedback("Copied: $idea")
                            }
                        ) {
                            Text(
                                text = idea.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        if (onDiscussInChat != null) {
            Button(
                onClick = { onDiscussInChat(kit) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("discuss_kit_in_chat_button")
            ) {
                Icon(
                    Icons.Rounded.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Refine Mid-Roll & CTA in Strategist Chat",
                    color = MaterialTheme.colorScheme.onSecondary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun BadgePill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
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

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onCopy,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = "Copy $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun HookItemCard(
    label: String,
    hookText: String,
    accent: Color,
    onCopy: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        onClick = onCopy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = hookText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PacingSegmentRow(segment: PacingSegment) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = segment.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text(
                text = "B-Roll: ${segment.bRollSuggestion}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "On-Screen Text: \"${segment.onScreenText}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = "Cut & SFX: ${segment.cutAndSfx}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
