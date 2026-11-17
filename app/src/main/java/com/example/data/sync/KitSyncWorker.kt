package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.repository.ViralKitRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private const val TAG = "KitSyncWorker"

class KitSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = Firebase.auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: return Result.success()

        return try {
            val repository = ViralKitRepository(applicationContext)
            val summary = repository.syncPendingKitMutations(uid)
            Log.d(
                TAG,
                "Offline Room->Firestore sync completed: synced=${summary.syncedCount}, remaining=${summary.remainingPendingCount}"
            )
            if (summary.remainingPendingCount == 0) {
                repository.fetchKitsHistory(uid)
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Deferred Room->Firestore sync failed, scheduling retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "viralkit_pending_firestore_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<KitSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }
}
