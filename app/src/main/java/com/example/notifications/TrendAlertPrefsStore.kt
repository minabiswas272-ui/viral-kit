package com.example.notifications

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.GroundingSource
import com.example.data.model.LatestTrendAlert
import com.example.data.model.TargetLanguage
import com.example.data.model.TargetPlatform
import com.example.data.model.TrendAlertFocus
import com.example.data.model.TrendAlertInterval
import com.example.data.model.TrendAlertPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "viralkit_trend_alerts"
private const val KEY_ENABLED = "alerts_enabled"
private const val KEY_INDUSTRY = "alerts_industry"
private const val KEY_FOCUS = "alerts_focus"
private const val KEY_INTERVAL_HOURS = "alerts_interval_hours"
private const val KEY_LANGUAGE = "alerts_language"
private const val KEY_PLATFORM = "alerts_platform"
private const val KEY_FCM_NICHE_PUSH_ENABLED = "fcm_niche_push_enabled"
private const val KEY_STRATEGY_COMPLETE_PUSH_ENABLED = "strategy_complete_push_enabled"
private const val KEY_FCM_TOKEN = "fcm_token"
private const val KEY_FCM_SUBSCRIBED_TOPIC = "fcm_subscribed_topic"

private const val KEY_LATEST_HEADLINE = "latest_headline"
private const val KEY_LATEST_SUMMARY = "latest_summary"
private const val KEY_LATEST_BRIEF = "latest_brief"
private const val KEY_LATEST_INDUSTRY = "latest_industry"
private const val KEY_LATEST_FOCUS = "latest_focus"
private const val KEY_LATEST_SOURCES_JSON = "latest_sources_json"
private const val KEY_LATEST_SEARCH_HTML = "latest_search_html"
private const val KEY_LATEST_TIMESTAMP = "latest_timestamp"

class TrendAlertPrefsStore(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _preferencesFlow = MutableStateFlow(readPreferences())
    val preferencesFlow: StateFlow<TrendAlertPreferences> = _preferencesFlow.asStateFlow()

    private val _latestAlertFlow = MutableStateFlow(readLatestAlert())
    val latestAlertFlow: StateFlow<LatestTrendAlert?> = _latestAlertFlow.asStateFlow()

    private val _fcmTokenFlow = MutableStateFlow(readFcmToken())
    val fcmTokenFlow: StateFlow<String?> = _fcmTokenFlow.asStateFlow()

    private val _fcmSubscribedTopicFlow = MutableStateFlow(readSubscribedTopic())
    val fcmSubscribedTopicFlow: StateFlow<String?> = _fcmSubscribedTopicFlow.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _preferencesFlow.value = readPreferences()
        _latestAlertFlow.value = readLatestAlert()
        _fcmTokenFlow.value = readFcmToken()
        _fcmSubscribedTopicFlow.value = readSubscribedTopic()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun readPreferences(): TrendAlertPreferences {
        return TrendAlertPreferences(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            industryNiche = prefs.getString(KEY_INDUSTRY, null)
                ?.takeIf { it.isNotBlank() } ?: "Indian Tech & AI Creators",
            focus = TrendAlertFocus.fromName(prefs.getString(KEY_FOCUS, null)),
            interval = TrendAlertInterval.fromHours(prefs.getLong(KEY_INTERVAL_HOURS, 12L)),
            language = TargetLanguage.fromLabel(prefs.getString(KEY_LANGUAGE, null)),
            platform = TargetPlatform.fromLabel(prefs.getString(KEY_PLATFORM, null)),
            fcmNichePushEnabled = prefs.getBoolean(KEY_FCM_NICHE_PUSH_ENABLED, true),
            strategyCompletePushEnabled = prefs.getBoolean(KEY_STRATEGY_COMPLETE_PUSH_ENABLED, true)
        )
    }

    fun savePreferences(updated: TrendAlertPreferences) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, updated.enabled)
            .putString(KEY_INDUSTRY, updated.industryNiche.trim().ifEmpty { "Indian Tech & AI Creators" })
            .putString(KEY_FOCUS, updated.focus.name)
            .putLong(KEY_INTERVAL_HOURS, updated.interval.hours)
            .putString(KEY_LANGUAGE, updated.language.label)
            .putString(KEY_PLATFORM, updated.platform.label)
            .putBoolean(KEY_FCM_NICHE_PUSH_ENABLED, updated.fcmNichePushEnabled)
            .putBoolean(KEY_STRATEGY_COMPLETE_PUSH_ENABLED, updated.strategyCompletePushEnabled)
            .apply()
        _preferencesFlow.value = readPreferences()
    }

    fun readFcmToken(): String? =
        prefs.getString(KEY_FCM_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun saveFcmToken(token: String?) {
        val cleaned = token?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit().putString(KEY_FCM_TOKEN, cleaned).apply()
        _fcmTokenFlow.value = cleaned
    }

    fun readSubscribedTopic(): String? =
        prefs.getString(KEY_FCM_SUBSCRIBED_TOPIC, null)?.takeIf { it.isNotBlank() }

    fun saveSubscribedTopic(topic: String?) {
        val cleaned = topic?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit().putString(KEY_FCM_SUBSCRIBED_TOPIC, cleaned).apply()
        _fcmSubscribedTopicFlow.value = cleaned
    }

    fun readLatestAlert(): LatestTrendAlert? {
        val headline = prefs.getString(KEY_LATEST_HEADLINE, null)?.takeIf { it.isNotBlank() } ?: return null
        val summary = prefs.getString(KEY_LATEST_SUMMARY, "").orEmpty()
        val brief = prefs.getString(KEY_LATEST_BRIEF, "").orEmpty()
        val industry = prefs.getString(KEY_LATEST_INDUSTRY, "").orEmpty()
        val focus = TrendAlertFocus.fromName(prefs.getString(KEY_LATEST_FOCUS, null))
        val sourcesJson = prefs.getString(KEY_LATEST_SOURCES_JSON, "[]").orEmpty()
        val html = prefs.getString(KEY_LATEST_SEARCH_HTML, null)
        val ts = prefs.getLong(KEY_LATEST_TIMESTAMP, 0L)

        val sources = buildList {
            try {
                val arr = JSONArray(sourcesJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val uri = obj.optString("uri", "")
                    if (uri.isNotBlank()) {
                        add(
                            GroundingSource(
                                title = obj.optString("title", uri),
                                uri = uri
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        return LatestTrendAlert(
            headline = headline,
            alertSummary = summary,
            fullBrief = brief,
            industryNiche = industry,
            focus = focus,
            sources = sources,
            searchEntryPointHtml = html,
            generatedAtMillis = ts
        )
    }

    fun saveLatestAlert(alert: LatestTrendAlert) {
        val sourcesArray = JSONArray().apply {
            alert.sources.forEach { src ->
                put(
                    JSONObject().apply {
                        put("title", src.title)
                        put("uri", src.uri)
                    }
                )
            }
        }
        prefs.edit()
            .putString(KEY_LATEST_HEADLINE, alert.headline)
            .putString(KEY_LATEST_SUMMARY, alert.alertSummary)
            .putString(KEY_LATEST_BRIEF, alert.fullBrief)
            .putString(KEY_LATEST_INDUSTRY, alert.industryNiche)
            .putString(KEY_LATEST_FOCUS, alert.focus.name)
            .putString(KEY_LATEST_SOURCES_JSON, sourcesArray.toString())
            .putString(KEY_LATEST_SEARCH_HTML, alert.searchEntryPointHtml)
            .putLong(KEY_LATEST_TIMESTAMP, alert.generatedAtMillis)
            .apply()
        _latestAlertFlow.value = readLatestAlert()
    }
}
