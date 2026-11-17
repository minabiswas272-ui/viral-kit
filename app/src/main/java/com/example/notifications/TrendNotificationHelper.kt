package com.example.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.LatestTrendAlert
import com.example.data.model.ViralGrowthKit

object TrendNotificationHelper {

    const val CHANNEL_ID = "viral_trend_alerts"
    const val CHANNEL_STRATEGY_COMPLETE = "viral_strategy_complete"
    const val EXTRA_OPEN_TRENDS = "extra_open_trends"
    const val EXTRA_TRENDING_TOPIC = "extra_trending_topic"
    const val EXTRA_OPEN_VAULT_KIT_ID = "extra_open_vault_kit_id"

    private const val NOTIFICATION_ID = 4102
    private const val STRATEGY_NOTIFICATION_ID = 4103

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val trendChannel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.trend_alerts_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.trend_alerts_channel_desc)
                    enableVibration(true)
                }
                manager.createNotificationChannel(trendChannel)
            }
            if (manager.getNotificationChannel(CHANNEL_STRATEGY_COMPLETE) == null) {
                val strategyChannel = NotificationChannel(
                    CHANNEL_STRATEGY_COMPLETE,
                    context.getString(R.string.strategy_complete_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.strategy_complete_channel_desc)
                    enableVibration(true)
                }
                manager.createNotificationChannel(strategyChannel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        val runtimeGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun postTrendNotification(context: Context, alert: LatestTrendAlert): Boolean {
        ensureChannel(context)
        if (!hasNotificationPermission(context)) {
            return false
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TRENDS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val expandedBody = buildString {
            append(alert.alertSummary)
            if (alert.sources.isNotEmpty()) {
                append("\n\nSources: ")
                append(alert.sources.take(3).joinToString(" · ") { it.title.take(28) })
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trend_alert)
            .setContentTitle(alert.headline.ifBlank { "New ${alert.industryNiche} Trend Alert" })
            .setContentText(alert.alertSummary)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(alert.headline)
                    .bigText(expandedBody)
                    .setSummaryText("Google Search Grounded · ${alert.focus.label}")
            )
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    @SuppressLint("MissingPermission")
    fun postNicheTrendingTopicNotification(
        context: Context,
        title: String,
        body: String,
        niche: String? = null,
        topic: String? = null
    ): Boolean {
        ensureChannel(context)
        if (!hasNotificationPermission(context)) {
            return false
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TRENDS, true)
            val resolvedTopic = topic?.takeIf { it.isNotBlank() } ?: niche?.takeIf { it.isNotBlank() }
            if (resolvedTopic != null) {
                putExtra(EXTRA_TRENDING_TOPIC, resolvedTopic)
            }
        }
        val requestCode = NOTIFICATION_ID + (topic?.hashCode() ?: 0).and(0x0FFF)
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val safeTitle = title.ifBlank {
            if (!niche.isNullOrBlank()) "Trending in $niche" else "New Niche Trend Match"
        }
        val safeBody = body.ifBlank {
            if (!topic.isNullOrBlank()) "Breakout trend detected: $topic. Tap to inspect in Live Trend Radar."
            else "A new trending topic matches your creator niche."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trend_alert)
            .setContentTitle(safeTitle)
            .setContentText(safeBody)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(safeTitle)
                    .bigText(safeBody)
                    .setSummaryText(niche?.takeIf { it.isNotBlank() } ?: "FCM Niche Trend Alert")
            )
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    fun postStrategyCompleteNotification(context: Context, kit: ViralGrowthKit): Boolean {
        val summary = buildString {
            append("Viral Score ${kit.viralScore}/100 · ${kit.platform} (${kit.language}). ")
            if (kit.curiosityHook.isNotBlank()) {
                append("Hook: \"${kit.curiosityHook.take(90)}\"")
            } else {
                append(kit.diagnosisCritique.take(120))
            }
        }
        return postStrategyCompleteNotification(
            context = context,
            kitId = kit.id,
            title = kit.title,
            viralScore = kit.viralScore.toInt(),
            platform = kit.platform,
            summaryBody = summary
        )
    }

    @SuppressLint("MissingPermission")
    fun postStrategyCompleteNotification(
        context: Context,
        kitId: String?,
        title: String,
        viralScore: Int? = null,
        platform: String? = null,
        summaryBody: String
    ): Boolean {
        ensureChannel(context)
        if (!hasNotificationPermission(context)) {
            return false
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_VAULT_KIT_ID, kitId?.ifBlank { "latest" } ?: "latest")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            STRATEGY_NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val safeTitle = if (viralScore != null) {
            "Strategy Ready ($viralScore/100): $title"
        } else {
            title.ifBlank { "AI Strategy Analysis Complete" }
        }
        val safeBody = summaryBody.ifBlank {
            "Your Viral Growth Kit is analyzed and saved to your Strategy Library."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_STRATEGY_COMPLETE)
            .setSmallIcon(R.drawable.ic_trend_alert)
            .setContentTitle(safeTitle)
            .setContentText(safeBody)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(safeTitle)
                    .bigText(safeBody)
                    .setSummaryText(platform?.takeIf { it.isNotBlank() } ?: "ViralKit Strategy Vault")
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(STRATEGY_NOTIFICATION_ID, notification)
        return true
    }
}

