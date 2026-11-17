package com.example.data.supabase

import com.example.data.model.GroundingSource
import com.example.data.model.PacingSegment
import com.example.data.model.TargetLanguage
import com.example.data.model.TargetPlatform
import com.example.data.model.ViralGrowthKit
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class SupabaseRestClient(
    private val config: SupabaseConfig = SupabaseConfig.fromBuildConfig(),
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    val isConfigured: Boolean
        get() = config.isConfigured

    suspend fun upsertCreatorProfile(
        userId: String,
        displayName: String,
        email: String?,
        language: TargetLanguage,
        platform: TargetPlatform,
        fcmTopic: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        }
        val url = "${config.restBaseUrl}/creator_profiles".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("on_conflict", "user_id")
            ?.build()
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Supabase URL"))

        val payload = JSONObject().apply {
            put("user_id", userId)
            put("display_name", displayName.trim().ifBlank { "Creator" }.take(100))
            if (!email.isNullOrBlank()) put("email", email.trim().take(160))
            put("preferred_language", language.label)
            put("preferred_platform", platform.label)
            if (!fcmTopic.isNullOrBlank()) put("fcm_topic", fcmTopic.trim().take(120))
            put("updated_at", formatIso8601(System.currentTimeMillis()))
        }

        executePost(url.toString(), payload.toString())
    }

    suspend fun upsertKit(kit: ViralGrowthKit): Result<Unit> =
        upsertKits(listOf(kit))

    suspend fun upsertKits(kits: List<ViralGrowthKit>): Result<Unit> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        }
        if (kits.isEmpty()) return@withContext Result.success(Unit)

        val url = "${config.restBaseUrl}/viral_growth_kits".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("on_conflict", "id")
            ?.build()
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Supabase URL"))

        val array = JSONArray()
        kits.forEach { kit ->
            if (kit.id.isNotBlank() && kit.userId.isNotBlank()) {
                array.put(kitToJson(kit))
            }
        }
        if (array.length() == 0) return@withContext Result.success(Unit)

        executePost(url.toString(), array.toString())
    }

    suspend fun fetchKitsForUser(userId: String): Result<List<ViralGrowthKit>> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        }
        val url = "${config.restBaseUrl}/viral_growth_kits".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("user_id", "eq.$userId")
            ?.addQueryParameter("order", "created_at.desc")
            ?.addQueryParameter("select", "*")
            ?.build()
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Supabase URL"))

        runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", config.anonKey)
                .addHeader("Authorization", "Bearer ${config.anonKey}")
                .addHeader("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Supabase HTTP ${response.code}: ${body.take(200)}")
                }
                parseKitsArray(body)
            }
        }
    }

    suspend fun deleteKit(kitId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        }
        val url = "${config.restBaseUrl}/viral_growth_kits".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("id", "eq.$kitId")
            ?.addQueryParameter("user_id", "eq.$userId")
            ?.build()
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Supabase URL"))

        runCatching {
            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("apikey", config.anonKey)
                .addHeader("Authorization", "Bearer ${config.anonKey}")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw IllegalStateException("Supabase delete HTTP ${response.code}: ${body.take(200)}")
                }
            }
        }
    }

    private fun executePost(url: String, jsonBody: String): Result<Unit> {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", config.anonKey)
                .addHeader("Authorization", "Bearer ${config.anonKey}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw IllegalStateException("Supabase HTTP ${response.code}: ${body.take(240)}")
                }
            }
        }
    }

    internal fun kitToJson(kit: ViralGrowthKit): JSONObject {
        val createdMillis = kit.createdAt?.toDate()?.time ?: System.currentTimeMillis()
        val updatedMillis = kit.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
        return JSONObject().apply {
            put("id", kit.id)
            put("user_id", kit.userId)
            put("title", kit.title.trim().ifBlank { "Viral Growth Kit" }.take(160))
            put("media_label", kit.mediaLabel.trim().take(120))
            put("media_kind", kit.mediaKind)
            put("language", kit.language)
            put("platform", kit.platform)
            put("model_used", kit.modelUsed)
            put("viral_score", kit.viralScore.coerceIn(0L, 100L))
            put("diagnosis_critique", kit.diagnosisCritique)
            put("first_30s_strategy", kit.first30sStrategy)
            put("curiosity_hook", kit.curiosityHook)
            put("contrarian_hook", kit.contrarianHook)
            put("direct_value_hook", kit.directValueHook)
            put(
                "pacing_segments",
                JSONArray().apply {
                    kit.pacingSegments.forEach { seg ->
                        put(
                            JSONObject().apply {
                                put("timestamp", seg.timestamp)
                                put("bRollSuggestion", seg.bRollSuggestion)
                                put("onScreenText", seg.onScreenText)
                                put("cutAndSfx", seg.cutAndSfx)
                            }
                        )
                    }
                }
            )
            put("instagram_caption", kit.instagramCaption)
            put("hashtags", JSONArray(kit.hashtags))
            put("youtube_title", kit.youtubeTitle)
            put("youtube_description", kit.youtubeDescription)
            put("thumbnail_ideas", JSONArray(kit.thumbnailIdeas))
            put("trending_topics", JSONArray(kit.trendingTopics))
            put(
                "trending_tag_sources",
                JSONArray().apply {
                    kit.trendingTagSources.forEach { src ->
                        put(
                            JSONObject().apply {
                                put("title", src.title)
                                put("uri", src.uri)
                            }
                        )
                    }
                }
            )
            put("raw_json", kit.rawJson.ifBlank { "{}" })
            put("created_at", formatIso8601(createdMillis))
            put("updated_at", formatIso8601(updatedMillis))
        }
    }

    internal fun parseKitsArray(jsonBody: String): List<ViralGrowthKit> {
        if (jsonBody.isBlank()) return emptyList()
        val array = JSONArray(jsonBody)
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(jsonToKit(obj))
            }
        }
    }

    private fun jsonToKit(obj: JSONObject): ViralGrowthKit {
        val segments = mutableListOf<PacingSegment>()
        val segArr = obj.optJSONArray("pacing_segments")
        if (segArr != null) {
            for (i in 0 until segArr.length()) {
                val s = segArr.optJSONObject(i) ?: continue
                segments.add(
                    PacingSegment(
                        timestamp = s.optString("timestamp", ""),
                        bRollSuggestion = s.optString("bRollSuggestion", ""),
                        onScreenText = s.optString("onScreenText", ""),
                        cutAndSfx = s.optString("cutAndSfx", "")
                    )
                )
            }
        }

        val sources = mutableListOf<GroundingSource>()
        val srcArr = obj.optJSONArray("trending_tag_sources")
        if (srcArr != null) {
            for (i in 0 until srcArr.length()) {
                val src = srcArr.optJSONObject(i) ?: continue
                sources.add(
                    GroundingSource(
                        title = src.optString("title", ""),
                        uri = src.optString("uri", "")
                    )
                )
            }
        }

        return ViralGrowthKit(
            id = obj.optString("id", ""),
            userId = obj.optString("user_id", ""),
            title = obj.optString("title", "Viral Growth Kit"),
            mediaLabel = obj.optString("media_label", ""),
            mediaKind = obj.optString("media_kind", "text"),
            language = obj.optString("language", TargetLanguage.ENGLISH.label),
            platform = obj.optString("platform", TargetPlatform.INSTAGRAM_REELS.label),
            modelUsed = obj.optString("model_used", "gemini-4-hydrogen"),
            viralScore = obj.optLong("viral_score", 0L),
            diagnosisCritique = obj.optString("diagnosis_critique", ""),
            first30sStrategy = obj.optString("first_30s_strategy", ""),
            curiosityHook = obj.optString("curiosity_hook", ""),
            contrarianHook = obj.optString("contrarian_hook", ""),
            directValueHook = obj.optString("direct_value_hook", ""),
            pacingSegments = segments,
            instagramCaption = obj.optString("instagram_caption", ""),
            hashtags = readStringArray(obj.optJSONArray("hashtags")),
            youtubeTitle = obj.optString("youtube_title", ""),
            youtubeDescription = obj.optString("youtube_description", ""),
            thumbnailIdeas = readStringArray(obj.optJSONArray("thumbnail_ideas")),
            rawJson = obj.optString("raw_json", "{}"),
            trendingTopics = readStringArray(obj.optJSONArray("trending_topics")),
            trendingTagSources = sources,
            createdAt = parseIso8601(obj.optString("created_at", "")),
            updatedAt = parseIso8601(obj.optString("updated_at", ""))
        )
    }

    private fun readStringArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val item = arr.optString(i, "")
                if (item.isNotEmpty()) add(item)
            }
        }
    }

    private fun formatIso8601(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    private fun parseIso8601(raw: String): Timestamp? {
        if (raw.isBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(raw)
            }.getOrNull()
            if (parsed != null) return Timestamp(parsed)
        }
        return null
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
