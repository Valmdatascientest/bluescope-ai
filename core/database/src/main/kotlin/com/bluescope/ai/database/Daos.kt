package com.bluescope.ai.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Transaction
    @Query("SELECT * FROM devices ORDER BY lastSeenAt DESC")
    fun observeAll(): Flow<List<DeviceWithMetadata>>

    @Transaction
    @Query("SELECT * FROM devices WHERE id = :id")
    fun observeById(id: String): Flow<DeviceWithMetadata?>

    @Transaction
    @Query(
        """
        SELECT devices.* FROM devices
        LEFT JOIN user_metadata ON devices.id = user_metadata.deviceId
        WHERE :query = ''
           OR COALESCE(displayName, '') LIKE '%' || :query || '%'
           OR COALESCE(advertisedName, '') LIKE '%' || :query || '%'
           OR COALESCE(manufacturerName, '') LIKE '%' || :query || '%'
           OR COALESCE(category, '') LIKE '%' || :query || '%'
        ORDER BY user_metadata.isFavorite DESC, lastSeenAt DESC
        """,
    )
    fun search(query: String): Flow<List<DeviceWithMetadata>>

    @Query("SELECT * FROM devices WHERE addressLookupHash = :hash LIMIT 1")
    suspend fun findByAddressHash(hash: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun getById(id: String): DeviceEntity?

    @Transaction
    @Query("SELECT * FROM devices ORDER BY lastSeenAt DESC")
    suspend fun getAll(): List<DeviceWithMetadata>

    @Query("SELECT * FROM user_metadata WHERE deviceId = :deviceId")
    suspend fun getMetadata(deviceId: String): UserMetadataEntity?

    @Upsert
    suspend fun upsert(device: DeviceEntity)

    @Upsert
    suspend fun upsertMetadata(metadata: UserMetadataEntity)

    @Query("UPDATE devices SET category = :category, manufacturerName = COALESCE(:manufacturer, manufacturerName) WHERE id = :deviceId")
    suspend fun updateClassification(deviceId: String, category: String, manufacturer: String?)

    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM devices")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM devices")
    suspend fun count(): Int
}

@Dao
interface ScanDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: ScanSessionEntity)

    @Query("UPDATE scan_sessions SET endedAt = :endedAt, status = :status, errorCode = :errorCode WHERE id = :id")
    suspend fun finish(id: String, endedAt: Long, status: String, errorCode: String?)

    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ScanSessionEntity>>

    @Query("SELECT COUNT(*) FROM scan_sessions")
    suspend fun count(): Int
}

@Dao
interface ObservationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(observation: ObservationEntity): Long

    @Query("SELECT * FROM observations WHERE deviceId = :deviceId ORDER BY observedAt DESC LIMIT :limit")
    fun observeForDevice(deviceId: String, limit: Int): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations WHERE deviceId = :deviceId ORDER BY observedAt DESC LIMIT 1")
    fun observeLatestForDevice(deviceId: String): Flow<ObservationEntity?>

    @Query("DELETE FROM observations WHERE id NOT IN (SELECT id FROM observations ORDER BY observedAt DESC LIMIT :keep)")
    suspend fun pruneKeepingLatest(keep: Int)

    @Query("SELECT COUNT(*) FROM observations")
    suspend fun count(): Int
}

@Dao
interface GattDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServices(services: List<GattServiceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacteristics(characteristics: List<GattCharacteristicEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDescriptors(descriptors: List<GattDescriptorEntity>)

    @Query("DELETE FROM gatt_services WHERE deviceId = :deviceId")
    suspend fun deleteForDevice(deviceId: String)

    @Transaction
    @Query("SELECT * FROM gatt_services WHERE deviceId = :deviceId ORDER BY uuid")
    fun observeForDevice(deviceId: String): Flow<List<ServiceWithCharacteristics>>
}

@Dao
interface AnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: AnalysisEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClaims(claims: List<ClaimEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: List<EvidenceEntity>)

    @Transaction
    @Query("SELECT * FROM analyses WHERE deviceId = :deviceId ORDER BY createdAt DESC LIMIT 1")
    fun observeLatest(deviceId: String): Flow<AnalysisWithClaims?>

    @Query("DELETE FROM analyses WHERE deviceId = :deviceId")
    suspend fun deleteForDevice(deviceId: String)
}

@Dao
interface AliasDao {
    @Upsert
    suspend fun upsert(alias: DeviceAliasEntity)

    @Query("SELECT * FROM device_aliases WHERE lookupHash = :hash LIMIT 1")
    suspend fun find(hash: String): DeviceAliasEntity?
}
