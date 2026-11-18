package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.local.KitSyncStatus
import com.example.data.local.ViralKitDao
import com.example.data.local.ViralKitDatabase
import com.example.data.local.ViralKitEntity
import com.example.data.model.ChatMessageRecord
import com.example.data.model.CreatorProfile
import com.example.data.model.GroundingSource
import com.example.data.model.StrategySession
import com.example.data.model.TaggedContentIdea
import com.example.data.model.TargetLanguage
import com.example.data.model.TargetPlatform
import com.example.data.model.ViralGrowthKit
import com.example.data.supabase.SupabaseRestClient
import com.example.data.sync.ConnectivityObserver
import com.example.data.sync.KitSyncWorker
import com.example.data.sync.NetworkConnectivityObserver
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

private const val TAG = "ViralKitRepository"
private const val FIRESTORE_WRITE_TIMEOUT_MS = 5000L

data class KitSyncSummary(
    val syncedCount: Int = 0,
    val failedCount: Int = 0,
    val remainingPendingCount: Int = 0
)

class ViralKitRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val kitDao: ViralKitDao? = null,
    private val connectivityObserver: ConnectivityObserver? = null,
    private val scheduleBackgroundSync: (() -> Unit)? = null,
    private val supabaseClient: SupabaseRestClient? = SupabaseRestClient()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
        kitDao = ViralKitDatabase.getInstance(context.applicationContext).viralKitDao(),
        connectivityObserver = NetworkConnectivityObserver(context.applicationContext),
        scheduleBackgroundSync = { KitSyncWorker.enqueue(context.applicationContext) },
        supabaseClient = SupabaseRestClient()
    )

    private val syncMutex = Mutex()

    val isSupabaseConfigured: Boolean
        get() = supabaseClient?.isConfigured == true

    fun isCurrentlyOnline(): Boolean = connectivityObserver?.isCurrentlyOnline() ?: true

    fun observeOnlineState(): Flow<Boolean> = connectivityObserver?.isOnline ?: flowOf(true)

    fun observePendingKitIds(userId: String): Flow<Set<String>> {
        val dao = kitDao ?: return flowOf(emptySet())
        return dao.observePendingSyncIds(userId).map { it.toSet() }
    }

    fun observePendingMutationCount(userId: String): Flow<Int> {
        val dao = kitDao ?: return flowOf(0)
        return dao.observePendingMutationCount(userId)
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun isTransientNetworkError(error: Exception): Boolean {
        return generateSequence<Throwable>(error) { it.cause }.any { cause ->
            when (cause) {
                is FirebaseFirestoreException -> cause.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                    cause.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ||
                    cause.code == FirebaseFirestoreException.Code.ABORTED
                is IOException -> true
                else -> false
            }
        }
    }

    fun observeProfile(userId: String): Flow<CreatorProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch for profile stream." }
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        CreatorProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertProfile(
        displayName: String,
        email: String?,
        language: TargetLanguage,
        platform: TargetPlatform
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val safeName = displayName.trim().ifBlank { "Creator" }.take(100)
            val safeEmail = email?.trim()?.takeIf { it.isNotEmpty() }?.take(160)
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val updates = buildMap<String, Any> {
                    put("displayName", safeName)
                    if (safeEmail != null) put("email", safeEmail)
                    put("preferredLanguage", language.label)
                    put("preferredPlatform", platform.label)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            } else {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", safeName)
                    if (safeEmail != null) put("email", safeEmail)
                    put("preferredLanguage", language.label)
                    put("preferredPlatform", platform.label)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
            mirrorProfileToSupabaseIfConfigured(uid, safeName, safeEmail, language, platform)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateFcmRegistration(
        fcmToken: String?,
        fcmNicheTopic: String?
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val safeToken = fcmToken?.trim()?.takeIf { it.isNotEmpty() }?.take(512)
            val safeTopic = fcmNicheTopic?.trim()?.takeIf { it.isNotEmpty() }?.take(120)
            if (safeToken == null && safeTopic == null) {
                return Result.success(Unit)
            }
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val updates = buildMap<String, Any> {
                    if (safeToken != null) put("fcmToken", safeToken)
                    if (safeTopic != null) put("fcmNicheTopic", safeTopic)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            } else {
                val safeName = auth.currentUser?.displayName?.trim()?.ifBlank { "Creator" }?.take(100) ?: "Creator"
                val safeEmail = auth.currentUser?.email?.trim()?.takeIf { it.isNotEmpty() }?.take(160)
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", safeName)
                    if (safeEmail != null) put("email", safeEmail)
                    put("preferredLanguage", TargetLanguage.ENGLISH.label)
                    put("preferredPlatform", TargetPlatform.INSTAGRAM_REELS.label)
                    if (safeToken != null) put("fcmToken", safeToken)
                    if (safeTopic != null) put("fcmNicheTopic", safeTopic)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeKits(userId: String): Flow<List<ViralGrowthKit>> = channelFlow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch for kits stream." }
        val colRef = db.collection("users").document(uid).collection("kits")

        if (kitDao == null) {
            colRef
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ViralGrowthKit::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
                .collect { send(it) }
            return@channelFlow
        }

        val remoteSyncJob = launch {
            colRef
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    Log.w(TAG, "Firestore kits listener paused; continuing with Room cache", exceptionToLog ?: error)
                }
                .collect { snapshot ->
                    val remoteEntities = snapshot.toObjects(
                        ViralGrowthKit::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { remoteKit ->
                        ViralKitEntity.fromDomain(
                            kit = remoteKit,
                            idOverride = remoteKit.id,
                            userIdOverride = uid,
                            syncStatus = KitSyncStatus.SYNCED
                        )
                    }
                    kitDao.reconcileRemoteSnapshot(
                        userId = uid,
                        remoteKits = remoteEntities,
                        pruneMissingSynced = !snapshot.metadata.isFromCache
                    )
                }
        }

        try {
            kitDao.observeKitsForUser(uid)
                .map { entities -> entities.map { it.toDomain() } }
                .collect { send(it) }
        } finally {
            remoteSyncJob.cancel()
        }
    }

    suspend fun fetchKitsHistory(userId: String): Result<List<ViralGrowthKit>> {
        val colPath = "users/$userId/kits"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch for kits history fetch." }
            val dao = kitDao

            if (!isCurrentlyOnline() && dao != null) {
                return Result.success(dao.getKitsForUser(uid).map { it.toDomain() })
            }

            if (dao != null) {
                syncPendingKitMutations(uid)
            }

            val snapshot = withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                db.collection("users").document(uid).collection("kits")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .await()
            }

            if (snapshot == null) {
                return if (dao != null) {
                    Result.success(dao.getKitsForUser(uid).map { it.toDomain() })
                } else {
                    Result.failure(IllegalStateException("Timed out fetching kits from Firestore."))
                }
            }

            val kits = snapshot.toObjects(
                ViralGrowthKit::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )

            if (dao != null) {
                val remoteEntities = kits.map { remoteKit ->
                    ViralKitEntity.fromDomain(
                        kit = remoteKit,
                        idOverride = remoteKit.id,
                        userIdOverride = uid,
                        syncStatus = KitSyncStatus.SYNCED
                    )
                }
                dao.reconcileRemoteSnapshot(
                    userId = uid,
                    remoteKits = remoteEntities,
                    pruneMissingSynced = !snapshot.metadata.isFromCache
                )
                Result.success(dao.getKitsForUser(uid).map { it.toDomain() })
            } else {
                Result.success(kits)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val dao = kitDao
            if (dao != null && isTransientNetworkError(e)) {
                val cached = runCatching { dao.getKitsForUser(userId).map { it.toDomain() } }.getOrNull()
                if (cached != null) return Result.success(cached)
            }
            handleFirestoreError(e, OperationType.LIST, colPath)
            Result.failure(e)
        }
    }

    suspend fun saveKit(kit: ViralGrowthKit): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/kits"
        return try {
            val uid = requireUserId()
            val docRef = if (kit.id.isNotBlank()) {
                db.collection("users").document(uid).collection("kits").document(kit.id)
            } else {
                db.collection("users").document(uid).collection("kits").document()
            }
            docPath = docRef.path
            val kitId = docRef.id
            val nowMillis = System.currentTimeMillis()
            val sanitizedKit = sanitizeKit(kit, kitId, uid)
            val dao = kitDao

            if (dao != null) {
                val localEntity = ViralKitEntity.fromDomain(
                    kit = sanitizedKit,
                    idOverride = kitId,
                    userIdOverride = uid,
                    syncStatus = KitSyncStatus.PENDING_CREATE,
                    nowMillis = nowMillis
                )
                dao.upsertKit(localEntity)

                if (!isCurrentlyOnline()) {
                    scheduleBackgroundSync?.invoke()
                    return Result.success(kitId)
                }

                val payload = buildKitCreatePayload(uid, sanitizedKit)
                val completed = try {
                    withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                        docRef.set(payload).await()
                    } != null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isTransientNetworkError(e)) {
                        dao.updateSyncStatus(kitId, KitSyncStatus.PENDING_CREATE, e.localizedMessage, nowMillis)
                        scheduleBackgroundSync?.invoke()
                        return Result.success(kitId)
                    }
                    dao.deleteKitById(kitId)
                    throw e
                }

                if (completed) {
                    dao.updateSyncStatus(kitId, KitSyncStatus.SYNCED, null, System.currentTimeMillis())
                    mirrorKitToSupabaseIfConfigured(sanitizedKit)
                } else {
                    scheduleBackgroundSync?.invoke()
                }
                return Result.success(kitId)
            }

            val payload = buildKitCreatePayload(uid, sanitizedKit)
            docRef.set(payload).await()
            mirrorKitToSupabaseIfConfigured(sanitizedKit)
            Result.success(kitId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateKitTrendingTopics(
        kitId: String,
        trendingTopics: List<String>,
        sources: List<GroundingSource>
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/kits/$kitId"
        return try {
            val uid = requireUserId()
            require(kitId.isNotBlank()) { "Kit ID cannot be blank" }
            val docRef = db.collection("users").document(uid).collection("kits").document(kitId)
            docPath = docRef.path
            val safeTopics = trendingTopics.map { it.trim() }.filter { it.isNotEmpty() }.take(12)
            val safeSources = sources.take(8)
            val dao = kitDao
            val nowMillis = System.currentTimeMillis()

            if (dao != null) {
                val existing = dao.getKitById(kitId)
                if (existing != null && existing.userId == uid) {
                    val nextStatus = if (existing.syncStatus == KitSyncStatus.PENDING_CREATE) {
                        KitSyncStatus.PENDING_CREATE
                    } else {
                        KitSyncStatus.PENDING_UPDATE
                    }
                    dao.upsertKit(
                        existing.copy(
                            trendingTopicsJson = ViralKitEntity.encodeStringList(safeTopics),
                            trendingTagSourcesJson = ViralKitEntity.encodeSources(safeSources),
                            updatedAtMillis = nowMillis,
                            syncStatus = nextStatus,
                            lastSyncError = null
                        )
                    )
                }
                if (!isCurrentlyOnline()) {
                    scheduleBackgroundSync?.invoke()
                    return Result.success(Unit)
                }
                val updates = mapOf(
                    "trendingTopics" to safeTopics,
                    "trendingTagSources" to safeSources.map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val completed = try {
                    withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                        docRef.update(updates).await()
                    } != null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isTransientNetworkError(e)) {
                        scheduleBackgroundSync?.invoke()
                        return Result.success(Unit)
                    }
                    throw e
                }
                if (completed) {
                    dao.updateSyncStatus(kitId, KitSyncStatus.SYNCED, null, System.currentTimeMillis())
                    dao.getKitById(kitId)?.toDomain()?.let { mirrorKitToSupabaseIfConfigured(it) }
                } else {
                    scheduleBackgroundSync?.invoke()
                }
                return Result.success(Unit)
            }

            val updates = mapOf(
                "trendingTopics" to safeTopics,
                "trendingTagSources" to safeSources.map { it.toMap() },
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateKitTitle(kitId: String, newTitle: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/kits/$kitId"
        return try {
            val uid = requireUserId()
            require(kitId.isNotBlank()) { "Kit ID cannot be blank" }
            val safeTitle = newTitle.trim().take(160)
            require(safeTitle.isNotEmpty()) { "Strategy title cannot be empty" }
            val docRef = db.collection("users").document(uid).collection("kits").document(kitId)
            docPath = docRef.path
            val dao = kitDao
            val nowMillis = System.currentTimeMillis()

            if (dao != null) {
                val existing = dao.getKitById(kitId)
                if (existing != null && existing.userId == uid) {
                    val nextStatus = if (existing.syncStatus == KitSyncStatus.PENDING_CREATE) {
                        KitSyncStatus.PENDING_CREATE
                    } else {
                        KitSyncStatus.PENDING_UPDATE
                    }
                    dao.upsertKit(
                        existing.copy(
                            title = safeTitle,
                            updatedAtMillis = nowMillis,
                            syncStatus = nextStatus,
                            lastSyncError = null
                        )
                    )
                }
                if (!isCurrentlyOnline()) {
                    scheduleBackgroundSync?.invoke()
                    return Result.success(Unit)
                }
                val completed = try {
                    withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                        docRef.update(
                            mapOf(
                                "title" to safeTitle,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    } != null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isTransientNetworkError(e)) {
                        scheduleBackgroundSync?.invoke()
                        return Result.success(Unit)
                    }
                    throw e
                }
                if (completed) {
                    dao.updateSyncStatus(kitId, KitSyncStatus.SYNCED, null, System.currentTimeMillis())
                    dao.getKitById(kitId)?.toDomain()?.let { mirrorKitToSupabaseIfConfigured(it) }
                } else {
                    scheduleBackgroundSync?.invoke()
                }
                return Result.success(Unit)
            }

            docRef.update(
                mapOf(
                    "title" to safeTitle,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteKit(kitId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/kits/$kitId"
        return try {
            val uid = requireUserId()
            require(kitId.isNotBlank()) { "Kit ID cannot be blank" }
            val docRef = db.collection("users").document(uid).collection("kits").document(kitId)
            docPath = docRef.path
            val dao = kitDao
            val nowMillis = System.currentTimeMillis()

            if (dao != null) {
                val existing = dao.getKitById(kitId)
                if (existing != null && existing.syncStatus == KitSyncStatus.PENDING_CREATE) {
                    dao.deleteKitById(kitId)
                    return Result.success(Unit)
                }

                if (existing != null && existing.userId == uid) {
                    dao.updateSyncStatus(kitId, KitSyncStatus.PENDING_DELETE, null, nowMillis)
                }

                if (!isCurrentlyOnline()) {
                    scheduleBackgroundSync?.invoke()
                    return Result.success(Unit)
                }

                val completed = try {
                    withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                        docRef.delete().await()
                    } != null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isTransientNetworkError(e)) {
                        scheduleBackgroundSync?.invoke()
                        return Result.success(Unit)
                    }
                    throw e
                }

                if (completed) {
                    dao.deleteKitById(kitId)
                    mirrorDeleteToSupabaseIfConfigured(kitId, uid)
                } else {
                    scheduleBackgroundSync?.invoke()
                }
                return Result.success(Unit)
            }

            docRef.delete().await()
            mirrorDeleteToSupabaseIfConfigured(kitId, uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncPendingKitMutations(userId: String): KitSyncSummary = syncMutex.withLock {
        val dao = kitDao ?: return@withLock KitSyncSummary()
        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() && it == userId }
            ?: return@withLock KitSyncSummary()

        val pending = dao.getPendingMutations(uid)
        if (pending.isEmpty() || !isCurrentlyOnline()) {
            return@withLock KitSyncSummary(
                syncedCount = 0,
                failedCount = 0,
                remainingPendingCount = pending.size
            )
        }

        var syncedCount = 0
        var failedCount = 0

        for (entity in pending) {
            val docRef = db.collection("users").document(uid).collection("kits").document(entity.id)
            try {
                when (entity.syncStatus) {
                    KitSyncStatus.PENDING_CREATE -> {
                        val domain = entity.toDomain()
                        val payload = buildKitCreatePayload(uid, domain)
                        docRef.set(payload).await()
                        dao.updateSyncStatus(entity.id, KitSyncStatus.SYNCED, null, System.currentTimeMillis())
                        mirrorKitToSupabaseIfConfigured(sanitizeKit(domain, entity.id, uid))
                        syncedCount++
                    }
                    KitSyncStatus.PENDING_UPDATE -> {
                        val domain = entity.toDomain()
                        val updates = mapOf(
                            "title" to domain.title.trim().ifBlank { "Viral Growth Kit" }.take(160),
                            "trendingTopics" to domain.trendingTopics.map { it.trim() }.filter { it.isNotEmpty() }.take(12),
                            "trendingTagSources" to domain.trendingTagSources.take(8).map { it.toMap() },
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        try {
                            docRef.update(updates).await()
                        } catch (updateError: FirebaseFirestoreException) {
                            if (updateError.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                                docRef.set(buildKitCreatePayload(uid, domain)).await()
                            } else {
                                throw updateError
                            }
                        }
                        dao.updateSyncStatus(entity.id, KitSyncStatus.SYNCED, null, System.currentTimeMillis())
                        mirrorKitToSupabaseIfConfigured(sanitizeKit(domain, entity.id, uid))
                        syncedCount++
                    }
                    KitSyncStatus.PENDING_DELETE -> {
                        docRef.delete().await()
                        dao.deleteKitById(entity.id)
                        mirrorDeleteToSupabaseIfConfigured(entity.id, uid)
                        syncedCount++
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedCount++
                dao.updateSyncStatus(
                    entity.id,
                    entity.syncStatus,
                    e.localizedMessage,
                    System.currentTimeMillis()
                )
                Log.w(TAG, "Failed to sync kit ${entity.id} (${entity.syncStatus})", e)
            }
        }

        val remaining = dao.getPendingMutations(uid).size
        KitSyncSummary(
            syncedCount = syncedCount,
            failedCount = failedCount,
            remainingPendingCount = remaining
        )
    }

    suspend fun syncAllKitsToSupabase(userId: String): Result<Int> {
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch for Supabase backup." }
            val client = supabaseClient?.takeIf { it.isConfigured }
                ?: return Result.failure(
                    IllegalStateException("Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets.")
                )
            if (!isCurrentlyOnline()) {
                return Result.failure(
                    IllegalStateException("Device is offline. Connect to the internet to back up to Supabase.")
                )
            }

            val kits = if (kitDao != null) {
                kitDao.getKitsForUser(uid).map { sanitizeKit(it.toDomain(), it.id, uid) }
            } else {
                db.collection("users").document(uid).collection("kits")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .await()
                    .toObjects(ViralGrowthKit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .map { sanitizeKit(it, it.id, uid) }
            }

            val safeName = auth.currentUser?.displayName?.trim()?.ifBlank { "Creator" }?.take(100) ?: "Creator"
            val safeEmail = auth.currentUser?.email?.trim()?.takeIf { it.isNotEmpty() }?.take(160)
            client.upsertCreatorProfile(
                userId = uid,
                displayName = safeName,
                email = safeEmail,
                language = TargetLanguage.ENGLISH,
                platform = TargetPlatform.INSTAGRAM_REELS
            ).onFailure {
                Log.w(TAG, "Supabase profile upsert warning during backup: ${it.message}")
            }

            client.upsertKits(kits).getOrThrow()
            Result.success(kits.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Supabase backup failed", e)
            Result.failure(e)
        }
    }

    suspend fun pullKitsFromSupabase(userId: String): Result<Int> {
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch for Supabase restore." }
            val client = supabaseClient?.takeIf { it.isConfigured }
                ?: return Result.failure(
                    IllegalStateException("Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets.")
                )
            if (!isCurrentlyOnline()) {
                return Result.failure(
                    IllegalStateException("Device is offline. Connect to the internet to restore from Supabase.")
                )
            }

            val remoteKits = client.fetchKitsForUser(uid).getOrThrow()
                .filter { it.id.isNotBlank() }
                .map { sanitizeKit(it, it.id, uid) }

            val dao = kitDao
            var importedCount = 0
            for (kit in remoteKits) {
                val existingLocal = dao?.getKitById(kit.id)
                if (existingLocal != null && existingLocal.syncStatus != KitSyncStatus.SYNCED) {
                    continue
                }
                val docRef = db.collection("users").document(uid).collection("kits").document(kit.id)
                runCatching {
                    withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                        docRef.set(buildKitCreatePayload(uid, kit)).await()
                    }
                }
                if (dao != null) {
                    val entity = ViralKitEntity.fromDomain(
                        kit = kit,
                        idOverride = kit.id,
                        userIdOverride = uid,
                        syncStatus = KitSyncStatus.SYNCED
                    )
                    dao.upsertKit(entity)
                }
                importedCount++
            }
            Result.success(importedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Supabase restore failed", e)
            Result.failure(e)
        }
    }

    private suspend fun mirrorProfileToSupabaseIfConfigured(
        userId: String,
        displayName: String,
        email: String?,
        language: TargetLanguage,
        platform: TargetPlatform
    ) {
        val client = supabaseClient?.takeIf { it.isConfigured } ?: return
        if (!isCurrentlyOnline()) return
        runCatching {
            client.upsertCreatorProfile(
                userId = userId,
                displayName = displayName,
                email = email,
                language = language,
                platform = platform
            ).getOrThrow()
        }.onFailure { error ->
            Log.w(TAG, "Best-effort Supabase profile sync skipped: ${error.message}")
        }
    }

    private suspend fun mirrorKitToSupabaseIfConfigured(kit: ViralGrowthKit) {
        val client = supabaseClient?.takeIf { it.isConfigured } ?: return
        if (!isCurrentlyOnline() || kit.id.isBlank() || kit.userId.isBlank()) return
        runCatching {
            client.upsertKit(kit).getOrThrow()
        }.onFailure { error ->
            Log.w(TAG, "Best-effort Supabase kit sync skipped: ${error.message}")
        }
    }

    private suspend fun mirrorDeleteToSupabaseIfConfigured(kitId: String, userId: String) {
        val client = supabaseClient?.takeIf { it.isConfigured } ?: return
        if (!isCurrentlyOnline() || kitId.isBlank() || userId.isBlank()) return
        runCatching {
            client.deleteKit(kitId, userId).getOrThrow()
        }.onFailure { error ->
            Log.w(TAG, "Best-effort Supabase delete sync skipped: ${error.message}")
        }
    }

    private fun sanitizeKit(kit: ViralGrowthKit, kitId: String, uid: String): ViralGrowthKit {
        val safeMediaKind = when (kit.mediaKind) {
            "video", "audio", "text" -> kit.mediaKind
            else -> "text"
        }
        return kit.copy(
            id = kitId,
            userId = uid,
            title = kit.title.trim().ifBlank { "Viral Growth Kit" }.take(160),
            mediaLabel = kit.mediaLabel.trim().take(120),
            mediaKind = safeMediaKind,
            language = TargetLanguage.fromLabel(kit.language).label,
            platform = TargetPlatform.fromLabel(kit.platform).label,
            modelUsed = kit.modelUsed.trim().ifBlank { "gemini-4-hydrogen" }.take(64),
            viralScore = kit.viralScore.coerceIn(0L, 100L),
            diagnosisCritique = kit.diagnosisCritique.trim().ifBlank { "No critique provided." }.take(2000),
            first30sStrategy = kit.first30sStrategy.trim().ifBlank { "No 30s strategy provided." }.take(2000),
            curiosityHook = kit.curiosityHook.trim().ifBlank { "Hook pending" }.take(600),
            contrarianHook = kit.contrarianHook.trim().ifBlank { "Hook pending" }.take(600),
            directValueHook = kit.directValueHook.trim().ifBlank { "Hook pending" }.take(600),
            pacingSegments = kit.pacingSegments.take(25),
            instagramCaption = kit.instagramCaption.trim().ifBlank { "Caption pending" }.take(3000),
            hashtags = kit.hashtags.map { it.trim() }.filter { it.isNotEmpty() }.take(15),
            youtubeTitle = kit.youtubeTitle.trim().ifBlank { "Untitled Short" }.take(120),
            youtubeDescription = kit.youtubeDescription.trim().ifBlank { "Short description" }.take(4000),
            thumbnailIdeas = kit.thumbnailIdeas.map { it.trim() }.filter { it.isNotEmpty() }.take(5),
            rawJson = kit.rawJson.trim().ifBlank { "{}" }.take(25000),
            trendingTopics = kit.trendingTopics.map { it.trim() }.filter { it.isNotEmpty() }.take(12),
            trendingTagSources = kit.trendingTagSources.take(8)
        )
    }

    private fun buildKitCreatePayload(uid: String, kit: ViralGrowthKit): Map<String, Any> {
        val safe = sanitizeKit(kit, kit.id, uid)
        return buildMap {
            put("userId", uid)
            put("title", safe.title)
            put("mediaLabel", safe.mediaLabel)
            put("mediaKind", safe.mediaKind)
            put("language", safe.language)
            put("platform", safe.platform)
            put("modelUsed", safe.modelUsed)
            put("viralScore", safe.viralScore)
            put("diagnosisCritique", safe.diagnosisCritique)
            put("first30sStrategy", safe.first30sStrategy)
            put("curiosityHook", safe.curiosityHook)
            put("contrarianHook", safe.contrarianHook)
            put("directValueHook", safe.directValueHook)
            put("pacingSegments", safe.pacingSegments.map { it.toMap() })
            put("instagramCaption", safe.instagramCaption)
            put("hashtags", safe.hashtags)
            put("youtubeTitle", safe.youtubeTitle)
            put("youtubeDescription", safe.youtubeDescription)
            put("thumbnailIdeas", safe.thumbnailIdeas)
            put("rawJson", safe.rawJson)
            if (safe.trendingTopics.isNotEmpty()) put("trendingTopics", safe.trendingTopics)
            val tagSources = safe.trendingTagSources.map { it.toMap() }
            if (tagSources.isNotEmpty()) put("trendingTagSources", tagSources)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
    }

    fun observeTaggedIdeas(userId: String): Flow<List<TaggedContentIdea>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch for tagged ideas stream." }
        val colRef = db.collection("users").document(uid).collection("taggedIdeas")
        emitAll(
            colRef
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TaggedContentIdea::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveTaggedIdea(idea: TaggedContentIdea): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/taggedIdeas"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("taggedIdeas").document()
            docPath = docRef.path
            val safeSource = if (idea.sourceType == "note") "note" else "voice"
            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("ideaText", idea.ideaText.trim().ifBlank { "Untitled idea" }.take(4000))
                put("sourceType", safeSource)
                put("industryNiche", idea.industryNiche.trim().ifBlank { "Creator Economy" }.take(120))
                put("language", TargetLanguage.fromLabel(idea.language).label)
                put("platform", TargetPlatform.fromLabel(idea.platform).label)
                put("extractedKeywords", idea.extractedKeywords.map { it.trim() }.filter { it.isNotEmpty() }.take(10))
                put("trendingTopics", idea.trendingTopics.map { it.trim() }.filter { it.isNotEmpty() }.take(12))
                put("groundingRationale", idea.groundingRationale.trim().take(1000))
                val sources = idea.groundingSources.take(8).map { it.toMap() }
                if (sources.isNotEmpty()) put("groundingSources", sources)
                put("createdAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTaggedIdea(ideaId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/taggedIdeas/$ideaId"
        return try {
            val uid = requireUserId()
            require(ideaId.isNotBlank()) { "Idea ID cannot be blank" }
            val docRef = db.collection("users").document(uid).collection("taggedIdeas").document(ideaId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeChatMessages(userId: String): Flow<List<ChatMessageRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch for chat stream." }
        val colRef = db.collection("users").document(uid).collection("chatMessages")
        emitAll(
            colRef
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessageRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveChatMessage(message: ChatMessageRecord): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/chatMessages"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("chatMessages").document()
            docPath = docRef.path
            val safeRole = if (message.role == "model") "model" else "user"
            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("role", safeRole)
                put("text", message.text.trim().ifBlank { "..." }.take(20000))
                put("modelId", message.modelId.trim().ifBlank { "gemini-4-hydrogen" }.take(64))
                val summary = message.thinkingSummary?.trim()?.takeIf { it.isNotEmpty() }?.take(10000)
                if (summary != null) put("thinkingSummary", summary)
                val sources = message.groundingSources.take(10).map { it.toMap() }
                if (sources.isNotEmpty()) put("groundingSources", sources)
                put("createdAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearChatHistory(): Result<Unit> {
        var colPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/chatMessages"
        return try {
            val uid = requireUserId()
            val colRef = db.collection("users").document(uid).collection("chatMessages")
            colPath = colRef.path
            val docs = colRef.get().await().documents
            for (chunk in docs.chunked(400)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colPath)
            Result.failure(e)
        }
    }

    fun observeStrategySessions(userId: String): Flow<List<StrategySession>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch for strategy sessions stream." }
        val colRef = db.collection("users").document(uid).collection("strategySessions")
        emitAll(
            colRef
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(40)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StrategySession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeSessionMessages(userId: String, sessionId: String): Flow<List<ChatMessageRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch for session messages stream." }
        if (sessionId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val colRef = db.collection("users")
            .document(uid)
            .collection("strategySessions")
            .document(sessionId)
            .collection("messages")
        emitAll(
            colRef
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessageRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun createStrategySession(
        title: String,
        modelId: String,
        language: TargetLanguage,
        platform: TargetPlatform
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/strategySessions"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("strategySessions").document()
            docPath = docRef.path
            val safeTitle = title.trim().ifBlank { "New Strategy Session" }.take(160)
            val safeModel = modelId.trim().ifBlank { "gemini-4-hydrogen" }.take(64)
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "lastMessagePreview" to "",
                "modelId" to safeModel,
                "language" to language.label,
                "platform" to platform.label,
                "messageCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun appendMessageToSession(
        sessionId: String?,
        message: ChatMessageRecord,
        language: TargetLanguage,
        platform: TargetPlatform,
        sessionTitleIfNew: String? = null
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/strategySessions"
        return try {
            val uid = requireUserId()
            val sessionsCol = db.collection("users").document(uid).collection("strategySessions")
            val sessionRef = if (!sessionId.isNullOrBlank()) {
                sessionsCol.document(sessionId)
            } else {
                sessionsCol.document()
            }
            docPath = sessionRef.path

            val safeRole = if (message.role == "model") "model" else "user"
            val safeText = message.text.trim().ifBlank { "..." }.take(20000)
            val safeModel = message.modelId.trim().ifBlank { "gemini-4-hydrogen" }.take(64)
            val preview = safeText.replace('\n', ' ').trim().take(280)

            val messageRef = sessionRef.collection("messages").document()
            val messagePayload = buildMap<String, Any> {
                put("userId", uid)
                put("role", safeRole)
                put("text", safeText)
                put("modelId", safeModel)
                val summary = message.thinkingSummary?.trim()?.takeIf { it.isNotEmpty() }?.take(10000)
                if (summary != null) put("thinkingSummary", summary)
                val sources = message.groundingSources.take(10).map { it.toMap() }
                if (sources.isNotEmpty()) put("groundingSources", sources)
                put("createdAt", FieldValue.serverTimestamp())
            }

            val existingSnap = sessionRef.get().await()
            val batch = db.batch()
            if (!existingSnap.exists()) {
                val safeTitle = sessionTitleIfNew?.trim()?.takeIf { it.isNotEmpty() }?.take(160)
                    ?: safeText.replace('\n', ' ').trim().take(60).ifBlank { "New Strategy Session" }
                val sessionPayload = mapOf(
                    "userId" to uid,
                    "title" to safeTitle,
                    "lastMessagePreview" to preview,
                    "modelId" to safeModel,
                    "language" to language.label,
                    "platform" to platform.label,
                    "messageCount" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(sessionRef, sessionPayload)
            } else {
                val updates = buildMap<String, Any> {
                    put("lastMessagePreview", preview)
                    put("modelId", safeModel)
                    put("language", language.label)
                    put("platform", platform.label)
                    put("messageCount", FieldValue.increment(1L))
                    put("updatedAt", FieldValue.serverTimestamp())
                    val currentTitle = existingSnap.getString("title").orEmpty()
                    val candidateTitle = sessionTitleIfNew?.trim()?.takeIf { it.isNotEmpty() }?.take(160)
                    if (candidateTitle != null && currentTitle == "New Strategy Session") {
                        put("title", candidateTitle)
                    }
                }
                batch.update(sessionRef, updates)
            }
            batch.set(messageRef, messagePayload)
            batch.commit().await()
            Result.success(sessionRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun renameStrategySession(sessionId: String, newTitle: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/strategySessions/$sessionId"
        return try {
            val uid = requireUserId()
            require(sessionId.isNotBlank()) { "Session ID cannot be blank" }
            val safeTitle = newTitle.trim().take(160)
            require(safeTitle.isNotEmpty()) { "Session title cannot be empty" }
            val docRef = db.collection("users").document(uid)
                .collection("strategySessions").document(sessionId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "title" to safeTitle,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteStrategySession(sessionId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/strategySessions/$sessionId"
        return try {
            val uid = requireUserId()
            require(sessionId.isNotBlank()) { "Session ID cannot be blank" }
            val sessionRef = db.collection("users").document(uid)
                .collection("strategySessions").document(sessionId)
            docPath = sessionRef.path
            val msgDocs = sessionRef.collection("messages").get().await().documents
            for (chunk in msgDocs.chunked(400)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            sessionRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun migrateLegacyChatIfNeeded(userId: String): Result<String?> {
        var colPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/strategySessions"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch for legacy chat migration." }
            val userRef = db.collection("users").document(uid)
            val sessionsCol = userRef.collection("strategySessions")
            colPath = sessionsCol.path
            val existingSessions = sessionsCol.limit(1).get().await()
            if (!existingSessions.isEmpty) {
                return Result.success(null)
            }
            val legacyDocs = userRef.collection("chatMessages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(100)
                .get()
                .await()
            if (legacyDocs.isEmpty) {
                return Result.success(null)
            }

            val legacyMessages = legacyDocs.toObjects(
                ChatMessageRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val firstUserText = legacyMessages.firstOrNull { it.role == "user" }?.text.orEmpty()
            val lastMessage = legacyMessages.last()
            val newSessionRef = sessionsCol.document()
            val batch = db.batch()
            batch.set(
                newSessionRef,
                mapOf(
                    "userId" to uid,
                    "title" to firstUserText.replace('\n', ' ').trim().take(60)
                        .ifBlank { "Previous Strategy Session" },
                    "lastMessagePreview" to lastMessage.text.replace('\n', ' ').trim().take(280),
                    "modelId" to lastMessage.modelId.ifBlank { "gemini-4-hydrogen" }.take(64),
                    "language" to TargetLanguage.ENGLISH.label,
                    "platform" to TargetPlatform.INSTAGRAM_REELS.label,
                    "messageCount" to legacyMessages.size.toLong(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            legacyMessages.forEach { msg ->
                val msgRef = newSessionRef.collection("messages").document()
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("role", if (msg.role == "model") "model" else "user")
                    put("text", msg.text.trim().ifBlank { "..." }.take(20000))
                    put("modelId", msg.modelId.trim().ifBlank { "gemini-4-hydrogen" }.take(64))
                    val summary = msg.thinkingSummary?.trim()?.takeIf { it.isNotEmpty() }?.take(10000)
                    if (summary != null) put("thinkingSummary", summary)
                    val sources = msg.groundingSources.take(10).map { it.toMap() }
                    if (sources.isNotEmpty()) put("groundingSources", sources)
                    put("createdAt", FieldValue.serverTimestamp())
                }
                batch.set(msgRef, payload)
            }
            batch.commit().await()
            Result.success(newSessionRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, colPath)
            Result.failure(e)
        }
    }
}
