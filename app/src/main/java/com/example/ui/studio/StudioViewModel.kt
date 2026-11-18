package com.example.ui.studio

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ChatStreamEvent
import com.example.data.ai.ViralAiService
import com.example.data.model.ChatMessageRecord
import com.example.data.model.CreatorProfile
import com.example.data.model.GroundingSource
import com.example.data.model.LatestTrendAlert
import com.example.data.model.MediaAttachment
import com.example.data.model.StrategistModel
import com.example.data.model.StrategySession
import com.example.data.model.TaggedContentIdea
import com.example.data.model.TargetLanguage
import com.example.data.model.TargetPlatform
import com.example.data.model.TrendAlertFocus
import com.example.data.model.TrendAlertInterval
import com.example.data.model.TrendAlertPreferences
import com.example.data.model.TrendBriefResult
import com.example.data.model.TrendingTagExtractionResult
import com.example.data.model.ViralGrowthKit
import com.example.data.repository.ViralKitRepository
import com.example.notifications.FcmPushManager
import com.example.notifications.TrendAlertPrefsStore
import com.example.notifications.TrendAlertWorker
import com.example.notifications.TrendNotificationHelper
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

private const val TAG = "StudioViewModel"
private const val STOP_TIMEOUT_MS = 5000L
private const val MAX_INLINE_MEDIA_BYTES = 18L * 1024L * 1024L
private const val MAX_INDUSTRY_CHARS = 120
private const val MAX_NOTES_CHARS = 4000
private const val MAX_TREND_QUERY_CHARS = 240
private const val MAX_QUICK_DRAFT_CHARS = 600
private const val MAX_CHAT_PROMPT_CHARS = 4000
private const val MAX_KIT_TITLE_CHARS = 120

sealed interface StreamUiState<out T> {
    data object Loading : StreamUiState<Nothing>
    data class Success<T>(val data: T) : StreamUiState<T>
    data class Error(val message: String) : StreamUiState<Nothing>
}

sealed interface AnalyzeUiState {
    data object Idle : AnalyzeUiState
    data object Analyzing : AnalyzeUiState
    data class Ready(val kit: ViralGrowthKit, val savedToCloud: Boolean) : AnalyzeUiState
    data class Error(val message: String) : AnalyzeUiState
}

sealed interface TrendUiState {
    data object Idle : TrendUiState
    data object Loading : TrendUiState
    data class Success(val result: TrendBriefResult) : TrendUiState
    data class Error(val message: String) : TrendUiState
}

sealed interface QuickRewriteUiState {
    data object Idle : QuickRewriteUiState
    data object Loading : QuickRewriteUiState
    data class Success(val output: String) : QuickRewriteUiState
    data class Error(val message: String) : QuickRewriteUiState
}

sealed interface IdeaTaggingUiState {
    data object Idle : IdeaTaggingUiState
    data object Extracting : IdeaTaggingUiState
    data class Ready(
        val ideaText: String,
        val result: TrendingTagExtractionResult,
        val savedToCloud: Boolean
    ) : IdeaTaggingUiState
    data class Error(val message: String) : IdeaTaggingUiState
}

data class StreamingChatDraft(
    val isStreaming: Boolean = false,
    val partialText: String = "",
    val partialThoughts: String = "",
    val model: StrategistModel = StrategistModel.HYDROGEN,
    val error: String? = null
)

class StudioViewModel(
    private val appContext: Context,
    private val repository: ViralKitRepository,
    private val aiService: ViralAiService,
    private val alertPrefsStore: TrendAlertPrefsStore,
    private val currentUserId: String,
    private val displayName: String,
    private val email: String?
) : ViewModel() {

    val creatorDisplayName: String get() = displayName.ifBlank { "Creator" }
    val creatorEmail: String? get() = email?.takeIf { it.isNotBlank() }

    val profileState: StateFlow<CreatorProfile?> = repository.observeProfile(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing profile", firestoreError ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val savedKitsState: StateFlow<StreamUiState<List<ViralGrowthKit>>> = repository.observeKits(currentUserId)
        .map<List<ViralGrowthKit>, StreamUiState<List<ViralGrowthKit>>> { StreamUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing saved kits", rootError)
            emit(StreamUiState.Error(rootError.message ?: "Failed to load saved kits"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = StreamUiState.Loading
        )

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private var userRequestedFreshSession = false

    val strategySessionsState: StateFlow<StreamUiState<List<StrategySession>>> =
        repository.observeStrategySessions(currentUserId)
            .onEach { sessions ->
                if (_activeSessionId.value == null && !userRequestedFreshSession && sessions.isNotEmpty()) {
                    _activeSessionId.value = sessions.first().id
                }
            }
            .map<List<StrategySession>, StreamUiState<List<StrategySession>>> { StreamUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing strategy sessions", rootError)
                emit(StreamUiState.Error(rootError.message ?: "Failed to load strategy sessions"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = StreamUiState.Loading
            )

    val activeSession: StateFlow<StrategySession?> = combine(
        strategySessionsState,
        _activeSessionId
    ) { sessionsUi, activeId ->
        val sessions = (sessionsUi as? StreamUiState.Success)?.data.orEmpty()
        sessions.firstOrNull { it.id == activeId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = null
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val chatHistoryState: StateFlow<StreamUiState<List<ChatMessageRecord>>> = _activeSessionId
        .flatMapLatest { sessionId ->
            if (sessionId.isNullOrBlank()) {
                flowOf(StreamUiState.Success(emptyList()))
            } else {
                repository.observeSessionMessages(currentUserId, sessionId)
                    .map<List<ChatMessageRecord>, StreamUiState<List<ChatMessageRecord>>> {
                        StreamUiState.Success(it)
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing chat history", rootError)
                        emit(StreamUiState.Error(rootError.message ?: "Failed to load chat history"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = StreamUiState.Loading
        )

    val taggedIdeasState: StateFlow<StreamUiState<List<TaggedContentIdea>>> = repository.observeTaggedIdeas(currentUserId)
        .map<List<TaggedContentIdea>, StreamUiState<List<TaggedContentIdea>>> { StreamUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing tagged ideas", rootError)
            emit(StreamUiState.Error(rootError.message ?: "Failed to load tagged ideas"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = StreamUiState.Loading
        )

    private val _selectedLanguage = MutableStateFlow(TargetLanguage.ENGLISH)
    val selectedLanguage: StateFlow<TargetLanguage> = _selectedLanguage.asStateFlow()

    private val _selectedPlatform = MutableStateFlow(TargetPlatform.INSTAGRAM_REELS)
    val selectedPlatform: StateFlow<TargetPlatform> = _selectedPlatform.asStateFlow()

    private val _useDeepThinkingForKit = MutableStateFlow(false)
    val useDeepThinkingForKit: StateFlow<Boolean> = _useDeepThinkingForKit.asStateFlow()

    private val _creatorNotes = MutableStateFlow("")
    val creatorNotes: StateFlow<String> = _creatorNotes.asStateFlow()

    private val _selectedMedia = MutableStateFlow<MediaAttachment?>(null)
    val selectedMedia: StateFlow<MediaAttachment?> = _selectedMedia.asStateFlow()

    private val _analyzeState = MutableStateFlow<AnalyzeUiState>(AnalyzeUiState.Idle)
    val analyzeState: StateFlow<AnalyzeUiState> = _analyzeState.asStateFlow()

    private val _trendQuery = MutableStateFlow("Trending Indian Reels & Shorts hooks this week")
    val trendQuery: StateFlow<String> = _trendQuery.asStateFlow()

    private val _trendState = MutableStateFlow<TrendUiState>(TrendUiState.Idle)
    val trendState: StateFlow<TrendUiState> = _trendState.asStateFlow()

    private val _quickDraftInput = MutableStateFlow("")
    val quickDraftInput: StateFlow<String> = _quickDraftInput.asStateFlow()

    private val _quickRewriteState = MutableStateFlow<QuickRewriteUiState>(QuickRewriteUiState.Idle)
    val quickRewriteState: StateFlow<QuickRewriteUiState> = _quickRewriteState.asStateFlow()

    private val _chatModel = MutableStateFlow(StrategistModel.HYDROGEN)
    val chatModel: StateFlow<StrategistModel> = _chatModel.asStateFlow()

    private val _chatSearchGrounding = MutableStateFlow(true)
    val chatSearchGrounding: StateFlow<Boolean> = _chatSearchGrounding.asStateFlow()

    private val _chatStreamDraft = MutableStateFlow(StreamingChatDraft())
    val chatStreamDraft: StateFlow<StreamingChatDraft> = _chatStreamDraft.asStateFlow()

    private val _lastSearchWidgetHtml = MutableStateFlow<String?>(null)
    val lastSearchWidgetHtml: StateFlow<String?> = _lastSearchWidgetHtml.asStateFlow()

    private val _selectedVaultKit = MutableStateFlow<ViralGrowthKit?>(null)
    val selectedVaultKit: StateFlow<ViralGrowthKit?> = _selectedVaultKit.asStateFlow()

    private val _isRefreshingHistory = MutableStateFlow(false)
    val isRefreshingHistory: StateFlow<Boolean> = _isRefreshingHistory.asStateFlow()

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val alertPreferences: StateFlow<TrendAlertPreferences> = alertPrefsStore.preferencesFlow
    val latestTrendAlert: StateFlow<LatestTrendAlert?> = alertPrefsStore.latestAlertFlow
    val fcmToken: StateFlow<String?> = alertPrefsStore.fcmTokenFlow
    val fcmSubscribedTopic: StateFlow<String?> = alertPrefsStore.fcmSubscribedTopicFlow

    private val _isSyncingFcm = MutableStateFlow(false)
    val isSyncingFcm: StateFlow<Boolean> = _isSyncingFcm.asStateFlow()

    private val _fcmSyncNote = MutableStateFlow<String?>(null)
    val fcmSyncNote: StateFlow<String?> = _fcmSyncNote.asStateFlow()

    private val _alertIndustryDraft = MutableStateFlow(alertPrefsStore.readPreferences().industryNiche)
    val alertIndustryDraft: StateFlow<String> = _alertIndustryDraft.asStateFlow()

    private val _isCheckingAlertNow = MutableStateFlow(false)
    val isCheckingAlertNow: StateFlow<Boolean> = _isCheckingAlertNow.asStateFlow()

    private val _alertError = MutableStateFlow<String?>(null)
    val alertError: StateFlow<String?> = _alertError.asStateFlow()

    private val _ideaTaggingState = MutableStateFlow<IdeaTaggingUiState>(IdeaTaggingUiState.Idle)
    val ideaTaggingState: StateFlow<IdeaTaggingUiState> = _ideaTaggingState.asStateFlow()

    private val _taggingKitIds = MutableStateFlow<Set<String>>(emptySet())
    val taggingKitIds: StateFlow<Set<String>> = _taggingKitIds.asStateFlow()

    val isOnline: StateFlow<Boolean> = repository.observeOnlineState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = repository.isCurrentlyOnline()
        )

    val pendingSyncKitIds: StateFlow<Set<String>> = repository.observePendingKitIds(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptySet()
        )

    val pendingSyncCount: StateFlow<Int> = repository.observePendingMutationCount(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = 0
        )

    private val _isSyncingPending = MutableStateFlow(false)
    val isSyncingPending: StateFlow<Boolean> = _isSyncingPending.asStateFlow()

    val isSupabaseConfigured: Boolean
        get() = repository.isSupabaseConfigured

    private val _isSyncingSupabase = MutableStateFlow(false)
    val isSyncingSupabase: StateFlow<Boolean> = _isSyncingSupabase.asStateFlow()

    private val _supabaseStatusNote = MutableStateFlow<String?>(null)
    val supabaseStatusNote: StateFlow<String?> = _supabaseStatusNote.asStateFlow()

    init {
        ensureProfileInitialized()
        TrendNotificationHelper.ensureChannel(appContext)
        syncFcmRegistration(silent = true)
        observeConnectivityAndSync()
        migrateLegacyChatHistoryIfNeeded()
    }

    private fun migrateLegacyChatHistoryIfNeeded() {
        viewModelScope.launch {
            val res = repository.migrateLegacyChatIfNeeded(currentUserId)
            val migratedSessionId = res.getOrNull()
            if (!migratedSessionId.isNullOrBlank() && _activeSessionId.value == null && !userRequestedFreshSession) {
                _activeSessionId.value = migratedSessionId
            }
        }
    }

    private fun observeConnectivityAndSync() {
        viewModelScope.launch {
            repository.observeOnlineState().collect { online ->
                if (online) {
                    syncOfflineKitsInternal(silentIfEmpty = true)
                }
            }
        }
    }

    fun syncOfflineKitsNow() {
        syncOfflineKitsInternal(silentIfEmpty = false)
    }

    private fun syncOfflineKitsInternal(silentIfEmpty: Boolean) {
        if (_isSyncingPending.value) return
        if (!repository.isCurrentlyOnline()) {
            if (!silentIfEmpty) {
                _statusBanner.value = "You're offline. Cached kits will auto-sync to Firestore once connected."
            }
            return
        }
        viewModelScope.launch {
            _isSyncingPending.value = true
            try {
                val summary = repository.syncPendingKitMutations(currentUserId)
                if (summary.syncedCount > 0) {
                    repository.fetchKitsHistory(currentUserId)
                    _statusBanner.value = "Synced ${summary.syncedCount} offline Viral Growth " +
                        "${if (summary.syncedCount == 1) "Kit" else "Kits"} to Firestore."
                } else if (!silentIfEmpty) {
                    _statusBanner.value = if (summary.remainingPendingCount > 0) {
                        "${summary.remainingPendingCount} kit(s) still waiting to sync."
                    } else {
                        "All cached Viral Growth Kits are synced with Firestore."
                    }
                }
            } finally {
                _isSyncingPending.value = false
            }
        }
    }

    fun backupToSupabaseNow() {
        if (_isSyncingSupabase.value) return
        if (!repository.isSupabaseConfigured) {
            val msg = "Add SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets to enable Supabase sync."
            _supabaseStatusNote.value = msg
            _statusBanner.value = msg
            return
        }
        viewModelScope.launch {
            _isSyncingSupabase.value = true
            try {
                val res = repository.syncAllKitsToSupabase(currentUserId)
                if (res.isSuccess) {
                    val count = res.getOrDefault(0)
                    val msg = if (count == 0) {
                        "Supabase connected — no saved kits to back up yet."
                    } else {
                        "Backed up $count Viral Growth ${if (count == 1) "Kit" else "Kits"} to Supabase."
                    }
                    _supabaseStatusNote.value = msg
                    _statusBanner.value = msg
                } else {
                    val errMsg = res.exceptionOrNull()?.localizedMessage ?: "Supabase backup failed."
                    _supabaseStatusNote.value = errMsg
                    _statusBanner.value = errMsg
                }
            } finally {
                _isSyncingSupabase.value = false
            }
        }
    }

    fun restoreFromSupabaseNow() {
        if (_isSyncingSupabase.value) return
        if (!repository.isSupabaseConfigured) {
            val msg = "Add SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets to restore from Supabase."
            _supabaseStatusNote.value = msg
            _statusBanner.value = msg
            return
        }
        viewModelScope.launch {
            _isSyncingSupabase.value = true
            try {
                val res = repository.pullKitsFromSupabase(currentUserId)
                if (res.isSuccess) {
                    val count = res.getOrDefault(0)
                    val msg = if (count == 0) {
                        "Supabase restore complete — no new kits found."
                    } else {
                        "Restored $count Viral Growth ${if (count == 1) "Kit" else "Kits"} from Supabase."
                    }
                    _supabaseStatusNote.value = msg
                    _statusBanner.value = msg
                } else {
                    val errMsg = res.exceptionOrNull()?.localizedMessage ?: "Supabase restore failed."
                    _supabaseStatusNote.value = errMsg
                    _statusBanner.value = errMsg
                }
            } finally {
                _isSyncingSupabase.value = false
            }
        }
    }

    private fun ensureProfileInitialized() {
        viewModelScope.launch {
            repository.upsertProfile(
                displayName = displayName,
                email = email,
                language = _selectedLanguage.value,
                platform = _selectedPlatform.value
            )
        }
    }

    fun syncFcmRegistration(silent: Boolean = false) {
        if (_isSyncingFcm.value) return
        val prefs = alertPrefsStore.readPreferences()
        val niche = _alertIndustryDraft.value.trim().ifEmpty { prefs.industryNiche }
        viewModelScope.launch {
            _isSyncingFcm.value = true
            if (!silent) _fcmSyncNote.value = null
            try {
                val res = FcmPushManager.syncFcmRegistration(
                    repository = repository,
                    prefsStore = alertPrefsStore,
                    niche = niche,
                    subscribeToTopic = prefs.fcmNichePushEnabled
                )
                if (res.errorMessage != null) {
                    _fcmSyncNote.value = res.errorMessage
                    if (!silent) {
                        _statusBanner.value = res.errorMessage
                    }
                } else {
                    val topicInfo = res.subscribedTopic?.let { " · Topic: $it" }.orEmpty()
                    _fcmSyncNote.value = if (res.syncedToFirestore) {
                        "FCM token synced to Firestore$topicInfo"
                    } else {
                        "FCM registered locally$topicInfo"
                    }
                    if (!silent) {
                        _statusBanner.value = "FCM push registration synced$topicInfo"
                    }
                }
            } finally {
                _isSyncingFcm.value = false
            }
        }
    }

    fun setStrategyCompletePushEnabled(enabled: Boolean) {
        val current = alertPrefsStore.readPreferences()
        alertPrefsStore.savePreferences(current.copy(strategyCompletePushEnabled = enabled))
        _statusBanner.value = if (enabled) {
            "AI strategy completion notifications enabled."
        } else {
            "AI strategy completion notifications paused."
        }
    }

    fun setFcmNichePushEnabled(enabled: Boolean) {
        val niche = _alertIndustryDraft.value.trim()
            .take(MAX_INDUSTRY_CHARS)
            .ifEmpty { "Indian Tech & AI Creators" }
        _alertIndustryDraft.value = niche
        val current = alertPrefsStore.readPreferences()
        alertPrefsStore.savePreferences(
            current.copy(
                industryNiche = niche,
                fcmNichePushEnabled = enabled
            )
        )
        syncFcmRegistration(silent = false)
    }

    fun selectLanguage(language: TargetLanguage) {
        _selectedLanguage.value = language
        val currentPrefs = alertPrefsStore.readPreferences()
        alertPrefsStore.savePreferences(currentPrefs.copy(language = language))
        viewModelScope.launch {
            repository.upsertProfile(displayName, email, language, _selectedPlatform.value)
        }
    }

    fun selectPlatform(platform: TargetPlatform) {
        _selectedPlatform.value = platform
        val currentPrefs = alertPrefsStore.readPreferences()
        alertPrefsStore.savePreferences(currentPrefs.copy(platform = platform))
        viewModelScope.launch {
            repository.upsertProfile(displayName, email, _selectedLanguage.value, platform)
        }
    }

    fun updateAlertIndustry(niche: String) {
        val bounded = niche.take(MAX_INDUSTRY_CHARS)
        _alertIndustryDraft.value = bounded
        if (bounded.trim().isNotEmpty()) {
            val current = alertPrefsStore.readPreferences()
            alertPrefsStore.savePreferences(current.copy(industryNiche = bounded.trim()))
        }
    }

    fun selectAlertFocus(focus: TrendAlertFocus) {
        val current = alertPrefsStore.readPreferences()
        alertPrefsStore.savePreferences(current.copy(focus = focus))
    }

    fun selectAlertInterval(interval: TrendAlertInterval) {
        val current = alertPrefsStore.readPreferences()
        alertPrefsStore.savePreferences(current.copy(interval = interval))
        if (current.enabled) {
            TrendAlertWorker.schedulePeriodic(appContext, interval.hours)
            _statusBanner.value = "Trend alerts rescheduled (${interval.label})."
        }
    }

    fun setPeriodicAlertsEnabled(enabled: Boolean) {
        val niche = _alertIndustryDraft.value.trim()
            .take(MAX_INDUSTRY_CHARS)
            .ifEmpty { "Indian Tech & AI Creators" }
        _alertIndustryDraft.value = niche
        val current = alertPrefsStore.readPreferences()
        val updated = current.copy(
            enabled = enabled,
            industryNiche = niche,
            language = _selectedLanguage.value,
            platform = _selectedPlatform.value
        )
        alertPrefsStore.savePreferences(updated)
        if (enabled) {
            TrendNotificationHelper.ensureChannel(appContext)
            TrendAlertWorker.schedulePeriodic(appContext, updated.interval.hours)
            _statusBanner.value = "Periodic ${updated.focus.label} alerts scheduled (${updated.interval.label})."
        } else {
            TrendAlertWorker.cancelPeriodic(appContext)
            _statusBanner.value = "Periodic trend alerts paused."
        }
    }

    fun sendGroundedAlertNow() {
        if (_isCheckingAlertNow.value) return
        val niche = _alertIndustryDraft.value.trim()
            .take(MAX_INDUSTRY_CHARS)
            .ifEmpty {
                _alertError.value = "Enter your creator industry or niche first."
                return
            }
        val current = alertPrefsStore.readPreferences().copy(
            industryNiche = niche,
            language = _selectedLanguage.value,
            platform = _selectedPlatform.value
        )
        alertPrefsStore.savePreferences(current)

        viewModelScope.launch {
            _isCheckingAlertNow.value = true
            _alertError.value = null
            try {
                val alert = aiService.fetchIndustryTrendAlert(
                    industryNiche = niche,
                    focus = current.focus,
                    language = current.language,
                    platform = current.platform
                )
                alertPrefsStore.saveLatestAlert(alert)
                val posted = TrendNotificationHelper.postTrendNotification(appContext, alert)
                _statusBanner.value = if (posted) {
                    "Grounded notification sent for $niche!"
                } else {
                    "Grounded alert ready below (enable Notifications to receive push alerts)."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Grounded alert check timed out", e)
                _alertError.value = "Google Search alert timed out. Please retry."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Grounded alert check failed", e)
                _alertError.value = e.localizedMessage ?: "Failed to fetch grounded trend alert."
            } finally {
                _isCheckingAlertNow.value = false
            }
        }
    }

    fun setUseDeepThinkingForKit(enabled: Boolean) {
        _useDeepThinkingForKit.value = enabled
    }

    fun updateCreatorNotes(notes: String) {
        _creatorNotes.value = notes.take(MAX_NOTES_CHARS)
    }

    fun applyVoiceTranscript(
        transcript: String,
        contentResolver: ContentResolver,
        triggerInstantAnalysis: Boolean
    ) {
        val cleaned = transcript.trim().take(MAX_NOTES_CHARS)
        if (cleaned.isEmpty()) return
        val existing = _creatorNotes.value.trim()
        _creatorNotes.value = (if (existing.isEmpty()) cleaned else "$existing\n$cleaned")
            .take(MAX_NOTES_CHARS)
        // Automatically extract keywords & tag the recorded voice idea with live trending industry topics
        extractAndTagRecordedIdea(
            ideaText = cleaned,
            sourceType = "voice",
            saveAsTaggedIdea = true
        )
        if (triggerInstantAnalysis) {
            generateViralGrowthKit(contentResolver)
        } else {
            _statusBanner.value = "Voice idea recorded — auto-tagging with Google Search trends..."
        }
    }

    fun extractTagsForCurrentDraft() {
        val notes = _creatorNotes.value.trim()
        if (notes.isEmpty()) {
            _ideaTaggingState.value = IdeaTaggingUiState.Error(
                "Record a voice idea or enter creator notes first to extract trending topic tags."
            )
            return
        }
        extractAndTagRecordedIdea(
            ideaText = notes,
            sourceType = "note",
            saveAsTaggedIdea = true
        )
    }

    fun extractAndTagRecordedIdea(
        ideaText: String,
        sourceType: String = "voice",
        saveAsTaggedIdea: Boolean = true
    ) {
        val cleanIdea = ideaText.trim().take(MAX_NOTES_CHARS)
        if (cleanIdea.isEmpty()) return
        val niche = alertPrefsStore.readPreferences().industryNiche
        val lang = _selectedLanguage.value
        val plat = _selectedPlatform.value

        viewModelScope.launch {
            _ideaTaggingState.value = IdeaTaggingUiState.Extracting
            try {
                val result = aiService.extractTrendingTopicTags(
                    ideaText = cleanIdea,
                    industryNiche = niche,
                    language = lang,
                    platform = plat
                )
                var saved = false
                if (saveAsTaggedIdea) {
                    val record = TaggedContentIdea(
                        userId = currentUserId,
                        ideaText = cleanIdea,
                        sourceType = sourceType,
                        industryNiche = niche,
                        language = lang.label,
                        platform = plat.label,
                        extractedKeywords = result.extractedKeywords,
                        trendingTopics = result.trendingTopics,
                        groundingRationale = result.rationale,
                        groundingSources = result.sources
                    )
                    saved = repository.saveTaggedIdea(record).isSuccess
                }
                _ideaTaggingState.value = IdeaTaggingUiState.Ready(
                    ideaText = cleanIdea,
                    result = result,
                    savedToCloud = saved
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Automated keyword extraction timed out", e)
                _ideaTaggingState.value = IdeaTaggingUiState.Error(
                    "Google Search topic extraction timed out. Tap Auto-Tag Idea to retry."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Automated keyword extraction failed", e)
                _ideaTaggingState.value = IdeaTaggingUiState.Error(
                    e.localizedMessage ?: "Failed to extract trending industry topics."
                )
            }
        }
    }

    fun deleteTaggedIdea(ideaId: String) {
        if (ideaId.isBlank()) return
        viewModelScope.launch {
            val res = repository.deleteTaggedIdea(ideaId)
            if (res.isFailure) {
                _statusBanner.value = "Failed to remove tagged idea: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun auditTaggedIdea(idea: TaggedContentIdea, contentResolver: ContentResolver) {
        _creatorNotes.value = idea.ideaText
        _ideaTaggingState.value = IdeaTaggingUiState.Ready(
            ideaText = idea.ideaText,
            result = TrendingTagExtractionResult(
                extractedKeywords = idea.extractedKeywords,
                trendingTopics = idea.trendingTopics,
                rationale = idea.groundingRationale,
                sources = idea.groundingSources,
                searchEntryPointHtml = null
            ),
            savedToCloud = true
        )
        generateViralGrowthKit(contentResolver)
    }

    fun tagSavedKitWithTrendingTopics(kit: ViralGrowthKit) {
        if (kit.id.isBlank() || _taggingKitIds.value.contains(kit.id)) return
        val ideaSeed = buildString {
            append(kit.title)
            if (kit.curiosityHook.isNotBlank()) append(" — ").append(kit.curiosityHook)
            if (kit.diagnosisCritique.isNotBlank()) append(" ").append(kit.diagnosisCritique.take(180))
        }.trim()
        val niche = alertPrefsStore.readPreferences().industryNiche

        viewModelScope.launch {
            _taggingKitIds.value = _taggingKitIds.value + kit.id
            try {
                val result = aiService.extractTrendingTopicTags(
                    ideaText = ideaSeed,
                    industryNiche = niche,
                    language = TargetLanguage.fromLabel(kit.language),
                    platform = TargetPlatform.fromLabel(kit.platform)
                )
                val updateRes = repository.updateKitTrendingTopics(
                    kitId = kit.id,
                    trendingTopics = result.trendingTopics,
                    sources = result.sources
                )
                if (updateRes.isSuccess) {
                    if (_selectedVaultKit.value?.id == kit.id) {
                        _selectedVaultKit.value = _selectedVaultKit.value?.copy(
                            trendingTopics = result.trendingTopics,
                            trendingTagSources = result.sources
                        )
                    }
                    _statusBanner.value = "Tagged \"${kit.title}\" with ${result.trendingTopics.size} trending topics."
                } else {
                    _statusBanner.value = "Tag update failed: ${updateRes.exceptionOrNull()?.localizedMessage}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to tag saved kit", e)
                _statusBanner.value = e.localizedMessage ?: "Failed to tag kit with trending topics."
            } finally {
                _taggingKitIds.value = _taggingKitIds.value - kit.id
            }
        }
    }

    fun attachMediaFromUri(contentResolver: ContentResolver, uri: Uri, kind: String) {
        viewModelScope.launch {
            try {
                val attachment = withContext(Dispatchers.IO) {
                    val mime = contentResolver.getType(uri)
                        ?: if (kind == "video") "video/mp4" else "audio/mpeg"
                    var fileName = if (kind == "video") "selected_video.mp4" else "selected_audio.mp3"
                    var fileSize = -1L

                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                            if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                        }
                    }

                    if (fileSize < 0L) {
                        contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                            fileSize = afd.length
                        }
                    }

                    MediaAttachment(
                        uriString = uri.toString(),
                        displayName = fileName,
                        mimeType = mime,
                        sizeBytes = fileSize.coerceAtLeast(0L),
                        kind = kind
                    )
                }

                if (attachment.sizeBytes > MAX_INLINE_MEDIA_BYTES) {
                    _statusBanner.value = "File is ${attachment.sizeMbFormatted}. Keep video/audio clips under 18 MB for instant AI analysis."
                    return@launch
                }

                _selectedMedia.value = attachment
                _analyzeState.value = AnalyzeUiState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inspect selected media", e)
                _statusBanner.value = e.localizedMessage ?: "Could not read selected file."
            }
        }
    }

    fun clearSelectedMedia() {
        _selectedMedia.value = null
    }

    fun generateViralGrowthKit(contentResolver: ContentResolver) {
        val attachment = _selectedMedia.value
        val notes = _creatorNotes.value.trim()
        if (attachment == null && notes.isEmpty()) {
            _analyzeState.value = AnalyzeUiState.Error(
                "Pick a short video/audio clip or describe your Reel/Short topic first."
            )
            return
        }

        viewModelScope.launch {
            _analyzeState.value = AnalyzeUiState.Analyzing
            try {
                val mediaBytes: ByteArray? = if (attachment != null) {
                    withContext(Dispatchers.IO) {
                        val uri = Uri.parse(attachment.uriString)
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val bytes = stream.readBytes()
                            if (bytes.size.toLong() > MAX_INLINE_MEDIA_BYTES) {
                                throw IllegalArgumentException(
                                    "Media exceeds 18 MB inline limit (${bytes.size / (1024 * 1024)} MB). Trim clip to under 18 MB."
                                )
                            }
                            bytes
                        } ?: throw IllegalStateException("Unable to open selected media stream.")
                    }
                } else {
                    null
                }

                val label = attachment?.let { "${it.displayName} (${it.sizeMbFormatted})" }
                    ?: notes.take(60)
                val kind = attachment?.kind ?: "text"
                val niche = alertPrefsStore.readPreferences().industryNiche
                val existingReady = (_ideaTaggingState.value as? IdeaTaggingUiState.Ready)
                    ?.takeIf { it.ideaText == notes }

                val (rawKit, autoTags) = supervisorScope {
                    val kitDeferred = async {
                        aiService.analyzeForViralGrowthKit(
                            userId = currentUserId,
                            mediaBytes = mediaBytes,
                            mediaMimeType = attachment?.mimeType,
                            mediaLabel = label,
                            mediaKind = kind,
                            creatorNotes = notes,
                            language = _selectedLanguage.value,
                            platform = _selectedPlatform.value,
                            useDeepThinking = _useDeepThinkingForKit.value
                        )
                    }
                    val tagsDeferred = async {
                        if (existingReady != null) {
                            existingReady.result
                        } else {
                            val tagSeed = notes.ifBlank { label }
                            runCatching {
                                aiService.extractTrendingTopicTags(
                                    ideaText = tagSeed,
                                    industryNiche = niche,
                                    language = _selectedLanguage.value,
                                    platform = _selectedPlatform.value
                                )
                            }.getOrNull()
                        }
                    }
                    kitDeferred.await() to tagsDeferred.await()
                }

                val generatedKit = if (autoTags != null && autoTags.trendingTopics.isNotEmpty()) {
                    if (existingReady == null && notes.isNotBlank()) {
                        _ideaTaggingState.value = IdeaTaggingUiState.Ready(
                            ideaText = notes,
                            result = autoTags,
                            savedToCloud = true
                        )
                    }
                    rawKit.copy(
                        trendingTopics = autoTags.trendingTopics,
                        trendingTagSources = autoTags.sources
                    )
                } else {
                    rawKit
                }

                val saveResult = repository.saveKit(generatedKit)
                val savedId = saveResult.getOrNull()
                val displayKit = if (savedId != null) {
                    generatedKit.copy(id = savedId)
                } else {
                    generatedKit
                }

                _analyzeState.value = AnalyzeUiState.Ready(
                    kit = displayKit,
                    savedToCloud = saveResult.isSuccess
                )
                if (alertPrefsStore.readPreferences().strategyCompletePushEnabled) {
                    TrendNotificationHelper.postStrategyCompleteNotification(appContext, displayKit)
                }
                if (saveResult.isFailure) {
                    _statusBanner.value = "Generated kit, but save failed: ${saveResult.exceptionOrNull()?.localizedMessage}"
                } else if (!repository.isCurrentlyOnline()) {
                    _statusBanner.value = "Saved offline to Room cache — will sync to Firestore when connected."
                } else {
                    _statusBanner.value = "Viral Growth Kit generated, tagged, and saved to Vault."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Analysis timed out", e)
                _analyzeState.value = AnalyzeUiState.Error(
                    "Analysis timed out after 60 seconds. Try a shorter clip or standard mode."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate Viral Growth Kit", e)
                _analyzeState.value = AnalyzeUiState.Error(
                    e.localizedMessage ?: "Failed to analyze content."
                )
            }
        }
    }

    fun updateTrendQuery(query: String) {
        _trendQuery.value = query.take(MAX_TREND_QUERY_CHARS)
    }

    fun searchLiveTrends(customQuery: String? = null) {
        val q = (customQuery ?: _trendQuery.value).trim().take(MAX_TREND_QUERY_CHARS)
        if (q.isEmpty()) {
            _trendState.value = TrendUiState.Error("Enter a creator niche or topic to search live trends.")
            return
        }
        _trendQuery.value = q
        viewModelScope.launch {
            _trendState.value = TrendUiState.Loading
            try {
                val result = aiService.fetchLiveCreatorTrends(
                    nicheQuery = q,
                    language = _selectedLanguage.value,
                    platform = _selectedPlatform.value
                )
                _trendState.value = TrendUiState.Success(result)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Trend search timed out", e)
                _trendState.value = TrendUiState.Error("Google Search trend lookup timed out. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Trend search failed", e)
                _trendState.value = TrendUiState.Error(
                    e.localizedMessage ?: "Failed to fetch live creator trends."
                )
            }
        }
    }

    fun updateQuickDraftInput(text: String) {
        _quickDraftInput.value = text.take(MAX_QUICK_DRAFT_CHARS)
    }

    fun runQuickHookRewrite() {
        val input = _quickDraftInput.value.trim().take(MAX_QUICK_DRAFT_CHARS)
        if (input.isEmpty()) {
            _quickRewriteState.value = QuickRewriteUiState.Error("Paste a hook, title, or topic first.")
            return
        }
        viewModelScope.launch {
            _quickRewriteState.value = QuickRewriteUiState.Loading
            try {
                val output = aiService.quickRewriteHooks(
                    draftHookOrTopic = input,
                    language = _selectedLanguage.value,
                    platform = _selectedPlatform.value
                )
                _quickRewriteState.value = QuickRewriteUiState.Success(output)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Quick rewrite timed out", e)
                _quickRewriteState.value = QuickRewriteUiState.Error("Fast rewrite timed out. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Quick rewrite failed", e)
                _quickRewriteState.value = QuickRewriteUiState.Error(
                    e.localizedMessage ?: "Hook rewrite failed."
                )
            }
        }
    }

    fun selectChatModel(model: StrategistModel) {
        _chatModel.value = model
    }

    fun toggleChatSearchGrounding(enabled: Boolean) {
        _chatSearchGrounding.value = enabled
    }

    fun startNewStrategySession() {
        if (_chatStreamDraft.value.isStreaming) return
        userRequestedFreshSession = true
        _activeSessionId.value = null
        _lastSearchWidgetHtml.value = null
        _chatStreamDraft.value = StreamingChatDraft(model = _chatModel.value)
    }

    fun selectStrategySession(sessionId: String) {
        if (sessionId.isBlank() || _chatStreamDraft.value.isStreaming) return
        userRequestedFreshSession = false
        _activeSessionId.value = sessionId
        _lastSearchWidgetHtml.value = null
        _chatStreamDraft.value = StreamingChatDraft(model = _chatModel.value)
        val sessions = (strategySessionsState.value as? StreamUiState.Success)?.data.orEmpty()
        sessions.firstOrNull { it.id == sessionId }?.let { session ->
            _chatModel.value = StrategistModel.fromModelId(session.modelId)
        }
    }

    fun renameStrategySession(sessionId: String, newTitle: String) {
        val trimmed = newTitle.trim().take(MAX_KIT_TITLE_CHARS)
        if (sessionId.isBlank() || trimmed.isEmpty()) {
            _statusBanner.value = "Session title cannot be empty."
            return
        }
        viewModelScope.launch {
            val res = repository.renameStrategySession(sessionId, trimmed)
            if (res.isSuccess) {
                _statusBanner.value = "Renamed strategy session to \"$trimmed\"."
            } else {
                _statusBanner.value = "Rename failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun deleteStrategySession(sessionId: String) {
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            val res = repository.deleteStrategySession(sessionId)
            if (res.isSuccess) {
                if (_activeSessionId.value == sessionId) {
                    userRequestedFreshSession = false
                    _activeSessionId.value = null
                    _lastSearchWidgetHtml.value = null
                }
                _statusBanner.value = "Deleted strategy session."
            } else {
                _statusBanner.value = "Delete failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun sendChatPrompt(rawPrompt: String, customSessionTitle: String? = null) {
        val prompt = rawPrompt.trim().take(MAX_CHAT_PROMPT_CHARS)
        if (prompt.isEmpty() || _chatStreamDraft.value.isStreaming) return

        val targetSessionId = _activeSessionId.value
        val currentHistory = if (targetSessionId.isNullOrBlank()) {
            emptyList()
        } else {
            (chatHistoryState.value as? StreamUiState.Success)?.data.orEmpty()
        }
        val chosenModel = _chatModel.value
        val useGrounding = _chatSearchGrounding.value && chosenModel == StrategistModel.HYDROGEN
        val currentLanguage = _selectedLanguage.value
        val currentPlatform = _selectedPlatform.value
        userRequestedFreshSession = false

        viewModelScope.launch {
            _chatStreamDraft.value = StreamingChatDraft(
                isStreaming = true,
                partialText = "",
                partialThoughts = "",
                model = chosenModel,
                error = null
            )
            _lastSearchWidgetHtml.value = null

            val userMsg = ChatMessageRecord(
                userId = currentUserId,
                role = "user",
                text = prompt,
                modelId = chosenModel.modelId
            )
            val userSaveResult = repository.appendMessageToSession(
                sessionId = targetSessionId,
                message = userMsg,
                language = currentLanguage,
                platform = currentPlatform,
                sessionTitleIfNew = customSessionTitle
            )
            val resolvedSessionId = userSaveResult.getOrNull() ?: targetSessionId
            if (resolvedSessionId != null && _activeSessionId.value != resolvedSessionId) {
                _activeSessionId.value = resolvedSessionId
            }
            if (userSaveResult.isFailure) {
                _statusBanner.value = "Could not persist message: ${userSaveResult.exceptionOrNull()?.localizedMessage}"
            }

            try {
                val textAcc = StringBuilder()
                val thoughtAcc = StringBuilder()
                var finalText = ""
                var finalThoughts: String? = null
                var finalSources = emptyList<GroundingSource>()

                aiService.sendChatMessageStream(
                    historyRecords = currentHistory,
                    userPrompt = prompt,
                    modelChoice = chosenModel,
                    enableSearchGrounding = useGrounding
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Chunk -> {
                            textAcc.append(event.textDelta)
                            thoughtAcc.append(event.thoughtDelta)
                            _chatStreamDraft.value = _chatStreamDraft.value.copy(
                                partialText = textAcc.toString(),
                                partialThoughts = thoughtAcc.toString()
                            )
                        }
                        is ChatStreamEvent.Done -> {
                            finalText = event.fullText
                            finalThoughts = event.fullThoughts
                            finalSources = event.groundingSources
                            _lastSearchWidgetHtml.value = event.searchEntryPointHtml
                        }
                    }
                }

                val modelMsg = ChatMessageRecord(
                    userId = currentUserId,
                    role = "model",
                    text = finalText,
                    modelId = chosenModel.modelId,
                    thinkingSummary = finalThoughts,
                    groundingSources = finalSources
                )
                repository.appendMessageToSession(
                    sessionId = resolvedSessionId,
                    message = modelMsg,
                    language = currentLanguage,
                    platform = currentPlatform,
                    sessionTitleIfNew = customSessionTitle
                )
                _chatStreamDraft.value = StreamingChatDraft(model = chosenModel)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat stream timed out", e)
                _chatStreamDraft.value = StreamingChatDraft(
                    isStreaming = false,
                    model = chosenModel,
                    error = "Response timed out. Please try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat stream failed", e)
                _chatStreamDraft.value = StreamingChatDraft(
                    isStreaming = false,
                    model = chosenModel,
                    error = e.localizedMessage ?: "Failed to generate strategist reply."
                )
            }
        }
    }

    fun clearChatHistory() {
        val currentSessionId = _activeSessionId.value
        if (!currentSessionId.isNullOrBlank()) {
            deleteStrategySession(currentSessionId)
            return
        }
        viewModelScope.launch {
            val res = repository.clearChatHistory()
            _lastSearchWidgetHtml.value = null
            if (res.isFailure) {
                _statusBanner.value = "Failed to clear chat: ${res.exceptionOrNull()?.localizedMessage}"
            } else {
                _statusBanner.value = "Strategist conversation cleared."
            }
        }
    }

    fun openVaultKit(kit: ViralGrowthKit?) {
        _selectedVaultKit.value = kit
    }

    fun openVaultKitById(kitId: String?) {
        val kits = (savedKitsState.value as? StreamUiState.Success)?.data.orEmpty()
        val match = when {
            kitId.isNullOrBlank() -> null
            kitId == "latest" -> kits.firstOrNull()
            else -> kits.firstOrNull { it.id == kitId } ?: kits.firstOrNull()
        }
        _selectedVaultKit.value = match
    }

    fun updateHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun refreshKitsHistory() {
        if (_isRefreshingHistory.value) return
        viewModelScope.launch {
            _isRefreshingHistory.value = true
            val result = repository.fetchKitsHistory(currentUserId)
            _isRefreshingHistory.value = false
            if (result.isSuccess) {
                val count = result.getOrNull()?.size ?: 0
                _statusBanner.value = if (repository.isCurrentlyOnline()) {
                    "Synced $count saved Viral Growth ${if (count == 1) "Kit" else "Kits"} with Firestore."
                } else {
                    "Offline mode: showing $count cached Viral Growth ${if (count == 1) "Kit" else "Kits"} from Room."
                }
            } else {
                _statusBanner.value = "Refresh failed: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun renameVaultKit(kitId: String, newTitle: String) {
        val trimmed = newTitle.trim().take(MAX_KIT_TITLE_CHARS)
        if (kitId.isBlank() || trimmed.isEmpty()) {
            _statusBanner.value = "Strategy title cannot be empty."
            return
        }
        viewModelScope.launch {
            val res = repository.updateKitTitle(kitId, trimmed)
            if (res.isSuccess) {
                if (_selectedVaultKit.value?.id == kitId) {
                    _selectedVaultKit.value = _selectedVaultKit.value?.copy(title = trimmed)
                }
                _statusBanner.value = "Renamed strategy kit to \"$trimmed\"."
            } else {
                _statusBanner.value = "Rename failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun deleteVaultKit(kitId: String) {
        if (kitId.isBlank()) return
        viewModelScope.launch {
            val res = repository.deleteKit(kitId)
            if (res.isSuccess) {
                if (_selectedVaultKit.value?.id == kitId) {
                    _selectedVaultKit.value = null
                }
                _statusBanner.value = "Removed kit from Vault."
            } else {
                _statusBanner.value = "Delete failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun discussKitInChat(kit: ViralGrowthKit) {
        startNewStrategySession()
        userRequestedFreshSession = false
        val prompt = """
            Let's optimize my "${kit.title}" kit (${kit.platform}, ${kit.language}, Viral Score ${kit.viralScore}/100).
            Critique received: ${kit.diagnosisCritique}
            Give me 2 alternative retention twists for the 0:05-0:15 mid-roll dip and a tighter CTA!
        """.trimIndent()
        sendChatPrompt(
            rawPrompt = prompt,
            customSessionTitle = "Kit: ${kit.title.take(45)}"
        )
    }

    fun consumeStatusBanner() {
        _statusBanner.value = null
    }
}
