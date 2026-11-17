package com.example.notifications

import android.util.Log
import com.example.data.repository.ViralKitRepository
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.Locale

private const val TAG = "FcmPushManager"

data class FcmSyncResult(
    val token: String?,
    val subscribedTopic: String?,
    val syncedToFirestore: Boolean,
    val errorMessage: String? = null
)

object FcmPushManager {

    private val invalidTopicCharsRegex = Regex("[^a-z0-9_]+")
    private val multiUnderscoreRegex = Regex("_+")

    /**
     * Converts a creator niche label (e.g. "Indian Tech & AI Creators") into a valid
     * Firebase Cloud Messaging topic name matching [a-zA-Z0-9-_.~%]{1,900}.
     */
    fun sanitizeNicheTopic(niche: String): String {
        val normalized = niche
            .trim()
            .lowercase(Locale.ROOT)
            .replace(invalidTopicCharsRegex, "_")
            .replace(multiUnderscoreRegex, "_")
            .trim('_')
            .take(80)
        return if (normalized.isEmpty()) "niche_creator_general" else "niche_$normalized"
    }

    suspend fun syncFcmRegistration(
        repository: ViralKitRepository,
        prefsStore: TrendAlertPrefsStore,
        niche: String,
        subscribeToTopic: Boolean
    ): FcmSyncResult {
        val targetTopic = sanitizeNicheTopic(niche)
        val previousTopic = prefsStore.readSubscribedTopic()

        var token: String? = prefsStore.readFcmToken()
        var activeTopic: String? = if (subscribeToTopic) targetTopic else null
        var errorMsg: String? = null

        try {
            val messaging = Firebase.messaging
            token = messaging.token.await()
            if (!token.isNullOrBlank()) {
                prefsStore.saveFcmToken(token)
            }

            if (subscribeToTopic) {
                if (!previousTopic.isNullOrBlank() && previousTopic != targetTopic) {
                    runCatching { messaging.unsubscribeFromTopic(previousTopic).await() }
                }
                messaging.subscribeToTopic(targetTopic).await()
                prefsStore.saveSubscribedTopic(targetTopic)
                activeTopic = targetTopic
            } else {
                if (!previousTopic.isNullOrBlank()) {
                    runCatching { messaging.unsubscribeFromTopic(previousTopic).await() }
                }
                prefsStore.saveSubscribedTopic(null)
                activeTopic = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "FCM token/topic sync failed cleanly (Play Services may be unavailable)", e)
            errorMsg = e.localizedMessage ?: "FCM registration unavailable on this device."
        }

        val firestoreSynced = if (!token.isNullOrBlank() || !activeTopic.isNullOrBlank()) {
            repository.updateFcmRegistration(
                fcmToken = token,
                fcmNicheTopic = activeTopic
            ).isSuccess
        } else {
            false
        }

        return FcmSyncResult(
            token = token,
            subscribedTopic = activeTopic,
            syncedToFirestore = firestoreSynced,
            errorMessage = errorMsg
        )
    }
}
