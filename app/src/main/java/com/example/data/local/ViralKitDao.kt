package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ViralKitDao {

    @Query(
        "SELECT * FROM viral_growth_kits " +
            "WHERE userId = :userId AND syncStatus != 'PENDING_DELETE' " +
            "ORDER BY createdAtMillis DESC"
    )
    fun observeKitsForUser(userId: String): Flow<List<ViralKitEntity>>

    @Query(
        "SELECT * FROM viral_growth_kits " +
            "WHERE userId = :userId AND syncStatus != 'PENDING_DELETE' " +
            "ORDER BY createdAtMillis DESC"
    )
    suspend fun getKitsForUser(userId: String): List<ViralKitEntity>

    @Query(
        "SELECT id FROM viral_growth_kits " +
            "WHERE userId = :userId AND syncStatus IN ('PENDING_CREATE', 'PENDING_UPDATE')"
    )
    fun observePendingSyncIds(userId: String): Flow<List<String>>

    @Query(
        "SELECT COUNT(*) FROM viral_growth_kits " +
            "WHERE userId = :userId AND syncStatus != 'SYNCED'"
    )
    fun observePendingMutationCount(userId: String): Flow<Int>

    @Query(
        "SELECT * FROM viral_growth_kits " +
            "WHERE userId = :userId AND syncStatus != 'SYNCED' " +
            "ORDER BY updatedAtMillis ASC"
    )
    suspend fun getPendingMutations(userId: String): List<ViralKitEntity>

    @Query("SELECT * FROM viral_growth_kits WHERE id = :kitId LIMIT 1")
    suspend fun getKitById(kitId: String): ViralKitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKit(entity: ViralKitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ViralKitEntity>)

    @Query(
        "UPDATE viral_growth_kits " +
            "SET syncStatus = :status, lastSyncError = :error, updatedAtMillis = :updatedAtMillis " +
            "WHERE id = :kitId"
    )
    suspend fun updateSyncStatus(
        kitId: String,
        status: String,
        error: String?,
        updatedAtMillis: Long
    )

    @Query("DELETE FROM viral_growth_kits WHERE id = :kitId")
    suspend fun deleteKitById(kitId: String)

    @Query("SELECT id FROM viral_growth_kits WHERE userId = :userId AND syncStatus != 'SYNCED'")
    suspend fun getPendingIdsForUser(userId: String): List<String>

    @Query("SELECT id FROM viral_growth_kits WHERE userId = :userId AND syncStatus = 'SYNCED'")
    suspend fun getSyncedIdsForUser(userId: String): List<String>

    @Query("DELETE FROM viral_growth_kits WHERE userId = :userId AND id IN (:ids)")
    suspend fun deleteKitsByIds(userId: String, ids: List<String>)

    @Transaction
    suspend fun reconcileRemoteSnapshot(
        userId: String,
        remoteKits: List<ViralKitEntity>,
        pruneMissingSynced: Boolean
    ) {
        val pendingIds = getPendingIdsForUser(userId).toSet()
        val toUpsert = remoteKits.filter { it.id.isNotBlank() && !pendingIds.contains(it.id) }
        if (toUpsert.isNotEmpty()) {
            upsertAll(toUpsert)
        }
        if (pruneMissingSynced) {
            val remoteIds = remoteKits.map { it.id }.toSet()
            val staleSyncedIds = getSyncedIdsForUser(userId).filter { !remoteIds.contains(it) }
            staleSyncedIds.chunked(400).forEach { chunk ->
                deleteKitsByIds(userId, chunk)
            }
        }
    }
}
