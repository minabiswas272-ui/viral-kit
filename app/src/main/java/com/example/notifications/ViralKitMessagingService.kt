package com.example.notifications

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.data.repository.ViralKitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "ViralKitFcmService"

class ViralKitMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Received refreshed FCM registration token.")
        val prefsStore = TrendAlertPrefsStore(applicationContext)
        prefsStore.saveFcmToken(token)

        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            val prefs = prefsStore.readPreferences()
            val topic = if (prefs.fcmNichePushEnabled) {
                FcmPushManager.sanitizeNicheTopic(prefs.industryNiche)
            } else {
                prefsStore.readSubscribedTopic()
            }
            serviceScope.launch {
                try {
                    val repo = ViralKitRepository(applicationContext)
                    repo.updateFcmRegistration(fcmToken = token, fcmNicheTopic = topic)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync refreshed FCM token to Firestore", e)
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val prefsStore = TrendAlertPrefsStore(applicationContext)
        val prefs = prefsStore.readPreferences()

        val data = message.data
        val messageType = data["type"]?.trim()?.lowercase().orEmpty()
        val notificationTitle = message.notification?.title
        val notificationBody = message.notification?.body

        when (messageType) {
            TYPE_STRATEGY_COMPLETE -> {
                if (!prefs.strategyCompletePushEnabled) return
                val kitId = data["kitId"] ?: data["kit_id"]
                val title = data["title"] ?: notificationTitle ?: "AI Strategy Analysis Complete"
                val body = data["body"] ?: notificationBody
                    ?: "Your Viral Growth Kit has finished processing and is ready in Vault."
                val viralScore = (data["viralScore"] ?: data["viral_score"])?.toIntOrNull()
                val platform = data["platform"]

                TrendNotificationHelper.postStrategyCompleteNotification(
                    context = applicationContext,
                    kitId = kitId,
                    title = title,
                    viralScore = viralScore,
                    platform = platform,
                    summaryBody = body
                )
            }

            TYPE_TRENDING_TOPIC -> {
                if (!prefs.fcmNichePushEnabled) return
                val niche = data["niche"] ?: prefs.industryNiche
                val topic = data["topic"] ?: data["trendingTopic"]
                val title = data["title"] ?: notificationTitle ?: "New Trend Match: $niche"
                val body = data["body"] ?: notificationBody
                    ?: "Trending topic \"${topic ?: niche}\" is surging right now. Tap to inspect."

                TrendNotificationHelper.postNicheTrendingTopicNotification(
                    context = applicationContext,
                    title = title,
                    body = body,
                    niche = niche,
                    topic = topic
                )
            }

            else -> {
                if (!prefs.fcmNichePushEnabled) return
                val title = data["title"] ?: notificationTitle ?: "ViralKit Creator Update"
                val body = data["body"] ?: notificationBody
                    ?: "New creator trend or strategy insight is ready."
                val topic = data["topic"]
                val niche = data["niche"] ?: prefs.industryNiche

                TrendNotificationHelper.postNicheTrendingTopicNotification(
                    context = applicationContext,
                    title = title,
                    body = body,
                    niche = niche,
                    topic = topic
                )
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val TYPE_STRATEGY_COMPLETE = "strategy_complete"
        const val TYPE_TRENDING_TOPIC = "trending_topic"
    }
}
