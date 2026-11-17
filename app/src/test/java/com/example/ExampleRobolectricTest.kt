package com.example

import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.KitSyncStatus
import com.example.data.local.ViralKitDatabase
import com.example.data.local.ViralKitEntity
import com.example.data.model.ChatMessageRecord
import com.example.data.model.GroundingSource
import com.example.data.model.LatestTrendAlert
import com.example.data.model.PacingSegment
import com.example.data.model.StrategistModel
import com.example.data.model.TargetLanguage
import com.example.data.model.TargetPlatform
import com.example.data.model.TrendAlertFocus
import com.example.data.model.TrendAlertInterval
import com.example.data.model.TrendAlertPreferences
import com.example.data.model.ViralGrowthKit
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseRestClient
import com.example.export.GrowthKitExporter
import com.example.notifications.FcmPushManager
import com.example.notifications.TrendAlertPrefsStore
import com.example.notifications.TrendNotificationHelper
import com.example.ui.components.isSafeWebUrl
import com.example.ui.screens.StrategyChatContent
import com.example.ui.studio.StreamUiState
import com.example.ui.studio.StreamingChatDraft
import com.example.ui.theme.ViralKitTheme
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ViralKit", appName)
    }

    @Test
    fun `trend alert preferences and latest alert persist cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = TrendAlertPrefsStore(context)

        val prefs = TrendAlertPreferences(
            enabled = true,
            industryNiche = "Personal Finance & Investing",
            focus = TrendAlertFocus.AUDIO_TRENDS,
            interval = TrendAlertInterval.SIX_HOURS,
            language = TargetLanguage.HINGLISH,
            platform = TargetPlatform.YOUTUBE_SHORTS,
            fcmNichePushEnabled = true,
            strategyCompletePushEnabled = false
        )
        store.savePreferences(prefs)
        val loadedPrefs = store.readPreferences()
        assertEquals(prefs, loadedPrefs)
        assertFalse(loadedPrefs.strategyCompletePushEnabled)
        assertTrue(loadedPrefs.fcmNichePushEnabled)

        store.saveFcmToken("test_fcm_token_xyz")
        store.saveSubscribedTopic("niche_personal_finance_investing")
        assertEquals("test_fcm_token_xyz", store.readFcmToken())
        assertEquals("niche_personal_finance_investing", store.readSubscribedTopic())

        val alert = LatestTrendAlert(
            headline = "Breakout Lo-Fi Reel Audio",
            alertSummary = "Creators in Personal Finance are seeing 2.1x saves with this audio.",
            fullBrief = "- Use 118 BPM cut on 0:03 hook\n- Show side-by-side ROI chart",
            industryNiche = "Personal Finance & Investing",
            focus = TrendAlertFocus.AUDIO_TRENDS,
            sources = listOf(
                GroundingSource(title = "Trend Report", uri = "https://example.com/trends")
            ),
            searchEntryPointHtml = null,
            generatedAtMillis = 1_760_000_000_000L
        )
        store.saveLatestAlert(alert)
        val loadedAlert = store.readLatestAlert()
        assertNotNull(loadedAlert)
        assertEquals(alert.headline, loadedAlert?.headline)
        assertEquals(1, loadedAlert?.sources?.size)
        assertEquals("https://example.com/trends", loadedAlert?.sources?.first()?.uri)
    }

    @Test
    fun `fcm topic sanitization formats valid topic names`() {
        assertEquals(
            "niche_indian_tech_ai_creators",
            FcmPushManager.sanitizeNicheTopic("Indian Tech & AI Creators!")
        )
        assertEquals(
            "niche_creator_general",
            FcmPushManager.sanitizeNicheTopic("   ")
        )
    }

    @Test
    fun `notification channels for trend alerts and strategy complete are created on Android O+`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TrendNotificationHelper.ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val trendChannel = manager.getNotificationChannel(TrendNotificationHelper.CHANNEL_ID)
            val strategyChannel = manager.getNotificationChannel(TrendNotificationHelper.CHANNEL_STRATEGY_COMPLETE)
            assertNotNull(trendChannel)
            assertNotNull(strategyChannel)
        }
    }

    @Test
    fun `isSafeWebUrl allows https and blocks dangerous schemes`() {
        assertTrue(isSafeWebUrl("https://trends.google.com/trends/explore?q=reels"))
        assertTrue(isSafeWebUrl("http://example.com/creator-guide"))

        assertFalse(isSafeWebUrl(null))
        assertFalse(isSafeWebUrl("   "))
        assertFalse(isSafeWebUrl("javascript:alert(1)"))
        assertFalse(isSafeWebUrl("file:///etc/passwd"))
        assertFalse(isSafeWebUrl("content://com.example.provider/secret"))
        assertFalse(isSafeWebUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(isSafeWebUrl("https://"))
        assertFalse(isSafeWebUrl("https://example.com/" + "a".repeat(2100)))
    }

    @Test
    fun `room entity round-trip preserves nested lists and fields`() {
        val original = ViralGrowthKit(
            id = "kit_123",
            userId = "user_abc",
            title = "3-Second Reel Hook Formula",
            mediaLabel = "clip_01.mp4 (4.2 MB)",
            mediaKind = "video",
            language = "Hinglish",
            platform = "Instagram Reels",
            modelUsed = "gemini-4-hydrogen",
            viralScore = 88L,
            diagnosisCritique = "Strong hook energy; tighten mid-roll pacing.",
            first30sStrategy = "Open with a visual pattern interrupt at 0:00.",
            curiosityHook = "Wait until you see the 0:04 cut...",
            contrarianHook = "Stop using 3-second intros!",
            directValueHook = "3 editing tricks to double watch time.",
            pacingSegments = listOf(
                PacingSegment("0:00-0:03", "Close-up reaction", "STOP SCROLLING", "Whoosh + 115% punch-in")
            ),
            instagramCaption = "Save this editing breakdown!",
            hashtags = listOf("#ReelsGrowth", "#CreatorTips"),
            youtubeTitle = "Double Your Retention in 30 Seconds",
            youtubeDescription = "Full Reels retention breakdown.",
            thumbnailIdeas = listOf("DON'T POST YET", "3X RETENTION"),
            rawJson = "{\"viral_score\":88}",
            trendingTopics = listOf("CapCut Velocity Edit"),
            trendingTagSources = listOf(GroundingSource("Trends", "https://trends.google.com"))
        )

        val entity = ViralKitEntity.fromDomain(
            kit = original,
            syncStatus = KitSyncStatus.PENDING_CREATE,
            nowMillis = 1_760_000_000_000L
        )
        val restored = entity.toDomain()

        assertEquals(original.id, restored.id)
        assertEquals(original.userId, restored.userId)
        assertEquals(original.title, restored.title)
        assertEquals(original.viralScore, restored.viralScore)
        assertEquals(original.pacingSegments, restored.pacingSegments)
        assertEquals(original.hashtags, restored.hashtags)
        assertEquals(original.thumbnailIdeas, restored.thumbnailIdeas)
        assertEquals(original.trendingTopics, restored.trendingTopics)
        assertEquals(original.trendingTagSources, restored.trendingTagSources)
    }

    @Test
    fun `room cache isolates users preserves pending offline mutations and reconciles remote snapshots`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ViralKitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.viralKitDao()
            val syncedKit = ViralKitEntity.fromDomain(
                kit = ViralGrowthKit(id = "kit_synced", userId = "userA", title = "Synced Kit", viralScore = 76),
                syncStatus = KitSyncStatus.SYNCED,
                nowMillis = 1000L
            )
            val staleSyncedKit = ViralKitEntity.fromDomain(
                kit = ViralGrowthKit(id = "kit_deleted_remotely", userId = "userA", title = "Old Remote Kit", viralScore = 60),
                syncStatus = KitSyncStatus.SYNCED,
                nowMillis = 900L
            )
            val pendingCreateKit = ViralKitEntity.fromDomain(
                kit = ViralGrowthKit(id = "kit_offline_new", userId = "userA", title = "Offline Created Kit", viralScore = 91),
                syncStatus = KitSyncStatus.PENDING_CREATE,
                nowMillis = 2000L
            )
            val pendingUpdateKit = ViralKitEntity.fromDomain(
                kit = ViralGrowthKit(id = "kit_offline_edit", userId = "userA", title = "Renamed Offline Title", viralScore = 84),
                syncStatus = KitSyncStatus.PENDING_UPDATE,
                nowMillis = 1500L
            )
            val pendingDeleteKit = ViralKitEntity.fromDomain(
                kit = ViralGrowthKit(id = "kit_offline_delete", userId = "userA", title = "Deleted Offline", viralScore = 55),
                syncStatus = KitSyncStatus.PENDING_DELETE,
                nowMillis = 1200L
            )
            val otherUserKit = ViralKitEntity.fromDomain(
                kit = ViralGrowthKit(id = "kit_other_user", userId = "userB", title = "Other User Kit", viralScore = 70),
                syncStatus = KitSyncStatus.SYNCED,
                nowMillis = 3000L
            )

            dao.upsertAll(
                listOf(syncedKit, staleSyncedKit, pendingCreateKit, pendingUpdateKit, pendingDeleteKit, otherUserKit)
            )

            // UserA sees SYNCED + PENDING_CREATE + PENDING_UPDATE, never PENDING_DELETE or userB
            val visibleBeforeReconcile = dao.observeKitsForUser("userA").first()
            assertEquals(
                listOf("kit_offline_new", "kit_offline_edit", "kit_synced", "kit_deleted_remotely"),
                visibleBeforeReconcile.map { it.id }
            )
            assertEquals(3, dao.observePendingMutationCount("userA").first())

            // Empty cache snapshot (pruneMissingSynced = false) must NOT wipe offline cache
            dao.reconcileRemoteSnapshot("userA", emptyList(), pruneMissingSynced = false)
            assertEquals(4, dao.getKitsForUser("userA").size)

            // Authoritative server snapshot updates synced kit, adds remote kit, prunes deleted synced kit,
            // and preserves local PENDING_CREATE and PENDING_UPDATE mutations untouched
            val remoteUpdateForPending = ViralKitEntity.fromDomain(
                kit = ViralGrowthKit(id = "kit_offline_edit", userId = "userA", title = "Stale Server Title", viralScore = 84),
                syncStatus = KitSyncStatus.SYNCED,
                nowMillis = 1500L
            )
            val newRemoteKit = ViralKitEntity.fromDomain(
                kit = ViralGrowthKit(id = "kit_from_server", userId = "userA", title = "Created On Web", viralScore = 89),
                syncStatus = KitSyncStatus.SYNCED,
                nowMillis = 2500L
            )
            dao.reconcileRemoteSnapshot(
                userId = "userA",
                remoteKits = listOf(syncedKit, remoteUpdateForPending, newRemoteKit),
                pruneMissingSynced = true
            )

            val visibleAfterReconcile = dao.getKitsForUser("userA")
            assertEquals(
                setOf("kit_from_server", "kit_offline_new", "kit_offline_edit", "kit_synced"),
                visibleAfterReconcile.map { it.id }.toSet()
            )
            assertEquals(
                "Renamed Offline Title",
                visibleAfterReconcile.first { it.id == "kit_offline_edit" }.title
            )
            // Other user's cache remains untouched
            assertEquals(1, dao.getKitsForUser("userB").size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `exporter generates valid PDF document and 1080x1350 PNG share card with FileProvider URIs`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sampleKit = ViralGrowthKit(
            id = "kit_export_test",
            userId = "userA",
            title = "How To Hook Viewers In 2 Seconds!",
            mediaLabel = "reel_draft.mp4",
            mediaKind = "video",
            language = "Hinglish",
            platform = "Instagram Reels",
            modelUsed = "gemini-4-hydrogen",
            viralScore = 92L,
            diagnosisCritique = "High energy opener! Cut the 1.5s silence before the product reveal.",
            first30sStrategy = "Use a fast visual reset at 0:06 and pin a debate question at 0:18.",
            curiosityHook = "99% creators miss THIS 2-second frame...",
            contrarianHook = "Stop posting trending audio without this cut!",
            directValueHook = "Copy this 3-step retention script today.",
            pacingSegments = listOf(
                PacingSegment("0:00-0:03", "Extreme close-up", "DON'T SKIP", "Bass drop + 115% zoom"),
                PacingSegment("0:04-0:12", "Screen recording B-roll", "STEP 1", "Whoosh transition")
            ),
            instagramCaption = "Save this retention checklist before your next Reel!",
            hashtags = listOf("#ReelsStrategy", "#CreatorGrowth", "#ViralHooks"),
            youtubeTitle = "Why Your Reels Drop Off at 3 Seconds",
            youtubeDescription = "Retention pacing breakdown for short-form creators.",
            thumbnailIdeas = listOf("STOP POSTING THIS", "92/100 HOOK"),
            trendingTopics = listOf("Fast-Cut Reels Pacing")
        )

        val pdfArtifact = GrowthKitExporter.exportKitToPdf(context, sampleKit)
        assertTrue(pdfArtifact.file.exists())
        assertTrue(pdfArtifact.file.length() > 100L)
        assertEquals("application/pdf", pdfArtifact.mimeType)
        assertEquals("content", pdfArtifact.uri.scheme)
        val pdfBytes = pdfArtifact.file.readBytes()
        assertEquals("%PDF-", String(pdfBytes, 0, 5, Charsets.US_ASCII))

        val imageArtifact = GrowthKitExporter.exportKitToShareImage(context, sampleKit)
        assertTrue(imageArtifact.file.exists())
        assertTrue(imageArtifact.file.length() > 100L)
        assertEquals("image/png", imageArtifact.mimeType)
        assertEquals("content", imageArtifact.uri.scheme)
        val decodedBitmap = BitmapFactory.decodeFile(imageArtifact.file.absolutePath)
        assertNotNull(decodedBitmap)
        assertEquals(1080, decodedBitmap.width)
        assertEquals(1350, decodedBitmap.height)
        decodedBitmap.recycle()
    }

    @Test
    fun `supabase config rejects placeholders and invalid urls and accepts valid config`() {
        assertFalse(
            SupabaseConfig(
                projectUrl = SupabaseConfig.PLACEHOLDER_URL,
                anonKey = SupabaseConfig.PLACEHOLDER_KEY
            ).isConfigured
        )
        assertFalse(
            SupabaseConfig(
                projectUrl = "https://abc.supabase.co",
                anonKey = SupabaseConfig.PLACEHOLDER_KEY
            ).isConfigured
        )
        assertFalse(
            SupabaseConfig(
                projectUrl = "ftp://abc.supabase.co",
                anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
            ).isConfigured
        )
        assertFalse(
            SupabaseConfig(
                projectUrl = "https://abc.supabase.co",
                anonKey = "   "
            ).isConfigured
        )

        val valid = SupabaseConfig(
            projectUrl = "https://myproject.supabase.co/",
            anonKey = "sb_publishable_test_key"
        )
        assertTrue(valid.isConfigured)
        assertEquals("https://myproject.supabase.co/rest/v1", valid.restBaseUrl)
    }

    @Test
    fun `supabase rest client upserts fetches and deletes kits with expected headers`() = runTest {
        var lastPostPath: String? = null
        var lastPostQuery: String? = null
        var lastPostBody = ""
        var lastApiKey: String? = null
        var lastAuth: String? = null
        var lastDeleteQuery: String? = null

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/rest/v1/viral_growth_kits") { exchange ->
                lastApiKey = exchange.requestHeaders.getFirst("apikey")
                lastAuth = exchange.requestHeaders.getFirst("Authorization")
                when (exchange.requestMethod) {
                    "POST" -> {
                        lastPostPath = exchange.requestURI.path
                        lastPostQuery = exchange.requestURI.query
                        lastPostBody = exchange.requestBody.bufferedReader().readText()
                        exchange.sendResponseHeaders(201, -1)
                    }
                    "GET" -> {
                        val responseBytes = lastPostBody.ifBlank { "[]" }.toByteArray(Charsets.UTF_8)
                        exchange.responseHeaders.add("Content-Type", "application/json")
                        exchange.sendResponseHeaders(200, responseBytes.size.toLong())
                        exchange.responseBody.use { it.write(responseBytes) }
                    }
                    "DELETE" -> {
                        lastDeleteQuery = exchange.requestURI.query
                        exchange.sendResponseHeaders(204, -1)
                    }
                    else -> exchange.sendResponseHeaders(405, -1)
                }
                exchange.close()
            }
            server.start()

            val port = server.address.port
            val client = SupabaseRestClient(
                config = SupabaseConfig(
                    projectUrl = "http://127.0.0.1:$port",
                    anonKey = "test_supabase_anon_key"
                )
            )
            assertTrue(client.isConfigured)

            val sampleKit = ViralGrowthKit(
                id = "kit_supa_1",
                userId = "firebase_uid_42",
                title = "Supabase Synced Reel Strategy",
                mediaLabel = "draft.mp4",
                mediaKind = "video",
                language = "English",
                platform = "Instagram Reels",
                viralScore = 89L,
                curiosityHook = "Nobody talks about this 2-second cut",
                pacingSegments = listOf(
                    PacingSegment("0:00-0:03", "Hook frame", "WATCH THIS", "Snap cut")
                ),
                hashtags = listOf("#CreatorGrowth"),
                thumbnailIdeas = listOf("89 SCORE HOOK"),
                trendingTopics = listOf("Retention Hooks"),
                trendingTagSources = listOf(GroundingSource("Trends", "https://trends.google.com"))
            )

            val upsertRes = client.upsertKits(listOf(sampleKit))
            assertTrue(upsertRes.isSuccess)
            assertEquals("/rest/v1/viral_growth_kits", lastPostPath)
            assertEquals("on_conflict=id", lastPostQuery)
            assertEquals("test_supabase_anon_key", lastApiKey)
            assertEquals("Bearer test_supabase_anon_key", lastAuth)

            val fetchRes = client.fetchKitsForUser("firebase_uid_42")
            assertTrue(fetchRes.isSuccess)
            val fetched = fetchRes.getOrThrow()
            assertEquals(1, fetched.size)
            assertEquals("kit_supa_1", fetched.first().id)
            assertEquals("firebase_uid_42", fetched.first().userId)
            assertEquals(89L, fetched.first().viralScore)
            assertEquals(listOf("Retention Hooks"), fetched.first().trendingTopics)
            assertEquals(1, fetched.first().pacingSegments.size)

            val deleteRes = client.deleteKit("kit_supa_1", "firebase_uid_42")
            assertTrue(deleteRes.isSuccess)
            assertTrue(lastDeleteQuery?.contains("id=eq.kit_supa_1") == true)
            assertTrue(lastDeleteQuery?.contains("user_id=eq.firebase_uid_42") == true)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `strategy chat screen renders multi-turn messages and sends prompt from composer`() {
        val sentPrompts = mutableListOf<String>()
        val conversation = listOf(
            ChatMessageRecord(
                id = "turn_1",
                userId = "userA",
                role = "user",
                text = "How do I fix my 0:05 retention dip?",
                modelId = StrategistModel.HYDROGEN.modelId
            ),
            ChatMessageRecord(
                id = "turn_2",
                userId = "userA",
                role = "model",
                text = "Add a visual pattern interrupt and punch-in zoom at 0:04.",
                modelId = StrategistModel.HYDROGEN.modelId,
                thinkingSummary = "Evaluated short-form viewer drop-off patterns."
            )
        )

        composeTestRule.setContent {
            ViralKitTheme {
                StrategyChatContent(
                    chatState = StreamUiState.Success(conversation),
                    selectedModel = StrategistModel.HYDROGEN,
                    searchGrounding = true,
                    streamDraft = StreamingChatDraft(),
                    lastSearchHtml = null,
                    language = TargetLanguage.ENGLISH,
                    platform = TargetPlatform.INSTAGRAM_REELS,
                    isOnline = true,
                    onSelectModel = {},
                    onToggleSearchGrounding = {},
                    onSendPrompt = { sentPrompts.add(it) },
                    onClearHistory = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("strategy_chat_message_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("strategy_chat_message_list")
            .performScrollToIndex(0)
        composeTestRule.onNodeWithText("How do I fix my 0:05 retention dip?").assertIsDisplayed()

        composeTestRule.onNodeWithTag("strategy_chat_message_list")
            .performScrollToIndex(1)
        composeTestRule.onNodeWithText("Add a visual pattern interrupt and punch-in zoom at 0:04.").assertIsDisplayed()

        composeTestRule.onNodeWithTag("strategy_chat_input")
            .performTextInput("Give me 2 stronger opening hooks")
        composeTestRule.onNodeWithTag("strategy_chat_send_button").performClick()

        assertEquals(listOf("Give me 2 stronger opening hooks"), sentPrompts)
    }
}
