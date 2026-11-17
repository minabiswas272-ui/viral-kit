package com.example.data.ai

import com.example.data.model.ChatMessageRecord
import com.example.data.model.GroundingSource
import com.example.data.model.LatestTrendAlert
import com.example.data.model.PacingSegment
import com.example.data.model.StrategistModel
import com.example.data.model.TargetLanguage
import com.example.data.model.TargetPlatform
import com.example.data.model.TrendAlertFocus
import com.example.data.model.TrendBriefResult
import com.example.data.model.TrendingTagExtractionResult
import com.example.data.model.ViralGrowthKit
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

sealed interface ChatStreamEvent {
    data class Chunk(val textDelta: String, val thoughtDelta: String) : ChatStreamEvent
    data class Done(
        val fullText: String,
        val fullThoughts: String?,
        val groundingSources: List<GroundingSource>,
        val searchEntryPointHtml: String?
    ) : ChatStreamEvent
}

class ViralAiService {

    private val aiBackend by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    private val viralGrowthKitSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "viralScore" to Schema.integer(description = "Retention potential score out of 100"),
                "diagnosisCritique" to Schema.string(
                    description = "Blunt, constructive 2-sentence critique on how to improve pacing or delivery."
                ),
                "first30sStrategy" to Schema.string(
                    description = "Recommended content strategy and engagement hook for the first 30 seconds."
                ),
                "hooks" to Schema.obj(
                    properties = mapOf(
                        "curiosityHook" to Schema.string(description = "Curiosity/Mystery first 3-second hook"),
                        "contrarianHook" to Schema.string(description = "Contrarian/Shocking first 3-second hook"),
                        "directValueHook" to Schema.string(description = "Direct Value/Problem-Solution first 3-second hook")
                    )
                ),
                "pacingGuide" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "timestamp" to Schema.string(description = "Segment timestamp e.g. 0:00-0:04"),
                            "bRollSuggestion" to Schema.string(description = "Specific B-roll footage suggestion"),
                            "onScreenText" to Schema.string(description = "On-screen text overlay"),
                            "cutAndSfx" to Schema.string(description = "Zoom cuts or sound effects (SFX)")
                        )
                    ),
                    description = "Timestamped B-Roll and visual pacing breakdown."
                ),
                "captionsAndHashtags" to Schema.obj(
                    properties = mapOf(
                        "instagramCaption" to Schema.string(
                            description = "Engaging Instagram Reel caption with strong Call-To-Action to drive comments/shares."
                        ),
                        "hashtags" to Schema.array(
                            items = Schema.string(),
                            description = "5-8 niche-specific hashtags"
                        ),
                        "youtubeShortsTitle" to Schema.string(
                            description = "High-CTR YouTube Shorts title strictly under 60 characters."
                        ),
                        "youtubeShortsDescription" to Schema.string(
                            description = "Keyword-rich YouTube Shorts description."
                        )
                    )
                ),
                "thumbnailTextIdeas" to Schema.array(
                    items = Schema.string(),
                    description = "3 short, punchy thumbnail text overlays (max 3-4 words each)."
                )
            )
        )
    }

    private fun strategistSystemInstruction(): String = """
        You are an elite Viral Content Strategist, Video Retention Expert, and Social Media Growth Specialist for Indian and global creators (specializing in Instagram Reels, YouTube Shorts, and Facebook Reels).
        You evaluate spoken words, tone of voice, pacing, visual elements, retention curves, and core messaging.
        You can fluently write native-sounding hooks, scripts, and captions in English, Hinglish (natural urban Indian Hindi+English in Roman script), and Bengali (natural Kolkata/Dhaka conversational Bengali script).
        Be concrete, tactical, blunt about drop-off risks, and focused on watch time, replays, shares, and saves.
    """.trimIndent()

    private fun buildKitModel(useDeepThinking: Boolean): GenerativeModel {
        val modelName = if (useDeepThinking) {
            StrategistModel.CARBON_THINKING.modelId
        } else {
            StrategistModel.HYDROGEN.modelId
        }
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = viralGrowthKitSchema
            if (useDeepThinking) {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            }
        }
        return aiBackend.generativeModel(
            modelName = modelName,
            generationConfig = config,
            systemInstruction = content { text(strategistSystemInstruction()) }
        )
    }

    private val trendSearchModel by lazy {
        aiBackend.generativeModel(
            modelName = StrategistModel.HYDROGEN.modelId,
            tools = listOf(Tool.googleSearch()),
            systemInstruction = content {
                text(
                    strategistSystemInstruction() + "\nProvide live, verified creator trends, viral formats, trending audio cues, and platform algorithm shifts using Google Search."
                )
            }
        )
    }

    private val fastLiteModel by lazy {
        aiBackend.generativeModel(
            modelName = StrategistModel.FLASH_LITE.modelId,
            systemInstruction = content {
                text("You are a rapid short-form hook & title specialist. Be crisp, punchy, and ready to copy-paste.")
            }
        )
    }

    private fun buildChatModel(
        strategistModel: StrategistModel,
        enableSearchGrounding: Boolean
    ): GenerativeModel {
        val config = if (strategistModel.usesHighThinking) {
            generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                    includeThoughts = true
                }
            }
        } else {
            null
        }
        val tools = if (strategistModel == StrategistModel.HYDROGEN && enableSearchGrounding) {
            listOf(Tool.googleSearch())
        } else {
            null
        }
        return aiBackend.generativeModel(
            modelName = strategistModel.modelId,
            generationConfig = config,
            tools = tools,
            systemInstruction = content { text(strategistSystemInstruction()) }
        )
    }

    suspend fun analyzeForViralGrowthKit(
        userId: String,
        mediaBytes: ByteArray?,
        mediaMimeType: String?,
        mediaLabel: String,
        mediaKind: String,
        creatorNotes: String,
        language: TargetLanguage,
        platform: TargetPlatform,
        useDeepThinking: Boolean
    ): ViralGrowthKit {
        val model = buildKitModel(useDeepThinking)
        val modelId = if (useDeepThinking) {
            StrategistModel.CARBON_THINKING.modelId
        } else {
            StrategistModel.HYDROGEN.modelId
        }

        val analysisPrompt = """
            Analyze the provided ${if (mediaBytes != null) "$mediaKind file ($mediaLabel)" else "creator script/concept"} thoroughly—evaluating spoken words, tone of voice, pacing, visual elements, and core message.
            Generate a complete, high-converting "Viral Growth Kit" strictly in **${language.label}** (${language.nativeHint}) optimized for **${platform.label}**.

            Creator context / notes:
            ${creatorNotes.ifBlank { "No additional notes provided; infer topic, pacing, and audience directly from the uploaded media." }}

            Follow these strict rules:
            1. viralScore (0-100) & diagnosisCritique: Rate raw video/concept retention potential out of 100 and give a blunt, constructive 2-sentence critique on how to improve pacing or delivery.
            2. hooks: Provide 3 distinct, scroll-stopping first-3-seconds hooks (curiosityHook, contrarianHook, directValueHook).
            3. pacingGuide: Break down into key timestamps (e.g., 0:00-0:04, 0:05-0:12, etc.) with specific B-roll footage, on-screen text overlays, zoom cuts, and sound effects (SFX) to prevent viewer drop-off.
            4. captionsAndHashtags:
               - instagramCaption: Engaging storytelling or punchy summary with a strong Call-To-Action (CTA) to drive comments/shares.
               - hashtags: 5-8 niche-specific hashtags.
               - youtubeShortsTitle: High-CTR title strictly under 60 characters.
               - youtubeShortsDescription: Keyword-rich description.
            5. thumbnailTextIdeas: 3 short, punchy text overlays (maximum 3-4 words each) creating a curiosity gap.
            6. first30sStrategy: Recommend a concrete engagement hook and retention strategy for the first 30 seconds.
        """.trimIndent()

        val inputContent = content {
            if (mediaBytes != null && !mediaMimeType.isNullOrBlank()) {
                inlineData(mediaBytes, mediaMimeType)
            }
            text(analysisPrompt)
        }

        val timeoutMs = if (useDeepThinking) 60_000L else 55_000L
        val response = withTimeout(timeoutMs) {
            model.generateContent(inputContent)
        }

        val jsonText = response.text
            ?: throw IllegalStateException("Gemini returned an empty response.")

        val root = JSONObject(jsonText)
        val hooksObj = root.optJSONObject("hooks") ?: JSONObject()
        val captionsObj = root.optJSONObject("captionsAndHashtags") ?: JSONObject()

        val pacingArray = root.optJSONArray("pacingGuide")
        val pacingList = buildList {
            if (pacingArray != null) {
                for (i in 0 until pacingArray.length()) {
                    val seg = pacingArray.optJSONObject(i) ?: continue
                    add(
                        PacingSegment(
                            timestamp = seg.optString("timestamp", "0:00-0:03"),
                            bRollSuggestion = seg.optString("bRollSuggestion", ""),
                            onScreenText = seg.optString("onScreenText", ""),
                            cutAndSfx = seg.optString("cutAndSfx", "")
                        )
                    )
                }
            }
        }

        val hashtagsArray = captionsObj.optJSONArray("hashtags")
        val hashtagList = buildList {
            if (hashtagsArray != null) {
                for (i in 0 until hashtagsArray.length()) {
                    val tag = hashtagsArray.optString(i, "").trim()
                    if (tag.isNotEmpty()) add(tag)
                }
            }
        }

        val thumbArray = root.optJSONArray("thumbnailTextIdeas")
        val thumbList = buildList {
            if (thumbArray != null) {
                for (i in 0 until thumbArray.length()) {
                    val idea = thumbArray.optString(i, "").trim()
                    if (idea.isNotEmpty()) add(idea)
                }
            }
        }

        val ytTitle = captionsObj.optString("youtubeShortsTitle", "Viral Growth Kit")
            .take(120)

        return ViralGrowthKit(
            userId = userId,
            title = ytTitle,
            mediaLabel = mediaLabel.ifBlank { "Concept / Script Audit" },
            mediaKind = mediaKind,
            language = language.label,
            platform = platform.label,
            modelUsed = modelId,
            viralScore = root.optInt("viralScore", 72).coerceIn(0, 100).toLong(),
            diagnosisCritique = root.optString("diagnosisCritique", ""),
            first30sStrategy = root.optString("first30sStrategy", ""),
            curiosityHook = hooksObj.optString("curiosityHook", ""),
            contrarianHook = hooksObj.optString("contrarianHook", ""),
            directValueHook = hooksObj.optString("directValueHook", ""),
            pacingSegments = pacingList,
            instagramCaption = captionsObj.optString("instagramCaption", ""),
            hashtags = hashtagList,
            youtubeTitle = ytTitle,
            youtubeDescription = captionsObj.optString("youtubeShortsDescription", ""),
            thumbnailIdeas = thumbList,
            rawJson = root.toString(2)
        )
    }

    suspend fun fetchLiveCreatorTrends(
        nicheQuery: String,
        language: TargetLanguage,
        platform: TargetPlatform
    ): TrendBriefResult {
        val prompt = """
            Use Google Search to find current, real-time viral content trends, breakout audio/formats, and high-performing hook angles right now for Indian & global creators in this niche:
            Niche / Topic: $nicheQuery
            Platform: ${platform.label}
            Output Language: ${language.label}

            Structure your brief clearly with:
            - Top 3 Trending Formats / Audio Angles happening right now
            - 3 Ready-to-film Hook Ideas in ${language.label}
            - Recommended Niche Hashtags & Posting Timing insight
        """.trimIndent()

        val response = withTimeout(45_000L) {
            trendSearchModel.generateContent(prompt)
        }

        val text = response.text ?: throw IllegalStateException("No trend insights returned.")
        val groundingMeta = response.candidates.firstOrNull()?.groundingMetadata
        val sources = groundingMeta?.groundingChunks?.mapNotNull { chunk ->
            val web = chunk.web
            val uri = web?.uri
            if (!uri.isNullOrBlank()) {
                GroundingSource(
                    title = web.title ?: uri,
                    uri = uri
                )
            } else {
                null
            }
        }?.distinctBy { it.uri } ?: emptyList()

        val html = groundingMeta?.searchEntryPoint?.renderedContent

        return TrendBriefResult(
            query = nicheQuery,
            summaryText = text,
            sources = sources,
            searchEntryPointHtml = html
        )
    }

    suspend fun fetchIndustryTrendAlert(
        industryNiche: String,
        focus: TrendAlertFocus,
        language: TargetLanguage,
        platform: TargetPlatform
    ): LatestTrendAlert {
        val prompt = """
            Use Google Search to discover what is breaking RIGHT NOW for creators in this industry/niche:
            Industry / Niche: $industryNiche
            Platform: ${platform.label}
            Language: ${language.label}
            Alert Focus: ${focus.promptDirective}

            Return your response using EXACTLY these three labeled sections so it can power an actionable creator notification:
            HEADLINE: <One punchy notification title under 55 characters naming a specific viral audio or content gap>
            ALERT_SUMMARY: <One crisp 1-2 sentence push notification alert explaining the trend or content gap and why creators should act today>
            OPPORTUNITY_BRIEF:
            <Bullet points covering:
            - 2 specific Viral Audio / Sound Trends or Rising Content Gaps found via live search
            - 2 ready-to-shoot Hook ideas in ${language.label}
            - Why this angle has low saturation / high retention upside today>
        """.trimIndent()

        val response = withTimeout(45_000L) {
            trendSearchModel.generateContent(prompt)
        }

        val rawText = response.text?.trim()
            ?: throw IllegalStateException("Google Search Grounding returned an empty trend alert.")

        val headline = Regex("(?im)^HEADLINE:\\s*(.+)$")
            .find(rawText)?.groupValues?.getOrNull(1)?.trim()
            ?.take(80)
            ?: "${industryNiche.take(28)}: New Viral Alert"

        val alertSummary = Regex("(?is)ALERT_SUMMARY:\\s*(.+?)(?=\\n\\s*OPPORTUNITY_BRIEF:|$)")
            .find(rawText)?.groupValues?.getOrNull(1)?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.take(220)
            ?: rawText.lines().firstOrNull { it.isNotBlank() }?.take(180).orEmpty()

        val brief = Regex("(?is)OPPORTUNITY_BRIEF:\\s*(.+)$")
            .find(rawText)?.groupValues?.getOrNull(1)?.trim()
            ?: rawText

        val groundingMeta = response.candidates.firstOrNull()?.groundingMetadata
        val sources = groundingMeta?.groundingChunks?.mapNotNull { chunk ->
            val web = chunk.web
            val uri = web?.uri
            if (!uri.isNullOrBlank()) {
                GroundingSource(
                    title = web.title ?: uri,
                    uri = uri
                )
            } else {
                null
            }
        }?.distinctBy { it.uri }?.take(8) ?: emptyList()

        val html = groundingMeta?.searchEntryPoint?.renderedContent

        return LatestTrendAlert(
            headline = headline,
            alertSummary = alertSummary,
            fullBrief = brief,
            industryNiche = industryNiche,
            focus = focus,
            sources = sources,
            searchEntryPointHtml = html,
            generatedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun extractTrendingTopicTags(
        ideaText: String,
        industryNiche: String,
        language: TargetLanguage,
        platform: TargetPlatform
    ): TrendingTagExtractionResult {
        val prompt = """
            Analyze the following recorded creator content idea and perform automated keyword extraction + live Google Search Grounding to match it with trending industry topics happening RIGHT NOW.

            Recorded Content Idea: "$ideaText"
            Creator Industry / Niche: $industryNiche
            Platform: ${platform.label}
            Language: ${language.label}

            Use Google Search to verify what industry topics, audio formats, or creator conversations related to this idea are trending right now.
            Reply using EXACTLY these four labeled lines/sections:
            EXTRACTED_KEYWORDS: <3 to 5 concise comma-separated keywords extracted from the recorded idea>
            TRENDING_TOPICS: <4 to 6 concise pipe-separated trending industry topic tags (2 to 4 words each, no # symbols) discovered via Google Search that this idea should tap into>
            RATIONALE: <1 to 2 crisp sentences explaining why these live trending topics boost reach for this recorded idea>
        """.trimIndent()

        val response = withTimeout(40_000L) {
            trendSearchModel.generateContent(prompt)
        }

        val rawText = response.text?.trim()
            ?: throw IllegalStateException("Google Search Grounding returned empty keyword tags.")

        val keywords = Regex("(?im)^EXTRACTED_KEYWORDS:\\s*(.+)$")
            .find(rawText)?.groupValues?.getOrNull(1)
            ?.split(",")
            ?.map { it.trim().removePrefix("#").take(40) }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.take(6)
            .orEmpty()

        val topicsRaw = Regex("(?im)^TRENDING_TOPICS:\\s*(.+)$")
            .find(rawText)?.groupValues?.getOrNull(1)
            .orEmpty()

        val trendingTopics = topicsRaw
            .split("|", ",")
            .map { it.trim().removePrefix("#").trim('"', '\'', '.', ' ').take(48) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)

        val rationale = Regex("(?is)RATIONALE:\\s*(.+)$")
            .find(rawText)?.groupValues?.getOrNull(1)?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.take(500)
            ?: "Matched with live ${industryNiche} search trends for ${platform.label}."

        val groundingMeta = response.candidates.firstOrNull()?.groundingMetadata
        val sources = groundingMeta?.groundingChunks?.mapNotNull { chunk ->
            val web = chunk.web
            val uri = web?.uri
            if (!uri.isNullOrBlank()) {
                GroundingSource(
                    title = web.title ?: uri,
                    uri = uri
                )
            } else {
                null
            }
        }?.distinctBy { it.uri }?.take(8) ?: emptyList()

        val html = groundingMeta?.searchEntryPoint?.renderedContent

        return TrendingTagExtractionResult(
            extractedKeywords = keywords,
            trendingTopics = trendingTopics.ifEmpty { keywords.take(4) },
            rationale = rationale,
            sources = sources,
            searchEntryPointHtml = html
        )
    }

    suspend fun quickRewriteHooks(
        draftHookOrTopic: String,
        language: TargetLanguage,
        platform: TargetPlatform
    ): String {
        val prompt = """
            Rewrite and punch up this creator hook/title in ${language.label} for ${platform.label}.
            Give:
            1) 3 ultra-punchy 3-second opening hooks (Curiosity, Contrarian, Direct Value)
            2) 2 YouTube Shorts titles under 55 characters
            3) 1 comment-bait CTA line

            Input: $draftHookOrTopic
        """.trimIndent()

        val response = withTimeout(30_000L) {
            fastLiteModel.generateContent(prompt)
        }
        return response.text ?: throw IllegalStateException("Flash-Lite returned empty text.")
    }

    fun sendChatMessageStream(
        historyRecords: List<ChatMessageRecord>,
        userPrompt: String,
        modelChoice: StrategistModel,
        enableSearchGrounding: Boolean
    ): Flow<ChatStreamEvent> = flow {
        val model = buildChatModel(modelChoice, enableSearchGrounding)
        val sdkHistory = historyRecords.takeLast(20).map { record ->
            content(role = if (record.role == "model") "model" else "user") {
                text(record.text)
            }
        }
        val chat = model.startChat(history = sdkHistory)

        val textBuilder = StringBuilder()
        val thoughtBuilder = StringBuilder()
        val sources = mutableListOf<GroundingSource>()
        var searchHtml: String? = null

        val timeoutMs = if (modelChoice.usesHighThinking) 60_000L else 45_000L
        withTimeout(timeoutMs) {
            chat.sendMessageStream(userPrompt).collect { chunk ->
                val textDelta = chunk.text.orEmpty()
                val thoughtDelta = chunk.thoughtSummary.orEmpty()
                if (textDelta.isNotEmpty()) textBuilder.append(textDelta)
                if (thoughtDelta.isNotEmpty()) thoughtBuilder.append(thoughtDelta)

                val meta = chunk.candidates.firstOrNull()?.groundingMetadata
                if (meta != null) {
                    meta.searchEntryPoint?.renderedContent?.let { searchHtml = it }
                    meta.groundingChunks.forEach { gChunk ->
                        val web = gChunk.web
                        val uri = web?.uri
                        if (!uri.isNullOrBlank() && sources.none { it.uri == uri }) {
                            sources.add(GroundingSource(title = web.title ?: uri, uri = uri))
                        }
                    }
                }

                if (textDelta.isNotEmpty() || thoughtDelta.isNotEmpty()) {
                    emit(ChatStreamEvent.Chunk(textDelta = textDelta, thoughtDelta = thoughtDelta))
                }
            }
        }

        val finalText = textBuilder.toString().ifBlank {
            throw IllegalStateException("Model returned an empty response.")
        }
        val finalThoughts = thoughtBuilder.toString().takeIf { it.isNotBlank() }

        emit(
            ChatStreamEvent.Done(
                fullText = finalText,
                fullThoughts = finalThoughts,
                groundingSources = sources.distinctBy { it.uri }.take(10),
                searchEntryPointHtml = searchHtml
            )
        )
    }
}
