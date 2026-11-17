package com.example.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.ai.ViralAiService
import com.example.firebase.configureAppCheck
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

private const val TAG = "TrendAlertWorker"
private const val UNIQUE_PERIODIC_WORK_NAME = "viralkit_periodic_trend_alerts"
private const val UNIQUE_IMMEDIATE_WORK_NAME = "viralkit_immediate_trend_alert"

class TrendAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefsStore = TrendAlertPrefsStore(applicationContext)
        val prefs = prefsStore.readPreferences()

        val isPeriodicRun = tags.contains(TAG_PERIODIC)
        if (isPeriodicRun && !prefs.enabled) {
            return Result.success()
        }

        // Initialize Firebase & App Check safely in background worker using persisted debug/recaptcha config
        configureAppCheck(applicationContext, null)

        return try {
            val aiService = ViralAiService()
            val alert = aiService.fetchIndustryTrendAlert(
                industryNiche = prefs.industryNiche,
                focus = prefs.focus,
                language = prefs.language,
                platform = prefs.platform
            )
            prefsStore.saveLatestAlert(alert)
            TrendNotificationHelper.postTrendNotification(applicationContext, alert)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Grounded trend alert worker failed (attempt $runAttemptCount)", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG_PERIODIC = "viralkit_trend_periodic"

        fun schedulePeriodic(context: Context, intervalHours: Long) {
            val safeHours = intervalHours.coerceAtLeast(1L)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<TrendAlertWorker>(
                safeHours,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag(TAG_PERIODIC)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
        }

        fun enqueueImmediateCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<TrendAlertWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
