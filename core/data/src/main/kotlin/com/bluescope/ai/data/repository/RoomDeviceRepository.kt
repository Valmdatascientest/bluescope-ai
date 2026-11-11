package com.bluescope.ai.data.repository

import androidx.room.withTransaction
import com.bluescope.ai.data.security.IdentifierProtector
import com.bluescope.ai.database.AnalysisDao
import com.bluescope.ai.database.AnalysisEntity
import com.bluescope.ai.database.BlueScopeDatabase
import com.bluescope.ai.database.ClaimEntity
import com.bluescope.ai.database.DeviceDao
import com.bluescope.ai.database.DeviceEntity
import com.bluescope.ai.database.EvidenceEntity
import com.bluescope.ai.database.GattCharacteristicEntity
import com.bluescope.ai.database.GattDao
import com.bluescope.ai.database.GattDescriptorEntity
import com.bluescope.ai.database.GattServiceEntity
import com.bluescope.ai.database.ObservationDao
import com.bluescope.ai.database.ObservationEntity
import com.bluescope.ai.database.ScanDao
import com.bluescope.ai.database.ScanSessionEntity
import com.bluescope.ai.database.UserMetadataEntity
import com.bluescope.ai.domain.model.BluetoothDeviceSnapshot
import com.bluescope.ai.domain.model.BondState
import com.bluescope.ai.domain.model.DeviceAnalysis
import com.bluescope.ai.domain.model.DeviceId
import com.bluescope.ai.domain.model.DeviceQuery
import com.bluescope.ai.domain.model.DeviceSort
import com.bluescope.ai.domain.model.ExportPayload
import com.bluescope.ai.domain.model.ExportRequest
import com.bluescope.ai.domain.model.GattProperty
import com.bluescope.ai.domain.model.GattReadStatus
import com.bluescope.ai.domain.model.GattSnapshot
import com.bluescope.ai.domain.model.ImportConflictPolicy
import com.bluescope.ai.domain.model.ImportReport
import com.bluescope.ai.domain.model.KnownDevice
import com.bluescope.ai.domain.model.ScanSession
import com.bluescope.ai.domain.model.ScanSessionId
import com.bluescope.ai.domain.repository.DeviceRepository
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class RoomDeviceRepository(
    private val database: BlueScopeDatabase,
    private val protector: IdentifierProtector,
    private val clock: Clock = Clock.systemUTC(),
) : DeviceRepository {
    private val deviceDao: DeviceDao = database.deviceDao()
    private val scanDao: ScanDao = database.scanDao()
    private val observationDao: ObservationDao = database.observationDao()
    private val gattDao: GattDao = database.gattDao()
    private val analysisDao: AnalysisDao = database.analysisDao()

    override fun observeDevices(query: DeviceQuery): Flow<List<KnownDevice>> =
        deviceDao.search(query.text.trim()).map { devices ->
            devices.asSequence()
                .map {
                    it.toDomain(
                        observation = null,
                        analysis = it.analyses.maxByOrNull { relation -> relation.analysis.createdAt },
                        protector = protector,
                    )
                }
                .filter { known -> query.transports.isEmpty() || known.snapshot.transport in query.transports }
                .filter { known ->
                    query.categories.isEmpty() || known.latestAnalysis?.category in query.categories
                }
                .filter { known -> !query.favoritesOnly || known.isFavorite }
                .let { sequence ->
                    when (query.sort) {
                        DeviceSort.LAST_SEEN_DESC -> sequence.sortedByDescending { it.snapshot.lastSeenAt }
                        DeviceSort.FIRST_SEEN_DESC -> sequence.sortedByDescending { it.snapshot.firstSeenAt }
                        DeviceSort.NAME_ASC -> sequence.sortedBy { it.snapshot.displayName.lowercase() }
                        DeviceSort.SIGNAL_DESC -> sequence.sortedByDescending { it.snapshot.rssi ?: Int.MIN_VALUE }
                    }
                }
                .toList()
        }

    override fun observeDevice(id: DeviceId): Flow<KnownDevice?> = combine(
        deviceDao.observeById(id.value),
        observationDao.observeLatestForDevice(id.value),
        gattDao.observeForDevice(id.value),
        analysisDao.observeLatest(id.value),
    ) { device, observation, gatt, analysis ->
        device?.toDomain(observation, gatt, analysis, protector)
    }

    override suspend fun startSession(session: ScanSession) {
        scanDao.insert(
            ScanSessionEntity(
                id = session.id.value,
                startedAt = session.startedAt.toEpochMilli(),
                endedAt = session.endedAt?.toEpochMilli(),
                requestedTransports = session.requestedTransports.joinToString(",") { it.name },
                status = if (session.endedAt == null) "RUNNING" else "FINISHED",
                errorCode = null,
            ),
        )
    }

    override suspend fun finishSession(session: ScanSession) {
        scanDao.finish(
            id = session.id.value,
            endedAt = (session.endedAt ?: clock.instant()).toEpochMilli(),
            status = "FINISHED",
            errorCode = null,
        )
    }

    override suspend fun recordObservation(
        sessionId: ScanSessionId,
        snapshot: BluetoothDeviceSnapshot,
    ): DeviceId = database.withTransaction {
        val protectedAddress = snapshot.address?.let { address ->
            ProtectedAddress(
                ciphertext = protector.encrypt(address.value),
                lookupHash = protector.lookupHash(address.value),
            )
        }
        val canUseAddressAsIdentity = snapshot.address?.isStable == true || snapshot.bondState == BondState.BONDED
        val existing = deviceDao.getById(snapshot.id.value)
            ?: protectedAddress?.takeIf { canUseAddressAsIdentity }?.let {
                deviceDao.findByAddressHash(it.lookupHash)
            }
        val id = existing?.id ?: snapshot.id.value
        val manufacturerId = snapshot.manufacturerData.keys.minOrNull()
        deviceDao.upsert(
            DeviceEntity(
                id = id,
                displayName = snapshot.name ?: existing?.displayName,
                advertisedName = snapshot.name ?: existing?.advertisedName,
                transport = snapshot.transport.name,
                category = existing?.category,
                manufacturerId = manufacturerId ?: existing?.manufacturerId,
                manufacturerName = existing?.manufacturerName,
                addressCiphertext = protectedAddress?.ciphertext ?: existing?.addressCiphertext,
                addressLookupHash = protectedAddress?.lookupHash ?: existing?.addressLookupHash,
                bondState = snapshot.bondState.name,
                deviceClass = snapshot.deviceClass ?: existing?.deviceClass,
                appearance = snapshot.appearance ?: existing?.appearance,
                latestRssi = snapshot.rssi,
                firstSeenAt = minOf(existing?.firstSeenAt ?: Long.MAX_VALUE, snapshot.firstSeenAt.toEpochMilli()),
                lastSeenAt = maxOf(existing?.lastSeenAt ?: Long.MIN_VALUE, snapshot.lastSeenAt.toEpochMilli()),
                lastVerifiedAt = snapshot.lastSeenAt.toEpochMilli(),
            ),
        )
        observationDao.insert(
            ObservationEntity(
                deviceId = id,
                sessionId = sessionId.value,
                observedAt = snapshot.lastSeenAt.toEpochMilli(),
                transport = snapshot.transport.name,
                rssi = snapshot.rssi,
                txPower = snapshot.txPower,
                name = snapshot.name,
                addressCiphertext = protectedAddress?.ciphertext,
                addressLookupHash = protectedAddress?.lookupHash,
                serviceUuidsJson = snapshot.serviceUuids.toJson(),
                manufacturerDataJson = snapshot.manufacturerData.toBinaryJson(),
                serviceDataJson = snapshot.serviceData.toBinaryJson(),
                payloadFingerprint = null,
                connectable = snapshot.connectable,
            ),
        )
        DeviceId(id)
    }

    override suspend fun saveGatt(snapshot: GattSnapshot) = database.withTransaction {
        gattDao.deleteForDevice(snapshot.deviceId.value)
        val services = mutableListOf<GattServiceEntity>()
        val characteristics = mutableListOf<GattCharacteristicEntity>()
        val descriptors = mutableListOf<GattDescriptorEntity>()
        snapshot.services.forEachIndexed { serviceIndex, service ->
            val serviceId = "${snapshot.deviceId.value}:${service.uuid}:$serviceIndex"
            services += GattServiceEntity(
                id = serviceId,
                deviceId = snapshot.deviceId.value,
                uuid = service.uuid.toString(),
                instanceId = serviceIndex,
                isPrimary = service.type.name == "PRIMARY",
                discoveredAt = snapshot.discoveredAt.toEpochMilli(),
            )
            service.characteristics.forEachIndexed { characteristicIndex, characteristic ->
                val characteristicId = "$serviceId:${characteristic.uuid}:$characteristicIndex"
                characteristics += GattCharacteristicEntity(
                    id = characteristicId,
                    serviceId = serviceId,
                    uuid = characteristic.uuid.toString(),
                    instanceId = characteristicIndex,
                    properties = characteristic.properties.toPropertyMask(),
                    permissions = 0,
                    valueBase64 = characteristic.value?.let {
                        Base64.getEncoder().encodeToString(it.toByteArray())
                    },
                    readStatus = characteristic.readStatus.name,
                )
                characteristic.descriptors.forEachIndexed { descriptorIndex, descriptor ->
                    descriptors += GattDescriptorEntity(
                        id = "$characteristicId:${descriptor.uuid}:$descriptorIndex",
                        characteristicId = characteristicId,
                        uuid = descriptor.uuid.toString(),
                        permissions = 0,
                        valueBase64 = descriptor.value?.let {
                            Base64.getEncoder().encodeToString(it.toByteArray())
                        },
                    )
                }
            }
        }
        if (services.isNotEmpty()) gattDao.insertServices(services)
        if (characteristics.isNotEmpty()) gattDao.insertCharacteristics(characteristics)
        if (descriptors.isNotEmpty()) gattDao.insertDescriptors(descriptors)
    }

    override suspend fun saveAnalysis(analysis: DeviceAnalysis) = database.withTransaction {
        analysisDao.insertAnalysis(
            AnalysisEntity(
                id = analysis.id.value,
                deviceId = analysis.deviceId.value,
                createdAt = analysis.analyzedAt.toEpochMilli(),
                engineVersion = analysis.engineVersion,
                summary = analysis.summary,
                category = analysis.category.name,
                manufacturer = analysis.probableManufacturer?.name,
                confidence = analysis.confidence.value,
                isRemote = analysis.isRemote,
            ),
        )
        val claims = analysis.claims.mapIndexed { index, claim ->
            ClaimEntity(
                id = "${analysis.id.value}:$index",
                analysisId = analysis.id.value,
                title = claim.title,
                description = claim.explanation,
                status = claim.status.name,
                confidence = claim.confidence.value,
            )
        }
        if (claims.isNotEmpty()) analysisDao.insertClaims(claims)
        val evidence = analysis.claims.flatMapIndexed { claimIndex, claim ->
            claim.evidence.mapIndexed { evidenceIndex, item ->
                EvidenceEntity(
                    id = "${analysis.id.value}:$claimIndex:$evidenceIndex",
                    claimId = "${analysis.id.value}:$claimIndex",
                    source = item.source.name,
                    technicalKey = item.type.name,
                    technicalValue = item.value,
                    observedAt = item.observedAt?.toEpochMilli(),
                )
            }
        }
        if (evidence.isNotEmpty()) analysisDao.insertEvidence(evidence)
        deviceDao.updateClassification(
            analysis.deviceId.value,
            analysis.category.name,
            analysis.probableManufacturer?.name,
        )
    }

    override suspend fun setFavorite(id: DeviceId, favorite: Boolean) {
        val current = deviceDao.getMetadata(id.value)
        deviceDao.upsertMetadata(
            UserMetadataEntity(
                deviceId = id.value,
                isFavorite = favorite,
                note = current?.note.orEmpty(),
                updatedAt = clock.millis(),
            ),
        )
    }

    override suspend fun setNote(id: DeviceId, note: String?) {
        val current = deviceDao.getMetadata(id.value)
        deviceDao.upsertMetadata(
            UserMetadataEntity(
                deviceId = id.value,
                isFavorite = current?.isFavorite ?: false,
                note = note.orEmpty().trim().take(MAX_NOTE_LENGTH),
                updatedAt = clock.millis(),
            ),
        )
    }

    override suspend fun deleteDevice(id: DeviceId) {
        deviceDao.delete(id.value)
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        database.clearAllTables()
    }

    override suspend fun export(request: ExportRequest): ExportPayload = withContext(Dispatchers.IO) {
        val allDevices = deviceDao.getAll().filter { !request.favoriteOnly || it.metadata?.isFavorite == true }
        val root = JSONObject()
            .put("schemaVersion", EXPORT_SCHEMA_VERSION)
            .put("exportedAt", clock.instant().toString())
            .put("addressesIncluded", request.includeAddresses)
            .put("rawPayloadsIncluded", false)
        val devices = JSONArray()
        allDevices.forEach { relation ->
            val entity = relation.device
            val json = JSONObject()
                .put("id", entity.id)
                .put("name", entity.displayName)
                .put("advertisedName", entity.advertisedName)
                .put("transport", entity.transport)
                .put("category", entity.category)
                .put("manufacturerId", entity.manufacturerId)
                .put("manufacturerName", entity.manufacturerName)
                .put("bondState", entity.bondState)
                .put("deviceClass", entity.deviceClass)
                .put("appearance", entity.appearance)
                .put("latestRssi", entity.latestRssi)
                .put("firstSeenAt", entity.firstSeenAt)
                .put("lastSeenAt", entity.lastSeenAt)
                .put("lastVerifiedAt", entity.lastVerifiedAt)
                .put("favorite", relation.metadata?.isFavorite ?: false)
                .put("note", relation.metadata?.note.orEmpty())
            if (request.includeAddresses) {
                val address = entity.addressCiphertext?.let { runCatching { protector.decrypt(it) }.getOrNull() }
                json.put("address", address)
            }
            devices.put(json)
        }
        root.put("devices", devices)
        ExportPayload(EXPORT_SCHEMA_VERSION, root.toString(2), clock.instant(), devices.length())
    }

    override suspend fun import(
        payload: String,
        conflictPolicy: ImportConflictPolicy,
    ): ImportReport = withContext(Dispatchers.IO) {
        if (payload.toByteArray().size > MAX_IMPORT_BYTES) {
            return@withContext ImportReport(0, 0, 1, listOf("Fichier supérieur à 5 Mo"))
        }
        runCatching {
            val root = JSONObject(payload)
            require(root.optInt("schemaVersion", -1) == EXPORT_SCHEMA_VERSION) {
                "Version d’export non prise en charge"
            }
            val devices = root.getJSONArray("devices")
            require(devices.length() <= MAX_IMPORT_DEVICES) { "Trop de périphériques dans l’import" }
            var imported = 0
            var skipped = 0
            database.withTransaction {
                repeat(devices.length()) { index ->
                    val json = devices.getJSONObject(index)
                    val sourceId = json.getString("id")
                    val current = deviceDao.getById(sourceId)
                    val importedLastSeen = json.getLong("lastSeenAt")
                    if (current != null && conflictPolicy == ImportConflictPolicy.KEEP_EXISTING) {
                        skipped++
                        return@repeat
                    }
                    if (current != null && conflictPolicy == ImportConflictPolicy.REPLACE_IF_NEWER &&
                        current.lastSeenAt >= importedLastSeen
                    ) {
                        skipped++
                        return@repeat
                    }
                    val id = if (current != null && conflictPolicy == ImportConflictPolicy.KEEP_BOTH) {
                        UUID.randomUUID().toString()
                    } else sourceId
                    val address = json.optStringOrNull("address")
                    deviceDao.upsert(
                        DeviceEntity(
                            id = id,
                            displayName = json.optStringOrNull("name"),
                            advertisedName = json.optStringOrNull("advertisedName"),
                            transport = json.optString("transport", "UNKNOWN"),
                            category = json.optStringOrNull("category"),
                            manufacturerId = json.optIntOrNull("manufacturerId"),
                            manufacturerName = json.optStringOrNull("manufacturerName"),
                            addressCiphertext = address?.let(protector::encrypt),
                            addressLookupHash = address?.let(protector::lookupHash),
                            bondState = json.optString("bondState", "UNKNOWN"),
                            deviceClass = json.optIntOrNull("deviceClass"),
                            appearance = json.optIntOrNull("appearance"),
                            latestRssi = json.optIntOrNull("latestRssi"),
                            firstSeenAt = json.getLong("firstSeenAt"),
                            lastSeenAt = importedLastSeen,
                            lastVerifiedAt = json.optLongOrNull("lastVerifiedAt"),
                        ),
                    )
                    deviceDao.upsertMetadata(
                        UserMetadataEntity(
                            deviceId = id,
                            isFavorite = json.optBoolean("favorite", false),
                            note = json.optString("note", "").take(MAX_NOTE_LENGTH),
                            updatedAt = clock.millis(),
                        ),
                    )
                    imported++
                }
            }
            ImportReport(imported, skipped, 0)
        }.getOrElse { error ->
            ImportReport(0, 0, 1, listOf(error.message ?: "Import JSON invalide"))
        }
    }

    private data class ProtectedAddress(val ciphertext: String, val lookupHash: String)

    private companion object {
        const val EXPORT_SCHEMA_VERSION = 1
        const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
        const val MAX_IMPORT_DEVICES = 10_000
        const val MAX_NOTE_LENGTH = 4_000
    }
}

private fun Set<GattProperty>.toPropertyMask(): Int = fold(0) { mask, property ->
    mask or when (property) {
        GattProperty.BROADCAST -> android.bluetooth.BluetoothGattCharacteristic.PROPERTY_BROADCAST
        GattProperty.READ -> android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ
        GattProperty.WRITE -> android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE
        GattProperty.WRITE_WITHOUT_RESPONSE -> android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        GattProperty.NOTIFY -> android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY
        GattProperty.INDICATE -> android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE
        GattProperty.SIGNED_WRITE -> android.bluetooth.BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE
        GattProperty.EXTENDED_PROPERTIES -> android.bluetooth.BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else getInt(key)

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else getLong(key)
