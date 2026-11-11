package com.bluescope.ai.database

import androidx.room.Embedded
import androidx.room.Relation

data class DeviceWithMetadata(
    @Embedded val device: DeviceEntity,
    @Relation(parentColumn = "id", entityColumn = "deviceId")
    val metadata: UserMetadataEntity?,
    @Relation(
        entity = AnalysisEntity::class,
        parentColumn = "id",
        entityColumn = "deviceId",
    )
    val analyses: List<AnalysisWithClaims>,
)

data class CharacteristicWithDescriptors(
    @Embedded val characteristic: GattCharacteristicEntity,
    @Relation(parentColumn = "id", entityColumn = "characteristicId")
    val descriptors: List<GattDescriptorEntity>,
)

data class ServiceWithCharacteristics(
    @Embedded val service: GattServiceEntity,
    @Relation(
        entity = GattCharacteristicEntity::class,
        parentColumn = "id",
        entityColumn = "serviceId",
    )
    val characteristics: List<CharacteristicWithDescriptors>,
)

data class ClaimWithEvidence(
    @Embedded val claim: ClaimEntity,
    @Relation(parentColumn = "id", entityColumn = "claimId")
    val evidence: List<EvidenceEntity>,
)

data class AnalysisWithClaims(
    @Embedded val analysis: AnalysisEntity,
    @Relation(
        entity = ClaimEntity::class,
        parentColumn = "id",
        entityColumn = "analysisId",
    )
    val claims: List<ClaimWithEvidence>,
)
