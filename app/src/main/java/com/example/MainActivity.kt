package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.firebase.configureAppCheck
import com.example.notifications.TrendNotificationHelper
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.ViralKitTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val openTrendsFromNotification = MutableStateFlow(false)
    private val trendingTopicFromNotification = MutableStateFlow<String?>(null)
    private val openVaultKitIdFromNotification = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before rendering UI or creating ViewModels
        configureAppCheck(this, intent)
        TrendNotificationHelper.ensureChannel(this)
        handleNotificationIntent(intent)

        enableEdgeToEdge()
        setContent {
            ViralKitTheme {
                val shouldOpenTrends by openTrendsFromNotification.collectAsStateWithLifecycle()
                val trendingTopic by trendingTopicFromNotification.collectAsStateWithLifecycle()
                val vaultKitId by openVaultKitIdFromNotification.collectAsStateWithLifecycle()
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        openTrendsRequest = shouldOpenTrends,
                        trendingTopicRequest = trendingTopic,
                        openVaultKitIdRequest = vaultKitId,
                        onConsumeOpenTrends = {
                            openTrendsFromNotification.value = false
                            trendingTopicFromNotification.value = null
                        },
                        onConsumeOpenVaultKit = {
                            openVaultKitIdFromNotification.value = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return

        val fcmType = intent.getStringExtra("type")?.trim()?.lowercase()
        val vaultKitId = intent.getStringExtra(TrendNotificationHelper.EXTRA_OPEN_VAULT_KIT_ID)
            ?: if (fcmType == "strategy_complete") intent.getStringExtra("kitId") ?: "latest" else null
        if (!vaultKitId.isNullOrBlank()) {
            openVaultKitIdFromNotification.value = vaultKitId
            intent.removeExtra(TrendNotificationHelper.EXTRA_OPEN_VAULT_KIT_ID)
            intent.removeExtra("kitId")
            intent.removeExtra("type")
        }

        val shouldOpenTrends = intent.getBooleanExtra(TrendNotificationHelper.EXTRA_OPEN_TRENDS, false) ||
            fcmType == "trending_topic"
        if (shouldOpenTrends) {
            val topicExtra = intent.getStringExtra(TrendNotificationHelper.EXTRA_TRENDING_TOPIC)
                ?: intent.getStringExtra("topic")
            trendingTopicFromNotification.value = topicExtra
            openTrendsFromNotification.value = true
            intent.removeExtra(TrendNotificationHelper.EXTRA_OPEN_TRENDS)
            intent.removeExtra(TrendNotificationHelper.EXTRA_TRENDING_TOPIC)
            intent.removeExtra("topic")
            intent.removeExtra("type")
        }
    }
}
