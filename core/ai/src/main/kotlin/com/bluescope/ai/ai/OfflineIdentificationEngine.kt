package com.bluescope.ai.ai

import com.bluescope.ai.domain.model.BluetoothDeviceSnapshot
import com.bluescope.ai.domain.model.ClaimStatus
import com.bluescope.ai.domain.model.Confidence
import com.bluescope.ai.domain.model.DeviceAnalysis
import com.bluescope.ai.domain.model.DeviceCategory
import com.bluescope.ai.domain.model.DiscoverySource
import com.bluescope.ai.domain.model.EvidenceSource
import com.bluescope.ai.domain.model.EvidenceType
import com.bluescope.ai.domain.model.GattReadStatus
import com.bluescope.ai.domain.model.GattServiceInfo
import com.bluescope.ai.domain.model.ManufacturerAssessment
import com.bluescope.ai.domain.model.CapabilityClaim
import com.bluescope.ai.domain.model.TechnicalEvidence
import com.bluescope.ai.domain.repository.IdentificationEngine
import java.time.Clock
import java.time.Instant

/**
 * Deterministic offline engine. It explains only signals that are present in the snapshot and
 * keeps possible real-world uses separate from directly observed protocol facts.
 */
class OfflineIdentificationEngine(
    private val catalog: AssignedNumbersCatalog = EmbeddedAssignedNumbersCatalog(),
    private val clock: Clock = Clock.systemUTC(),
) : IdentificationEngine {
    override suspend fun analyze(
        snapshot: BluetoothDeviceSnapshot,
        gattServices: List<GattServiceInfo>,
    ): DeviceAnalysis {
        val claims = mutableListOf<CapabilityClaim>()
        val categoryCandidates = mutableListOf<CategoryCandidate>()
        val observationSource = snapshot.source.toEvidenceSource()

        snapshot.appearance?.let { appearance ->
            catalog.appearance(appearance)?.let { definition ->
                val direct = TechnicalEvidence(
                    type = EvidenceType.APPEARANCE,
                    value = "0x${appearance.toString(16).uppercase().padStart(4, '0')} (${definition.description})",
                    source = observationSource,
                    observedAt = snapshot.lastSeenAt,
                )
                claims += factualClaim(
                    title = "Apparence déclarée : ${definition.category}",
                    explanation = "Le périphérique annonce une valeur Appearance standardisée.",
                    evidence = listOf(direct, assignedNumberEvidence("Appearance ${definition.value}")),
                    source = observationSource,
                    confidence = 0.92,
                )
                categoryCandidates += CategoryCandidate(definition.category.toDeviceCategory(), 0.82)
            }
        }

        snapshot.deviceClass?.let { deviceClass ->
            ClassicDeviceClassInterpreter.interpret(deviceClass)?.let { interpretation ->
                val direct = TechnicalEvidence(
                    type = EvidenceType.DEVICE_CLASS,
                    value = "0x${deviceClass.toString(16).uppercase()} (${interpretation.description})",
                    source = observationSource,
                    observedAt = snapshot.lastSeenAt,
                )
                claims += factualClaim(
                    title = "Classe Bluetooth : ${interpretation.category}",
                    explanation = "La classe majeure Classic observée indique cette famille générale.",
                    evidence = listOf(direct),
                    source = observationSource,
                    confidence = 0.88,
                )
                categoryCandidates += CategoryCandidate(interpretation.category.toDeviceCategory(), 0.76)
            }
        }

        snapshot.serviceUuids.sortedBy { it.toString() }.forEach { uuid ->
            val definition = catalog.service(uuid.toString()) ?: return@forEach
            val direct = TechnicalEvidence(
                type = EvidenceType.SERVICE_UUID,
                value = "${uuid.toString().uppercase()} (${definition.name})",
                source = observationSource,
                observedAt = snapshot.lastSeenAt,
            )
            claims += factualClaim(
                title = "Service ${definition.name} annoncé",
                explanation = "L'identifiant standard est présent dans les données observées ; cela ne prouve pas encore que le service est accessible.",
                evidence = listOf(direct, assignedNumberEvidence("Service ${definition.uuid}")),
                source = observationSource,
                confidence = 0.95,
            )
            claims += CapabilityClaim(
                title = "Usage possible : ${definition.possibleUse}",
                explanation = "Cet usage est associé au service standard, mais doit être confirmé par exploration GATT et, si nécessaire, lecture autorisée.",
                status = ClaimStatus.INFERRED,
                confidence = Confidence.of(0.68),
                evidence = listOf(direct, assignedNumberEvidence("Service ${definition.uuid}")),
            )
            categoryCandidates += CategoryCandidate(definition.category.toDeviceCategory(), 0.84)
        }

        gattServices.sortedBy { it.uuid.toString() }.forEach { service ->
            addGattServiceClaims(service, claims, categoryCandidates)
        }

        val manufacturer = assessManufacturer(snapshot, observationSource)
        val category = categoryCandidates.maxByOrNull(CategoryCandidate::score)?.category
            ?: DeviceCategory.UNKNOWN

        if (claims.isEmpty()) {
            claims += CapabilityClaim(
                title = "Capacités non déterminées",
                explanation = "Les informations observées ne correspondent pas encore à un identifiant standard du catalogue local.",
                status = ClaimStatus.UNKNOWN,
                confidence = Confidence.NONE,
            )
        }

        val confirmed = claims.count { it.status == ClaimStatus.CONFIRMED }
        val inferred = claims.count { it.status == ClaimStatus.INFERRED }
        val manufacturerText = manufacturer?.let { " Constructeur probable : ${it.name}." }.orEmpty()
        val summary = buildString {
            append(snapshot.displayName)
            append(" — ")
            append(category.frenchLabel())
            append(". ")
            append(confirmed)
            append(if (confirmed > 1) " faits techniques confirmés" else " fait technique confirmé")
            append(" et ")
            append(inferred)
            append(if (inferred > 1) " hypothèses explicites." else " hypothèse explicite.")
            append(manufacturerText)
        }

        val overallConfidence = when {
            gattServices.isNotEmpty() -> 0.90
            snapshot.serviceUuids.any { catalog.service(it.toString()) != null } -> 0.78
            category != DeviceCategory.UNKNOWN -> 0.68
            manufacturer != null -> 0.55
            else -> 0.20
        }

        return DeviceAnalysis(
            deviceId = snapshot.id,
            category = category,
            probableManufacturer = manufacturer,
            summary = summary,
            claims = claims.distinctBy { Triple(it.title, it.status, it.evidence.firstOrNull()?.value) },
            confidence = Confidence.of(overallConfidence),
            analyzedAt = Instant.now(clock),
            engineVersion = "offline-rules-1.0.0/catalog-${catalog.snapshotVersion}",
            isRemote = false,
        )
    }

    private fun addGattServiceClaims(
        service: GattServiceInfo,
        claims: MutableList<CapabilityClaim>,
        categoryCandidates: MutableList<CategoryCandidate>,
    ) {
        val definition = catalog.service(service.uuid.toString())
        val serviceName = definition?.name ?: "propriétaire"
        val evidence = mutableListOf(
            TechnicalEvidence(
                type = EvidenceType.SERVICE_UUID,
                value = "${service.uuid.toString().uppercase()} ($serviceName)",
                source = EvidenceSource.GATT_DISCOVERY,
            ),
        )
        definition?.let {
            evidence += assignedNumberEvidence("Service ${it.uuid}")
            categoryCandidates += CategoryCandidate(it.category.toDeviceCategory(), 0.94)
        }
        claims += CapabilityClaim(
            title = "Service GATT $serviceName découvert",
            explanation = "Le service est présent dans la base GATT accessible pendant l'exploration autorisée.",
            status = ClaimStatus.CONFIRMED,
            confidence = Confidence.of(if (definition == null) 0.90 else 0.98),
            evidence = evidence,
        )

        service.characteristics.sortedBy { it.uuid.toString() }.forEach { characteristic ->
            val characteristicDefinition = catalog.characteristic(characteristic.uuid.toString())
                ?: return@forEach
            val propertyLabel = characteristic.properties
                .joinToString(separator = ", ") { it.name }
                .ifBlank { "aucune propriété publiée" }
            val characteristicEvidence = mutableListOf(
                TechnicalEvidence(
                    type = EvidenceType.CHARACTERISTIC_UUID,
                    value = "${characteristic.uuid.toString().uppercase()} (${characteristicDefinition.name}); propriétés: $propertyLabel",
                    source = EvidenceSource.GATT_DISCOVERY,
                ),
                assignedNumberEvidence("Characteristic ${characteristicDefinition.uuid}"),
            )
            claims += CapabilityClaim(
                title = "Caractéristique ${characteristicDefinition.name} découverte",
                explanation = "${characteristicDefinition.meaning.replaceFirstChar(Char::uppercase)}. Les propriétés affichées décrivent les opérations publiées, pas leur autorisation effective.",
                status = ClaimStatus.CONFIRMED,
                confidence = Confidence.of(0.97),
                evidence = characteristicEvidence,
            )

            val readValue = characteristic.value
            if (characteristic.readStatus == GattReadStatus.SUCCESS && readValue != null) {
                claims += CapabilityClaim(
                    title = "Lecture autorisée : ${characteristicDefinition.name}",
                    explanation = "Une valeur de ${readValue.size} octets a été lue avec succès. Son contenu brut n'est pas repris dans l'analyse.",
                    status = ClaimStatus.CONFIRMED,
                    confidence = Confidence.of(0.99),
                    evidence = listOf(
                        TechnicalEvidence(
                            type = EvidenceType.GATT_VALUE,
                            value = "${readValue.size} octets lus avec succès",
                            source = EvidenceSource.GATT_READ,
                        ),
                    ),
                )
            }
        }
    }

    private fun assessManufacturer(
        snapshot: BluetoothDeviceSnapshot,
        observationSource: EvidenceSource,
    ): ManufacturerAssessment? {
        val registered = snapshot.manufacturerData.keys.sorted().mapNotNull(catalog::company)
        val company = registered.singleOrNull() ?: registered.firstOrNull() ?: return null
        val direct = TechnicalEvidence(
            type = EvidenceType.MANUFACTURER_ID,
            value = "0x${company.id.toString(16).uppercase().padStart(4, '0')}; champ de ${snapshot.manufacturerData[company.id]?.size ?: 0} octets",
            source = observationSource,
            observedAt = snapshot.lastSeenAt,
        )
        return ManufacturerAssessment(
            name = company.name,
            confidence = Confidence.of(if (registered.size == 1) 0.68 else 0.48),
            evidence = listOf(direct, assignedNumberEvidence("Company Identifier ${company.id}")),
        )
    }

    private fun factualClaim(
        title: String,
        explanation: String,
        evidence: List<TechnicalEvidence>,
        source: EvidenceSource,
        confidence: Double,
    ): CapabilityClaim = CapabilityClaim(
        title = title,
        explanation = explanation,
        status = if (source.isDirectObservation) ClaimStatus.CONFIRMED else ClaimStatus.INFERRED,
        confidence = Confidence.of(confidence),
        evidence = evidence,
    )

    private fun assignedNumberEvidence(value: String) = TechnicalEvidence(
        type = EvidenceType.LOCAL_CATALOG,
        value = "$value — ${catalog.sourceLabel}, snapshot ${catalog.snapshotVersion}",
        source = EvidenceSource.OFFICIAL_ASSIGNED_NUMBER,
    )

    private data class CategoryCandidate(val category: DeviceCategory, val score: Double)
}

private fun DiscoverySource.toEvidenceSource(): EvidenceSource = when (this) {
    DiscoverySource.BLE_ADVERTISEMENT -> EvidenceSource.BLE_ADVERTISEMENT
    DiscoverySource.CLASSIC_DISCOVERY -> EvidenceSource.CLASSIC_DISCOVERY
    DiscoverySource.PAIRED_CACHE -> EvidenceSource.BONDED_CACHE
    DiscoverySource.GATT -> EvidenceSource.GATT_DISCOVERY
    DiscoverySource.IMPORTED -> EvidenceSource.LOCAL_CATALOG
}

private fun String.toDeviceCategory(): DeviceCategory = when (this) {
    "Santé", "Sport" -> DeviceCategory.HEALTH
    "Interface", "Périphérique", "Télécommande" -> DeviceCategory.INPUT
    "Capteur", "Thermomètre" -> DeviceCategory.SENSOR
    "Montre", "Portable" -> DeviceCategory.WEARABLE
    "Audio/Vidéo" -> DeviceCategory.AUDIO
    "Ordinateur" -> DeviceCategory.COMPUTER
    "Téléphone" -> DeviceCategory.PHONE
    "Réseau" -> DeviceCategory.NETWORK
    else -> DeviceCategory.UNKNOWN
}

private fun DeviceCategory.frenchLabel(): String = when (this) {
    DeviceCategory.AUDIO -> "appareil audio ou vidéo"
    DeviceCategory.INPUT -> "périphérique d'entrée"
    DeviceCategory.WEARABLE -> "appareil porté"
    DeviceCategory.HEALTH -> "appareil de santé ou de sport"
    DeviceCategory.SENSOR -> "capteur"
    DeviceCategory.COMPUTER -> "ordinateur"
    DeviceCategory.PHONE -> "téléphone"
    DeviceCategory.NETWORK -> "équipement réseau"
    DeviceCategory.AUTOMOTIVE -> "équipement automobile"
    DeviceCategory.SMART_HOME -> "équipement domotique"
    DeviceCategory.BEACON -> "balise"
    DeviceCategory.OTHER -> "périphérique Bluetooth"
    DeviceCategory.UNKNOWN -> "catégorie non déterminée"
}
