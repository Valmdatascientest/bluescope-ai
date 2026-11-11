package com.bluescope.ai.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "devices",
    indices = [
        Index(value = ["lastSeenAt"]),
        Index(value = ["displayName"]),
        Index(value = ["manufacturerName"]),
        Index(value = ["addressLookupHash"]),
    ],
)
data class DeviceEntity(
    @PrimaryKey val id: String,
    val displayName: String?,
    val advertisedName: String?,
    val transport: String,
    val category: String?,
    val manufacturerId: Int?,
    val manufacturerName: String?,
    val addressCiphertext: String?,
    val addressLookupHash: String?,
    val bondState: String,
    val deviceClass: Int?,
    val appearance: Int?,
    val latestRssi: Int?,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val lastVerifiedAt: Long?,
)

@Entity(tableName = "device_aliases", indices = [Index("deviceId"), Index("lookupHash", unique = true)])
data class DeviceAliasEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val lookupHash: String,
    val addressCiphertext: String?,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
)

@Entity(tableName = "scan_sessions", indices = [Index("startedAt")])
data class ScanSessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val requestedTransports: String,
    val status: String,
    val errorCode: String?,
)

@Entity(
    tableName = "observations",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deviceId"), Index("sessionId"), Index("observedAt")],
)
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val sessionId: String,
    val observedAt: Long,
    val transport: String,
    val rssi: Int?,
    val txPower: Int?,
    val name: String?,
    val addressCiphertext: String?,
    val addressLookupHash: String?,
    val serviceUuidsJson: String,
    val manufacturerDataJson: String,
    val serviceDataJson: String,
    val payloadFingerprint: String?,
    val connectable: Boolean?,
)

@Entity(
    tableName = "gatt_services",
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["id"],
        childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("deviceId"), Index(value = ["deviceId", "uuid"])],
)
data class GattServiceEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val uuid: String,
    val instanceId: Int,
    val isPrimary: Boolean,
    val discoveredAt: Long,
)

@Entity(
    tableName = "gatt_characteristics",
    foreignKeys = [ForeignKey(
        entity = GattServiceEntity::class,
        parentColumns = ["id"],
        childColumns = ["serviceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("serviceId")],
)
data class GattCharacteristicEntity(
    @PrimaryKey val id: String,
    val serviceId: String,
    val uuid: String,
    val instanceId: Int,
    val properties: Int,
    val permissions: Int,
    val valueBase64: String?,
    val readStatus: String?,
)

@Entity(
    tableName = "gatt_descriptors",
    foreignKeys = [ForeignKey(
        entity = GattCharacteristicEntity::class,
        parentColumns = ["id"],
        childColumns = ["characteristicId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("characteristicId")],
)
data class GattDescriptorEntity(
    @PrimaryKey val id: String,
    val characteristicId: String,
    val uuid: String,
    val permissions: Int,
    val valueBase64: String?,
)

@Entity(
    tableName = "analyses",
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["id"],
        childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("deviceId"), Index("createdAt")],
)
data class AnalysisEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val createdAt: Long,
    val engineVersion: String,
    val summary: String,
    val category: String?,
    val manufacturer: String?,
    val confidence: Double,
    val isRemote: Boolean,
)

@Entity(
    tableName = "claims",
    foreignKeys = [ForeignKey(
        entity = AnalysisEntity::class,
        parentColumns = ["id"],
        childColumns = ["analysisId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("analysisId")],
)
data class ClaimEntity(
    @PrimaryKey val id: String,
    val analysisId: String,
    val title: String,
    val description: String,
    val status: String,
    val confidence: Double,
)

@Entity(
    tableName = "evidence",
    foreignKeys = [ForeignKey(
        entity = ClaimEntity::class,
        parentColumns = ["id"],
        childColumns = ["claimId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("claimId")],
)
data class EvidenceEntity(
    @PrimaryKey val id: String,
    val claimId: String,
    val source: String,
    val technicalKey: String,
    val technicalValue: String,
    val observedAt: Long?,
)

@Entity(
    tableName = "user_metadata",
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["id"],
        childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("deviceId", unique = true), Index("isFavorite")],
)
data class UserMetadataEntity(
    @PrimaryKey val deviceId: String,
    val isFavorite: Boolean = false,
    val note: String = "",
    val updatedAt: Long,
)
