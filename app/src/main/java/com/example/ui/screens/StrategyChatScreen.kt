package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatMessageRecord
import com.example.data.model.StrategistModel
import com.example.data.model.StrategySession
import com.example.data.model.TargetLanguage
import com.example.data.model.TargetPlatform
import com.example.ui.components.openSafeExternalUri
import com.example.ui.studio.StreamUiState
import com.example.ui.studio.StreamingChatDraft
import com.example.ui.studio.StudioViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

private const val MAX_STRATEGY_INPUT_CHARS = 4000

@Composable
fun StrategyChatScreen(
    viewModel: StudioViewModel,
    onShowToast: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val chatState by viewModel.chatHistoryState.collectAsStateWithLifecycle()
    val sessionsState by viewModel.strategySessionsState.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val selectedModel by viewModel.chatModel.collectAsStateWithLifecycle()
    val searchGrounding by viewModel.chatSearchGrounding.collectAsStateWithLifecycle()
    val streamDraft by viewModel.chatStreamDraft.collectAsStateWithLifecycle()
    val lastSearchHtml by viewModel.lastSearchWidgetHtml.collectAsStateWithLifecycle()
    val language by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val platform by viewModel.selectedPlatform.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    StrategyChatContent(
        chatState = chatState,
        sessionsState = sessionsState,
        activeSession = activeSession,
        selectedModel = selectedModel,
        searchGrounding = searchGrounding,
        streamDraft = streamDraft,
        lastSearchHtml = lastSearchHtml,
        language = language,
        platform = platform,
        isOnline = isOnline,
        onSelectModel = viewModel::selectChatModel,
        onToggleSearchGrounding = { viewModel.toggleChatSearchGrounding(!searchGrounding) },
        onSendPrompt = { prompt -> viewModel.sendChatPrompt(prompt) },
        onClearHistory = viewModel::clearChatHistory,
        onSelectSession = viewModel::selectStrategySession,
        onNewSession = viewModel::startNewStrategySession,
        onRenameSession = viewModel::renameStrategySession,
        onDeleteSession = viewModel::deleteStrategySession,
        onShowToast = onShowToast,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StrategyChatContent(
    chatState: StreamUiState<List<ChatMessageRecord>>,
    selectedModel: StrategistModel,
    searchGrounding: Boolean,
    streamDraft: StreamingChatDraft,
    lastSearchHtml: String?,
    language: TargetLanguage,
    platform: TargetPlatform,
    isOnline: Boolean,
    onSelectModel: (StrategistModel) -> Unit,
    onToggleSearchGrounding: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onClearHistory: () -> Unit,
    sessionsState: StreamUiState<List<StrategySession>> = StreamUiState.Success(emptyList()),
    activeSession: StrategySession? = null,
    onSelectSession: (String) -> Unit = {},
    onNewSession: () -> Unit = {},
    onRenameSession: (String, String) -> Unit = { _, _ -> },
    onDeleteSession: (String) -> Unit = {},
    onShowToast: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var showSessionsSheet by remember { mutableStateOf(false) }
    var renameTargetSession by remember { mutableStateOf<StrategySession?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deleteTargetSession by remember { mutableStateOf<StrategySession?>(null) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    val messages = (chatState as? StreamUiState.Success)?.data.orEmpty()
    val savedSessions = (sessionsState as? StreamUiState.Success)?.data.orEmpty()
    val canJumpToBottom by remember(messages.size, streamDraft.isStreaming) {
        derivedStateOf { listState.canScrollForward }
    }

    LaunchedEffect(messages.size, streamDraft.isStreaming, streamDraft.partialText.length) {
        val totalItems = messages.size + if (streamDraft.isStreaming) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Model, Grounding & Thread Controls Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Forum,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeSession?.title?.takeIf { it.isNotBlank() } ?: "New Strategy Session",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("strategy_active_session_title")
                        )
                        Text(
                            text = "${messages.size} ${if (messages.size == 1) "turn" else "turns"} · ${platform.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!isOnline) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("strategy_chat_offline_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudOff,
                                    contentDescription = "Offline",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    FilterChip(
                        selected = showSessionsSheet,
                        onClick = { showSessionsSheet = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (savedSessions.isNotEmpty()) {
                                    "Sessions (${savedSessions.size})"
                                } else {
                                    "Sessions"
                                }
                            )
                        },
                        modifier = Modifier.testTag("strategy_sessions_button")
                    )

                    IconButton(
                        onClick = onNewSession,
                        enabled = !streamDraft.isStreaming,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("strategy_new_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddComment,
                            contentDescription = "Start new strategy session",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("strategy_chat_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "Delete strategy session",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedModel == StrategistModel.HYDROGEN) {
                        item(key = "strategy_search_toggle") {
                            FilterChip(
                                selected = searchGrounding,
                                onClick = onToggleSearchGrounding,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.TravelExplore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Search") },
                                modifier = Modifier.testTag("strategy_chat_search_chip")
                            )
                        }
                    }
                    items(StrategistModel.entries, key = { it.name }) { model ->
                        FilterChip(
                            selected = selectedModel == model,
                            onClick = { onSelectModel(model) },
                            label = { Text("${model.displayName} · ${model.badge}") },
                            modifier = Modifier.testTag("strategy_model_chip_${model.name.lowercase()}")
                        )
                    }
                }

                Text(
                    text = selectedModel.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

        // Scrolling Multi-Turn Message List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (chatState) {
                is StreamUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.testTag("strategy_chat_loading"))
                    }
                }

                is StreamUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chatState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is StreamUiState.Success -> {
                    if (messages.isEmpty() && !streamDraft.isStreaming) {
                        StrategyChatEmptyState(
                            language = language,
                            platform = platform,
                            enabled = isOnline,
                            recentSessions = savedSessions.take(3),
                            onSelectStarter = onSendPrompt,
                            onResumeSession = onSelectSession
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("strategy_chat_message_list"),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = messages,
                                key = { msg ->
                                    msg.id.ifBlank { "${msg.role}_${msg.text.hashCode()}_${msg.modelId}" }
                                }
                            ) { msg ->
                                StrategyMessageBubble(
                                    message = msg,
                                    onCopyText = { copied ->
                                        clipboardManager.setText(AnnotatedString(copied))
                                        onShowToast("Strategist response copied")
                                    },
                                    onOpenUri = { uri -> openSafeExternalUri(uriHandler, uri) }
                                )
                            }

                            if (streamDraft.isStreaming) {
                                item(key = "strategy_streaming_draft") {
                                    StrategyStreamingBubble(
                                        partialText = streamDraft.partialText,
                                        partialThoughts = streamDraft.partialThoughts,
                                        modelName = streamDraft.model.displayName
                                    )
                                }
                            } else if (messages.lastOrNull()?.role == "model") {
                                item(key = "strategy_follow_up_chips") {
                                    StrategyFollowUpRow(
                                        platform = platform,
                                        enabled = isOnline,
                                        onSelectFollowUp = onSendPrompt
                                    )
                                }
                            }

                            if (!lastSearchHtml.isNullOrBlank()) {
                                item(key = "strategy_search_widget") {
                                    SearchEntryPointWebView(htmlContent = lastSearchHtml)
                                }
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = canJumpToBottom,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(14.dp)
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val lastIdx = (messages.size + if (streamDraft.isStreaming) 1 else 0) - 1
                                        if (lastIdx >= 0) {
                                            listState.animateScrollToItem(lastIdx)
                                        }
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("strategy_chat_scroll_bottom_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDownward,
                                    contentDescription = "Jump to latest message"
                                )
                            }
                        }
                    }
                }
            }
        }

        if (streamDraft.error != null) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("strategy_chat_error_banner")
            ) {
                Text(
                    text = streamDraft.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Composer Input Field & Send Action
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { next ->
                        inputText = next.take(MAX_STRATEGY_INPUT_CHARS)
                    },
                    placeholder = {
                        Text("Refine hooks, pacing, or ${language.label} ${platform.label} scripts...")
                    },
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() && !streamDraft.isStreaming && isOnline) {
                                val toSend = inputText.trim()
                                inputText = ""
                                onSendPrompt(toSend)
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("strategy_chat_input")
                )

                FilledIconButton(
                    onClick = {
                        val toSend = inputText.trim()
                        if (toSend.isNotEmpty()) {
                            inputText = ""
                            onSendPrompt(toSend)
                        }
                    },
                    enabled = inputText.isNotBlank() && !streamDraft.isStreaming && isOnline,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(50.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("strategy_chat_send_button")
                ) {
                    if (streamDraft.isStreaming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send strategy message"
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Delete Strategy Thread?") },
            text = {
                Text(
                    text = "Remove this session and all ${messages.size} saved messages from Firestore?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearHistory()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("strategy_chat_clear_confirm")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    modifier = Modifier.testTag("strategy_chat_clear_cancel")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSessionsSheet) {
        StrategySessionsSheet(
            sessionsState = sessionsState,
            activeSessionId = activeSession?.id,
            isStreaming = streamDraft.isStreaming,
            onDismiss = { showSessionsSheet = false },
            onSelectSession = { sessionId ->
                showSessionsSheet = false
                onSelectSession(sessionId)
            },
            onNewSession = {
                showSessionsSheet = false
                onNewSession()
            },
            onRequestRename = { session ->
                renameDraft = session.title
                renameTargetSession = session
            },
            onRequestDelete = { session ->
                deleteTargetSession = session
            }
        )
    }

    renameTargetSession?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTargetSession = null },
            title = { Text("Rename Strategy Session") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(120) },
                    label = { Text("Session title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("strategy_rename_session_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameDraft.trim()
                        if (trimmed.isNotEmpty()) {
                            onRenameSession(target.id, trimmed)
                            renameTargetSession = null
                        }
                    },
                    enabled = renameDraft.isNotBlank(),
                    modifier = Modifier.testTag("strategy_rename_session_confirm")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetSession = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteTargetSession?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTargetSession = null },
            title = { Text("Delete Session?") },
            text = {
                Text(
                    text = "Permanently remove \"${target.title}\" and its saved Firestore messages?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSession(target.id)
                        deleteTargetSession = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("strategy_delete_session_confirm")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetSession = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrategySessionsSheet(
    sessionsState: StreamUiState<List<StrategySession>>,
    activeSessionId: String?,
    isStreaming: Boolean,
    onDismiss: () -> Unit,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onRequestRename: (StrategySession) -> Unit,
    onRequestDelete: (StrategySession) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("strategy_sessions_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Strategy Sessions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Synced with Cloud Firestore — tap any session to resume",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onNewSession,
                    enabled = !isStreaming,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("strategy_sheet_new_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            when (sessionsState) {
                is StreamUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is StreamUiState.Error -> {
                    Text(
                        text = sessionsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                is StreamUiState.Success -> {
                    val sessions = sessionsState.data
                    if (sessions.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "No saved strategy sessions yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Send your first prompt to automatically create and persist a multi-turn session.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                        ) {
                            items(sessions, key = { it.id }) { session ->
                                val isActive = session.id == activeSessionId
                                val model = StrategistModel.fromModelId(session.modelId)
                                val timestampLabel = session.updatedAt?.toDate()?.let { dateFormatter.format(it) }
                                    ?: "Just now"
                                Surface(
                                    onClick = { if (!isStreaming) onSelectSession(session.id) },
                                    color = if (isActive) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(
                                        width = if (isActive) 1.5.dp else 1.dp,
                                        color = if (isActive) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("strategy_session_item_${session.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = session.title.ifBlank { "Strategy Session" },
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (isActive) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = RoundedCornerShape(50)
                                                    ) {
                                                        Text(
                                                            text = "Active",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            if (session.lastMessagePreview.isNotBlank()) {
                                                Text(
                                                    text = session.lastMessagePreview,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "${model.displayName} · ${session.messageCount} ${if (session.messageCount == 1L) "msg" else "msgs"} · ${session.platform} · $timestampLabel",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        IconButton(
                                            onClick = { onRequestRename(session) },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("strategy_session_rename_${session.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = "Rename ${session.title}",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { onRequestDelete(session) },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("strategy_session_delete_${session.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete ${session.title}",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrategyChatEmptyState(
    language: TargetLanguage,
    platform: TargetPlatform,
    enabled: Boolean,
    recentSessions: List<StrategySession>,
    onSelectStarter: (String) -> Unit,
    onResumeSession: (String) -> Unit
) {
    val starters = listOf(
        "Audit my 0:00-0:03 hook in ${language.label} for ${platform.label}",
        "Build a 5-part content sprint with retention twists",
        "Why do viewers drop off around 0:08 on short-form?"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("strategy_chat_empty_state"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Ask Your Viral Strategist",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Iterate turn-by-turn on hooks, pacing fixes, and ${language.label} scripts for ${platform.label}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            starters.forEachIndexed { index, starter ->
                AssistChip(
                    onClick = { if (enabled) onSelectStarter(starter) },
                    enabled = enabled,
                    label = { Text(starter) },
                    modifier = Modifier.testTag("strategy_starter_chip_$index")
                )
            }
        }

        if (recentSessions.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Resume Previous Session",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                recentSessions.forEach { session ->
                    Surface(
                        onClick = { onResumeSession(session.id) },
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("strategy_resume_recent_${session.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Resume ${session.title}",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.title.ifBlank { "Strategy Session" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${session.messageCount} ${if (session.messageCount == 1L) "msg" else "msgs"} · ${session.lastMessagePreview.ifBlank { session.platform }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
private fun StrategyFollowUpRow(
    platform: TargetPlatform,
    enabled: Boolean,
    onSelectFollowUp: (String) -> Unit
) {
    val followUps = listOf(
        "Give 3 punchier 0:00–0:03 hooks",
        "Suggest B-roll & SFX cuts",
        "Optimize CTA for ${platform.label}"
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .testTag("strategy_follow_up_row")
    ) {
        items(followUps) { suggestion ->
            AssistChip(
                onClick = { if (enabled) onSelectFollowUp(suggestion) },
                enabled = enabled,
                label = {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}

@Composable
private fun StrategyMessageBubble(
    message: ChatMessageRecord,
    onCopyText: (String) -> Unit,
    onOpenUri: (String) -> Unit
) {
    val isUser = message.role == "user"
    var showThinking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isUser) "strategy_user_bubble" else "strategy_model_bubble"),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            border = if (!isUser) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            } else {
                null
            },
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = StrategistModel.fromModelId(message.modelId)
                                .let { "${it.displayName} · ${it.badge}" },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        IconButton(
                            onClick = { onCopyText(message.text) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("strategy_copy_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy response",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (!message.thinkingSummary.isNullOrBlank()) {
                    TextButton(
                        onClick = { showThinking = !showThinking },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (showThinking) "Hide Thought Process" else "View Thought Process",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Icon(
                            imageVector = if (showThinking) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    AnimatedVisibility(visible = showThinking) {
                        Surface(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = message.thinkingSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (message.groundingSources.isNotEmpty()) {
                    GroundingSourcesSection(
                        sources = message.groundingSources,
                        onOpenUri = onOpenUri
                    )
                }
            }
        }
    }
}

@Composable
private fun StrategyStreamingBubble(
    partialText: String,
    partialThoughts: String,
    modelName: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomEnd = 18.dp,
            bottomStart = 4.dp
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
        modifier = Modifier
            .widthIn(max = 560.dp)
            .testTag("strategy_streaming_bubble")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$modelName streaming...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (partialThoughts.isNotBlank()) {
                Text(
                    text = "Thinking: ${partialThoughts.takeLast(220)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (partialText.isNotBlank()) {
                Text(
                    text = partialText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
