package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.TaggedContentIdea
import com.example.data.model.TargetLanguage
import com.example.data.model.TargetPlatform
import com.example.data.model.ViralGrowthKit
import com.example.ui.components.GrowthKitReportView
import com.example.ui.studio.AnalyzeUiState
import com.example.ui.studio.IdeaTaggingUiState
import com.example.ui.studio.StreamUiState
import com.example.ui.studio.StudioViewModel
import com.example.voice.VoiceIdeaRecorderCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyzeScreen(
    viewModel: StudioViewModel,
    onShowToast: (String) -> Unit,
    onOpenChatForKit: (ViralGrowthKit) -> Unit,
    onExploreTopic: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val selectedPlatform by viewModel.selectedPlatform.collectAsStateWithLifecycle()
    val useDeepThinking by viewModel.useDeepThinkingForKit.collectAsStateWithLifecycle()
    val creatorNotes by viewModel.creatorNotes.collectAsStateWithLifecycle()
    val selectedMedia by viewModel.selectedMedia.collectAsStateWithLifecycle()
    val analyzeState by viewModel.analyzeState.collectAsStateWithLifecycle()
    val ideaTaggingState by viewModel.ideaTaggingState.collectAsStateWithLifecycle()
    val taggedIdeasState by viewModel.taggedIdeasState.collectAsStateWithLifecycle()
    val taggingKitIds by viewModel.taggingKitIds.collectAsStateWithLifecycle()

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.attachMediaFromUri(context.contentResolver, uri, "video")
            }
        }
    )

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.attachMediaFromUri(context.contentResolver, uri, "audio")
            }
        }
    )

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasAudioPermission = granted
            if (!granted) {
                onShowToast("Microphone permission denied. Type your idea or attach an audio file instead.")
            } else {
                onShowToast("Microphone enabled — tap Speak & Instant Audit to record.")
            }
        }
    )

    val speechFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val transcript = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    .orEmpty()
                if (transcript.isNotBlank()) {
                    viewModel.applyVoiceTranscript(
                        transcript = transcript,
                        contentResolver = context.contentResolver,
                        triggerInstantAnalysis = true
                    )
                }
            }
        }
    )

    val recentTaggedIdeas = (taggedIdeasState as? StreamUiState.Success)?.data.orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_hero),
                    contentDescription = "ViralKit analysis studio banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.35f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Viral Growth Kit Studio",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = "Record a voice idea, attach media (<=18 MB), or paste your script for auto-tagged retention insights.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "1. Capture Idea, Video or Audio",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("pick_video_button")
                        ) {
                            Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pick Video")
                        }

                        OutlinedButton(
                            onClick = {
                                audioPickerLauncher.launch(arrayOf("audio/*"))
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("pick_audio_button")
                        ) {
                            Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pick Audio")
                        }
                    }

                    if (selectedMedia != null) {
                        val media = selectedMedia!!
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (media.kind == "video") Icons.Rounded.VideoLibrary else Icons.Rounded.AudioFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = media.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${media.kind.uppercase()} · ${media.sizeMbFormatted} · Ready for Gemini",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.clearSelectedMedia() },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("clear_media_button")
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Remove attached media",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    VoiceIdeaRecorderCard(
                        context = context,
                        selectedLanguage = selectedLanguage,
                        hasAudioPermission = hasAudioPermission,
                        onRequestPermission = {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onLaunchSystemSpeechFallback = { intent ->
                            speechFallbackLauncher.launch(intent)
                        },
                        onTranscriptReady = { transcript, instantAnalyze ->
                            viewModel.applyVoiceTranscript(
                                transcript = transcript,
                                contentResolver = context.contentResolver,
                                triggerInstantAnalysis = instantAnalyze
                            )
                        }
                    )

                    OutlinedTextField(
                        value = creatorNotes,
                        onValueChange = { viewModel.updateCreatorNotes(it) },
                        label = { Text("Topic, Spoken Script, or Creator Context (Optional with Media)") },
                        placeholder = { Text("e.g. Explaining 3 budget camera lighting hacks for solo Indian creators...") },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("creator_notes_input")
                    )

                    // Automated Keyword Extraction & Trending Topic Tagging Panel
                    IdeaTrendingTagsPanel(
                        state = ideaTaggingState,
                        hasNotes = creatorNotes.isNotBlank(),
                        onAutoTagIdea = { viewModel.extractTagsForCurrentDraft() },
                        onExploreTopic = onExploreTopic,
                        onCopyTags = { text ->
                            clipboard.setText(AnnotatedString(text))
                            onShowToast("Trending topic tags copied")
                        },
                        onOpenUri = { uri ->
                            if (!com.example.ui.components.openSafeExternalUri(uriHandler, uri)) {
                                onShowToast("Blocked unsafe source link")
                            }
                        }
                    )

                    Text(
                        text = "2. Output Language",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TargetLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = selectedLanguage == lang,
                                onClick = { viewModel.selectLanguage(lang) },
                                label = { Text("${lang.label} (${lang.nativeHint})") },
                                modifier = Modifier.testTag("lang_chip_${lang.name.lowercase()}")
                            )
                        }
                    }

                    Text(
                        text = "3. Target Platform",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TargetPlatform.entries.forEach { platform ->
                            FilterChip(
                                selected = selectedPlatform == platform,
                                onClick = { viewModel.selectPlatform(platform) },
                                label = { Text(platform.label) },
                                modifier = Modifier.testTag("platform_chip_${platform.name.lowercase()}")
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Deep Thinking Audit (Carbon HIGH)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Uses gemini-4-carbon with ThinkingLevel.HIGH for complex retention & pacing teardowns.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useDeepThinking,
                                onCheckedChange = { viewModel.setUseDeepThinkingForKit(it) },
                                modifier = Modifier.testTag("deep_thinking_switch")
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.generateViralGrowthKit(context.contentResolver) },
                        enabled = analyzeState !is AnalyzeUiState.Analyzing,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("generate_kit_button")
                    ) {
                        if (analyzeState is AnalyzeUiState.Analyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (useDeepThinking) "Deep Thinking Audit Running..." else "Auditing Pacing & Hooks...",
                                style = MaterialTheme.typography.labelLarge
                            )
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Generate Viral Growth Kit",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }

        when (val state = analyzeState) {
            is AnalyzeUiState.Error -> {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is AnalyzeUiState.Ready -> {
                item {
                    GrowthKitReportView(
                        kit = state.kit,
                        onCopyFeedback = onShowToast,
                        onDiscussInChat = onOpenChatForKit,
                        onExploreTopic = onExploreTopic,
                        onAutoTagKit = { viewModel.tagSavedKitWithTrendingTopics(it) },
                        isTaggingKit = taggingKitIds.contains(state.kit.id)
                    )
                }
            }
            else -> Unit
        }

        if (recentTaggedIdeas.isNotEmpty()) {
            item {
                Text(
                    text = "RECENT AUTO-TAGGED IDEAS (${recentTaggedIdeas.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(
                items = recentTaggedIdeas.take(5),
                key = { it.id.ifBlank { it.ideaText.take(24) } }
            ) { idea ->
                TaggedIdeaHistoryCard(
                    idea = idea,
                    onAudit = { viewModel.auditTaggedIdea(idea, context.contentResolver) },
                    onDelete = { viewModel.deleteTaggedIdea(idea.id) },
                    onExploreTopic = onExploreTopic
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdeaTrendingTagsPanel(
    state: IdeaTaggingUiState,
    hasNotes: Boolean,
    onAutoTagIdea: () -> Unit,
    onExploreTopic: (String) -> Unit,
    onCopyTags: (String) -> Unit,
    onOpenUri: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("idea_trending_tags_panel")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Rounded.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Tagged Industry Topics",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Keyword extraction + Google Search Grounding",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                OutlinedButton(
                    onClick = onAutoTagIdea,
                    enabled = hasNotes && state !is IdeaTaggingUiState.Extracting,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("auto_tag_idea_button")
                ) {
                    Text(
                        text = if (state is IdeaTaggingUiState.Ready) "Re-Tag" else "Auto-Tag",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            when (state) {
                is IdeaTaggingUiState.Idle -> {
                    Text(
                        text = "Record a voice idea (auto-tags immediately) or tap Auto-Tag to match your script with live Google Search industry trends.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is IdeaTaggingUiState.Extracting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Extracting keywords & discovering live trending industry topics...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                is IdeaTaggingUiState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is IdeaTaggingUiState.Ready -> {
                    val res = state.result
                    if (res.extractedKeywords.isNotEmpty()) {
                        Text(
                            text = "EXTRACTED KEYWORDS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            res.extractedKeywords.forEach { kw ->
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = kw,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "TRENDING INDUSTRY TOPICS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onCopyTags(res.trendingTopics.joinToString(", ")) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy trending topics",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        res.trendingTopics.forEach { topic ->
                            AssistChip(
                                onClick = { onExploreTopic(topic) },
                                label = { Text(topic, style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.TravelExplore,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            )
                        }
                    }

                    if (res.rationale.isNotBlank()) {
                        Text(
                            text = res.rationale,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (res.sources.isNotEmpty()) {
                        GroundingSourcesSection(
                            sources = res.sources,
                            onOpenUri = onOpenUri
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaggedIdeaHistoryCard(
    idea: TaggedContentIdea,
    onAudit: () -> Unit,
    onDelete: () -> Unit,
    onExploreTopic: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tagged_idea_card_${idea.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (idea.sourceType == "voice") Icons.Rounded.Mic else Icons.Rounded.LocalOffer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${idea.sourceType.uppercase()} · ${idea.platform} · ${idea.industryNiche}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = onAudit) {
                    Text("Audit Idea")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete tagged idea",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = idea.ideaText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (idea.trendingTopics.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    idea.trendingTopics.forEach { topic ->
                        AssistChip(
                            onClick = { onExploreTopic(topic) },
                            label = { Text(topic, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}
