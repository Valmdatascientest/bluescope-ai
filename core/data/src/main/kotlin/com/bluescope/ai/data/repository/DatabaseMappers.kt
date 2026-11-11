package com.bluescope.ai.data.repository

import android.bluetooth.BluetoothGattCharacteristic
import com.bluescope.ai.data.security.IdentifierProtector
import com.bluescope.ai.database.AnalysisWithClaims
import com.bluescope.ai.database.DeviceWithMetadata
import com.bluescope.ai.database.ObservationEntity
import com.bluescope.ai.database.ServiceWithCharacteristics
import com.bluescope.ai.domain.model.AnalysisId
import com.bluescope.ai.domain.model.BinaryData
import com.bluescope.ai.domain.model.BluetoothAddressType
import com.bluescope.ai.domain.model.BluetoothDeviceSnapshot
import com.bluescope.ai.domain.model.BluetoothTransport
import com.bluescope.ai.domain.model.BondState
import com.bluescope.ai.domain.model.CapabilityClaim
import com.bluescope.ai.domain.model.ClaimStatus
import com.bluescope.ai.domain.model.Confidence
import com.bluescope.ai.domain.model.DeviceAnalysis
import com.bluescope.ai.domain.model.DeviceCategory
import com.bluescope.ai.domain.model.DeviceId
import com.bluescope.ai.domain.model.DiscoverySource
import com.bluescope.ai.domain.model.EvidenceSource
import com.bluescope.ai.domain.model.EvidenceType
import com.bluescope.ai.domain.model.Freshness
import com.bluescope.ai.domain.model.GattCharacteristicInfo
import com.bluescope.ai.domain.model.GattDescriptorInfo
import com.bluescope.ai.domain.model.GattProperty
import com.bluescope.ai.domain.model.GattReadStatus
import com.bluescope.ai.domain.model.GattServiceInfo
import com.bluescope.ai.domain.model.GattServiceType
import com.bluescope.ai.domain.model.GattSnapshot
import com.bluescope.ai.domain.model.KnownDevice
import com.bluescope.ai.domain.model.ManufacturerAssessment
import com.bluescope.ai.domain.model.ObservedAddress
import com.bluescope.ai.domain.model.TechnicalEvidence
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal fun DeviceWithMetadata.toDomain(
    observation: ObservationEntity?,
    gatt: List<ServiceWithCharacteristics> = emptyList(),
    analysis: AnalysisWithClaims? = null,
    protector: IdentifierProtector,
): KnownDevice {
    val address = device.addressCiphertext?.let { encrypted ->
        runCatching { protector.decrypt(encrypted) }.getOrNull()
    }?.let { ObservedAddress(it, BluetoothAddressType.UNKNOWN, isStable = true) }
    val snapshot = BluetoothDeviceSnapshot(
        id = DeviceId(device.id),
        name = device.displayName ?: device.advertisedName,
        address = address,
        transport = enumValueOrDefault(device.transport, BluetoothTransport.UNKNOWN),
        rssi = device.latestRssi,
        txPower = observation?.txPower,
        bondState = enumValueOrDefault(device.bondState, BondState.UNKNOWN),
        deviceClass = device.deviceClass,
        appearance = device.appearance,
        serviceUuids = parseUuidArray(observation?.serviceUuidsJson),
        manufacturerData = parseBinaryObject(observation?.manufacturerDataJson).mapNotNull { (key, value) ->
            key.toIntOrNull()?.let { it to value }
        }.toMap(),
        serviceData = parseBinaryObject(observation?.serviceDataJson).mapNotNull { (key, value) ->
            runCatching { UUID.fromString(key) }.getOrNull()?.let { it to value }
        }.toMap(),
        firstSeenAt = Instant.ofEpochMilli(device.firstSeenAt),
        lastSeenAt = Instant.ofEpochMilli(device.lastSeenAt),
        freshness = Freshness.CACHED,
        source = DiscoverySource.IMPORTED,
        connectable = observation?.connectable,
    )
    return KnownDevice(
        snapshot = snapshot,
        latestGatt = gatt.takeIf(List<*>::isNotEmpty)?.toGattSnapshot(DeviceId(device.id)),
        latestAnalysis = analysis?.toDomain(),
        isFavorite = metadata?.isFavorite ?: false,
        note = metadata?.note?.takeIf(String::isNotBlank),
        lastVerifiedAt = device.lastVerifiedAt?.let(Instant::ofEpochMilli),
    )
}

internal fun List<ServiceWithCharacteristics>.toGattSnapshot(deviceId: DeviceId): GattSnapshot {
    val discoveredAt = maxOfOrNull { it.service.discoveredAt } ?: 0L
    return GattSnapshot(
        deviceId = deviceId,
        services = mapNotNull { relation ->
            val serviceUuid = runCatching { UUID.fromString(relation.service.uuid) }.getOrNull()
                ?: return@mapNotNull null
            GattServiceInfo(
                uuid = serviceUuid,
                type = if (relation.service.isPrimary) GattServiceType.PRIMARY else GattServiceType.SECONDARY,
                characteristics = relation.characteristics.mapNotNull { characteristicRelation ->
                    val characteristic = characteristicRelation.characteristic
                    val uuid = runCatching { UUID.fromString(characteristic.uuid) }.getOrNull()
                        ?: return@mapNotNull null
                    GattCharacteristicInfo(
                        uuid = uuid,
                        properties = characteristic.properties.toGattProperties(),
                        descriptors = characteristicRelation.descriptors.mapNotNull { descriptor ->
                            runCatching { UUID.fromString(descriptor.uuid) }.getOrNull()?.let {
                                GattDescriptorInfo(
                                    uuid = it,
                                    value = descriptor.valueBase64.decodeBinaryOrNull(),
                                )
                            }
                        },
                        value = characteristic.valueBase64.decodeBinaryOrNull(),
                        readStatus = characteristic.readStatus?.let {
                            enumValueOrDefault(it, GattReadStatus.NOT_ATTEMPTED)
                        } ?: GattReadStatus.NOT_ATTEMPTED,
                    )
                },
            )
        },
        discoveredAt = Instant.ofEpochMilli(discoveredAt),
        freshness = Freshness.CACHED,
    )
}

internal fun AnalysisWithClaims.toDomain(): DeviceAnalysis = DeviceAnalysis(
    id = AnalysisId(analysis.id),
    deviceId = DeviceId(analysis.deviceId),
    category = enumValueOrDefault(analysis.category ?: "", DeviceCategory.UNKNOWN),
    probableManufacturer = analysis.manufacturer?.let { manufacturer ->
        val evidence = claims.flatMap { it.evidence }.map { item ->
            TechnicalEvidence(
                type = enumValueOrDefault(item.technicalKey, EvidenceType.LOCAL_CATALOG),
                value = item.technicalValue,
                source = enumValueOrDefault(item.source, EvidenceSource.LOCAL_CATALOG),
                observedAt = item.observedAt?.let(Instant::ofEpochMilli),
            )
        }.ifEmpty {
            listOf(TechnicalEvidence(EvidenceType.LOCAL_CATALOG, manufacturer, EvidenceSource.LOCAL_CATALOG))
        }
        ManufacturerAssessment(manufacturer, Confidence.of(analysis.confidence), evidence)
    },
    summary = analysis.summary,
    claims = claims.map { relation ->
        CapabilityClaim(
            title = relation.claim.title,
            explanation = relation.claim.description,
            status = enumValueOrDefault(relation.claim.status, ClaimStatus.UNKNOWN),
            confidence = Confidence.of(relation.claim.confidence.coerceIn(0.0, 1.0)),
            evidence = relation.evidence.map { evidence ->
                TechnicalEvidence(
                    type = enumValueOrDefault(evidence.technicalKey, EvidenceType.LOCAL_CATALOG),
                    value = evidence.technicalValue,
                    source = enumValueOrDefault(evidence.source, EvidenceSource.LOCAL_CATALOG),
                    observedAt = evidence.observedAt?.let(Instant::ofEpochMilli),
                )
            },
        )
    },
    confidence = Confidence.of(analysis.confidence.coerceIn(0.0, 1.0)),
    analyzedAt = Instant.ofEpochMilli(analysis.createdAt),
    engineVersion = analysis.engineVersion,
    isRemote = analysis.isRemote,
)

internal fun Set<UUID>.toJson(): String = JSONArray().apply {
    sortedBy(UUID::toString).forEach { put(it.toString()) }
}.toString()

internal fun Map<*, BinaryData>.toBinaryJson(): String = JSONObject().apply {
    this@toBinaryJson.forEach { (key, value) ->
        put(key.toString(), Base64.getEncoder().encodeToString(value.toByteArray()))
    }
}.toString()

private fun parseUuidArray(value: String?): Set<UUID> = runCatching {
    val json = JSONArray(value ?: "[]")
    buildSet {
        repeat(json.length()) { index ->
            runCatching { UUID.fromString(json.getString(index)) }.getOrNull()?.let(::add)
        }
    }
}.getOrDefault(emptySet())

private fun parseBinaryObject(value: String?): Map<String, BinaryData> = runCatching {
    val json = JSONObject(value ?: "{}")
    buildMap {
        json.keys().forEach { key ->
            runCatching { Base64.getDecoder().decode(json.getString(key)) }
                .getOrNull()
                ?.let { put(key, BinaryData.of(it)) }
        }
    }
}.getOrDefault(emptyMap())

private fun Int.toGattProperties(): Set<GattProperty> = buildSet {
    if (this@toGattProperties and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) add(GattProperty.BROADCAST)
    if (this@toGattProperties and BluetoothGattCharacteristic.PROPERTY_READ != 0) add(GattProperty.READ)
    if (this@toGattProperties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add(GattProperty.WRITE)
    if (this@toGattProperties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add(GattProperty.WRITE_WITHOUT_RESPONSE)
    if (this@toGattProperties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add(GattProperty.NOTIFY)
    if (this@toGattProperties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add(GattProperty.INDICATE)
    if (this@toGattProperties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) add(GattProperty.SIGNED_WRITE)
    if (this@toGattProperties and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) add(GattProperty.EXTENDED_PROPERTIES)
}

private fun String?.decodeBinaryOrNull(): BinaryData? = this?.let { value ->
    runCatching { BinaryData.of(Base64.getDecoder().decode(value)) }.getOrNull()
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
