package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.GroundingSource
import com.example.data.model.PacingSegment
import com.example.data.model.ViralGrowthKit
import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

object KitSyncStatus {
    const val SYNCED = "SYNCED"
    const val PENDING_CREATE = "PENDING_CREATE"
    const val PENDING_UPDATE = "PENDING_UPDATE"
    const val PENDING_DELETE = "PENDING_DELETE"
}

@Entity(
    tableName = "viral_growth_kits",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["userId", "createdAtMillis"])
    ]
)
data class ViralKitEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val mediaLabel: String,
    val mediaKind: String,
    val language: String,
    val platform: String,
    val modelUsed: String,
    val viralScore: Long,
    val diagnosisCritique: String,
    val first30sStrategy: String,
    val curiosityHook: String,
    val contrarianHook: String,
    val directValueHook: String,
    val pacingSegmentsJson: String,
    val instagramCaption: String,
    val hashtagsJson: String,
    val youtubeTitle: String,
    val youtubeDescription: String,
    val thumbnailIdeasJson: String,
    val rawJson: String,
    val trendingTopicsJson: String,
    val trendingTagSourcesJson: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val syncStatus: String = KitSyncStatus.SYNCED,
    val lastSyncError: String? = null
) {
    fun toDomain(): ViralGrowthKit = ViralGrowthKit(
        id = id,
        userId = userId,
        title = title,
        mediaLabel = mediaLabel,
        mediaKind = mediaKind,
        language = language,
        platform = platform,
        modelUsed = modelUsed,
        viralScore = viralScore,
        diagnosisCritique = diagnosisCritique,
        first30sStrategy = first30sStrategy,
        curiosityHook = curiosityHook,
        contrarianHook = contrarianHook,
        directValueHook = directValueHook,
        pacingSegments = decodePacingSegments(pacingSegmentsJson),
        instagramCaption = instagramCaption,
        hashtags = decodeStringList(hashtagsJson),
        youtubeTitle = youtubeTitle,
        youtubeDescription = youtubeDescription,
        thumbnailIdeas = decodeStringList(thumbnailIdeasJson),
        rawJson = rawJson,
        trendingTopics = decodeStringList(trendingTopicsJson),
        trendingTagSources = decodeSources(trendingTagSourcesJson),
        createdAt = Timestamp(Date(createdAtMillis)),
        updatedAt = Timestamp(Date(updatedAtMillis))
    )

    companion object {
        fun fromDomain(
            kit: ViralGrowthKit,
            idOverride: String = kit.id,
            userIdOverride: String = kit.userId,
            syncStatus: String = KitSyncStatus.SYNCED,
            nowMillis: Long = System.currentTimeMillis(),
            lastSyncError: String? = null
        ): ViralKitEntity {
            val createdMs = kit.createdAt?.toDate()?.time ?: nowMillis
            val updatedMs = kit.updatedAt?.toDate()?.time ?: nowMillis
            return ViralKitEntity(
                id = idOverride,
                userId = userIdOverride,
                title = kit.title,
                mediaLabel = kit.mediaLabel,
                mediaKind = kit.mediaKind,
                language = kit.language,
                platform = kit.platform,
                modelUsed = kit.modelUsed,
                viralScore = kit.viralScore,
                diagnosisCritique = kit.diagnosisCritique,
                first30sStrategy = kit.first30sStrategy,
                curiosityHook = kit.curiosityHook,
                contrarianHook = kit.contrarianHook,
                directValueHook = kit.directValueHook,
                pacingSegmentsJson = encodePacingSegments(kit.pacingSegments),
                instagramCaption = kit.instagramCaption,
                hashtagsJson = encodeStringList(kit.hashtags),
                youtubeTitle = kit.youtubeTitle,
                youtubeDescription = kit.youtubeDescription,
                thumbnailIdeasJson = encodeStringList(kit.thumbnailIdeas),
                rawJson = kit.rawJson,
                trendingTopicsJson = encodeStringList(kit.trendingTopics),
                trendingTagSourcesJson = encodeSources(kit.trendingTagSources),
                createdAtMillis = createdMs,
                updatedAtMillis = updatedMs,
                syncStatus = syncStatus,
                lastSyncError = lastSyncError
            )
        }

        fun encodeStringList(items: List<String>): String {
            val array = JSONArray()
            items.forEach { array.put(it) }
            return array.toString()
        }

        fun decodeStringList(json: String): List<String> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(json)
                buildList(array.length()) {
                    for (i in 0 until array.length()) {
                        val value = array.optString(i, "")
                        if (value.isNotEmpty()) add(value)
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun encodePacingSegments(segments: List<PacingSegment>): String {
            val array = JSONArray()
            segments.forEach { seg ->
                array.put(
                    JSONObject().apply {
                        put("timestamp", seg.timestamp)
                        put("bRollSuggestion", seg.bRollSuggestion)
                        put("onScreenText", seg.onScreenText)
                        put("cutAndSfx", seg.cutAndSfx)
                    }
                )
            }
            return array.toString()
        }

        fun decodePacingSegments(json: String): List<PacingSegment> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(json)
                buildList(array.length()) {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        add(
                            PacingSegment(
                                timestamp = obj.optString("timestamp", ""),
                                bRollSuggestion = obj.optString("bRollSuggestion", ""),
                                onScreenText = obj.optString("onScreenText", ""),
                                cutAndSfx = obj.optString("cutAndSfx", "")
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun encodeSources(sources: List<GroundingSource>): String {
            val array = JSONArray()
            sources.forEach { src ->
                array.put(
                    JSONObject().apply {
                        put("title", src.title)
                        put("uri", src.uri)
                    }
                )
            }
            return array.toString()
        }

        fun decodeSources(json: String): List<GroundingSource> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(json)
                buildList(array.length()) {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        add(
                            GroundingSource(
                                title = obj.optString("title", ""),
                                uri = obj.optString("uri", "")
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
