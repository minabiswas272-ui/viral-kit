package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TargetLanguage(val label: String, val nativeHint: String) {
    ENGLISH("English", "Global Creator"),
    HINGLISH("Hinglish", "Hindi + English"),
    BENGALI("Bengali", "বাংলা + Creator");

    companion object {
        fun fromLabel(raw: String?): TargetLanguage =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: ENGLISH
    }
}

enum class TargetPlatform(val label: String) {
    INSTAGRAM_REELS("Instagram Reels"),
    YOUTUBE_SHORTS("YouTube Shorts"),
    FACEBOOK_REELS("Facebook Reels"),
    ALL_SHORT_FORM("All Short-Form");

    companion object {
        fun fromLabel(raw: String?): TargetPlatform =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: INSTAGRAM_REELS
    }
}

enum class StrategistModel(
    val modelId: String,
    val displayName: String,
    val badge: String,
    val description: String,
    val usesHighThinking: Boolean = false
) {
    HYDROGEN(
        modelId = "gemini-4-hydrogen",
        displayName = "Hydrogen",
        badge = "General & Search",
        description = "Balanced Reels/Shorts growth strategy with optional live Google Search"
    ),
    CARBON_THINKING(
        modelId = "gemini-4-carbon",
        displayName = "Carbon Pro",
        badge = "High Thinking",
        description = "Deep reasoning mode (ThinkingLevel.HIGH) for retention teardowns",
        usesHighThinking = true
    ),
    ARGON(
        modelId = "gemini-4-argon",
        displayName = "Argon",
        badge = "Complex Campaigns",
        description = "Multi-part series, monetization funnels & complex interactive plans"
    ),
    FLASH_LITE(
        modelId = "gemini-3.1-flash-lite",
        displayName = "Flash-Lite",
        badge = "Low Latency",
        description = "Sub-second hook variations, rapid A/B titles & quick translations"
    );

    companion object {
        fun fromModelId(id: String?): StrategistModel =
            entries.firstOrNull { it.modelId == id } ?: HYDROGEN
    }
}

@Keep
@IgnoreExtraProperties
data class PacingSegment(
    val timestamp: String = "",
    val bRollSuggestion: String = "",
    val onScreenText: String = "",
    val cutAndSfx: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "timestamp" to timestamp.take(40),
        "bRollSuggestion" to bRollSuggestion.take(400),
        "onScreenText" to onScreenText.take(200),
        "cutAndSfx" to cutAndSfx.take(300)
    )
}

@Keep
@IgnoreExtraProperties
data class GroundingSource(
    val title: String = "",
    val uri: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "title" to title.take(150),
        "uri" to uri.take(500)
    )
}

@Keep
@IgnoreExtraProperties
data class CreatorProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val preferredLanguage: String = TargetLanguage.ENGLISH.label,
    val preferredPlatform: String = TargetPlatform.INSTAGRAM_REELS.label,
    val fcmToken: String? = null,
    val fcmNicheTopic: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ViralGrowthKit(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val mediaLabel: String = "",
    val mediaKind: String = "text",
    val language: String = TargetLanguage.ENGLISH.label,
    val platform: String = TargetPlatform.INSTAGRAM_REELS.label,
    val modelUsed: String = StrategistModel.HYDROGEN.modelId,
    val viralScore: Long = 0L,
    val diagnosisCritique: String = "",
    val first30sStrategy: String = "",
    val curiosityHook: String = "",
    val contrarianHook: String = "",
    val directValueHook: String = "",
    val pacingSegments: List<PacingSegment> = emptyList(),
    val instagramCaption: String = "",
    val hashtags: List<String> = emptyList(),
    val youtubeTitle: String = "",
    val youtubeDescription: String = "",
    val thumbnailIdeas: List<String> = emptyList(),
    val rawJson: String = "",
    val trendingTopics: List<String> = emptyList(),
    val trendingTagSources: List<GroundingSource> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TaggedContentIdea(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ideaText: String = "",
    val sourceType: String = "voice",
    val industryNiche: String = "",
    val language: String = TargetLanguage.ENGLISH.label,
    val platform: String = TargetPlatform.INSTAGRAM_REELS.label,
    val extractedKeywords: List<String> = emptyList(),
    val trendingTopics: List<String> = emptyList(),
    val groundingRationale: String = "",
    val groundingSources: List<GroundingSource> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class TrendingTagExtractionResult(
    val extractedKeywords: List<String>,
    val trendingTopics: List<String>,
    val rationale: String,
    val sources: List<GroundingSource>,
    val searchEntryPointHtml: String?
)

@Keep
@IgnoreExtraProperties
data class ChatMessageRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val text: String = "",
    val modelId: String = StrategistModel.HYDROGEN.modelId,
    val thinkingSummary: String? = null,
    val groundingSources: List<GroundingSource> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StrategySession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Strategy Session",
    val lastMessagePreview: String = "",
    val modelId: String = StrategistModel.HYDROGEN.modelId,
    val language: String = TargetLanguage.ENGLISH.label,
    val platform: String = TargetPlatform.INSTAGRAM_REELS.label,
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class MediaAttachment(
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val kind: String // "video" or "audio"
) {
    val sizeMbFormatted: String
        get() = String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
}

data class TrendBriefResult(
    val query: String,
    val summaryText: String,
    val sources: List<GroundingSource>,
    val searchEntryPointHtml: String?
)

enum class TrendAlertFocus(val label: String, val promptDirective: String) {
    AUDIO_TRENDS(
        label = "Viral Audio Trends",
        promptDirective = "breakout trending audio tracks, viral sound remixes, dialogue hooks, and trending Reels/Shorts beat-sync formats"
    ),
    CONTENT_GAPS(
        label = "Niche Content Gaps",
        promptDirective = "underserved niche-specific content gaps, high-demand unanswered creator questions, and low-competition viral topic angles"
    ),
    BOTH(
        label = "Audio + Content Gaps",
        promptDirective = "both breakout viral audio trends AND underserved niche-specific content gaps"
    );

    companion object {
        fun fromName(raw: String?): TrendAlertFocus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BOTH
    }
}

enum class TrendAlertInterval(val hours: Long, val label: String) {
    SIX_HOURS(6L, "Every 6h"),
    TWELVE_HOURS(12L, "Every 12h"),
    DAILY(24L, "Daily");

    companion object {
        fun fromHours(hours: Long): TrendAlertInterval =
            entries.firstOrNull { it.hours == hours } ?: TWELVE_HOURS
    }
}

data class TrendAlertPreferences(
    val enabled: Boolean = false,
    val industryNiche: String = "Indian Tech & AI Creators",
    val focus: TrendAlertFocus = TrendAlertFocus.BOTH,
    val interval: TrendAlertInterval = TrendAlertInterval.TWELVE_HOURS,
    val language: TargetLanguage = TargetLanguage.ENGLISH,
    val platform: TargetPlatform = TargetPlatform.INSTAGRAM_REELS,
    val fcmNichePushEnabled: Boolean = true,
    val strategyCompletePushEnabled: Boolean = true
)

data class LatestTrendAlert(
    val headline: String,
    val alertSummary: String,
    val fullBrief: String,
    val industryNiche: String,
    val focus: TrendAlertFocus,
    val sources: List<GroundingSource>,
    val searchEntryPointHtml: String?,
    val generatedAtMillis: Long
)

