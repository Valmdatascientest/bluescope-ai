package com.bluescope.ai.domain.model

import java.time.Instant

enum class DeviceCategory {
    AUDIO,
    INPUT,
    WEARABLE,
    HEALTH,
    SENSOR,
    COMPUTER,
    PHONE,
    NETWORK,
    AUTOMOTIVE,
    SMART_HOME,
    BEACON,
    OTHER,
    UNKNOWN,
}

enum class ClaimStatus { CONFIRMED, INFERRED, UNKNOWN }

enum class EvidenceType {
    SERVICE_UUID,
    CHARACTERISTIC_UUID,
    MANUFACTURER_ID,
    MANUFACTURER_DATA,
    SERVICE_DATA,
    APPEARANCE,
    DEVICE_CLASS,
    DEVICE_NAME,
    TRANSPORT,
    GATT_VALUE,
    LOCAL_CATALOG,
}

enum class EvidenceSource(val isDirectObservation: Boolean) {
    BLE_ADVERTISEMENT(true),
    CLASSIC_DISCOVERY(true),
    GATT_DISCOVERY(true),
    GATT_READ(true),
    BONDED_CACHE(true),
    OFFICIAL_ASSIGNED_NUMBER(false),
    LOCAL_CATALOG(false),
    AI_INFERENCE(false),
    USER(false),
}

data class TechnicalEvidence(
    val type: EvidenceType,
    /** Human-readable, bounded technical fact. Never a secret or an unbounded payload. */
    val value: String,
    val source: EvidenceSource,
    val observedAt: Instant? = null,
) {
    init {
        require(value.isNotBlank()) { "Une preuve technique doit contenir une valeur" }
        require(value.length <= MAX_VALUE_LENGTH) { "Une preuve technique est trop longue" }
    }

    companion object {
        const val MAX_VALUE_LENGTH = 512
    }
}

data class CapabilityClaim(
    val title: String,
    val explanation: String,
    val status: ClaimStatus,
    val confidence: Confidence,
    val evidence: List<TechnicalEvidence> = emptyList(),
) {
    init {
        require(title.isNotBlank()) { "Le titre d’une capacité ne peut pas être vide" }
        require(explanation.isNotBlank()) { "L’explication d’une capacité ne peut pas être vide" }
        require(status == ClaimStatus.UNKNOWN || evidence.isNotEmpty()) {
            "Une capacité confirmée ou supposée doit citer au moins une preuve"
        }
        require(status != ClaimStatus.CONFIRMED || evidence.any { it.source.isDirectObservation }) {
            "Une capacité confirmée exige une observation directe"
        }
    }
}

data class ManufacturerAssessment(
    val name: String,
    val confidence: Confidence,
    val evidence: List<TechnicalEvidence>,
) {
    init {
        require(name.isNotBlank()) { "Le constructeur probable ne peut pas être vide" }
        require(evidence.isNotEmpty()) { "Le constructeur probable doit être justifié" }
    }
}

data class DeviceAnalysis(
    val id: AnalysisId = AnalysisId.random(),
    val deviceId: DeviceId,
    val category: DeviceCategory,
    val probableManufacturer: ManufacturerAssessment? = null,
    val summary: String,
    val claims: List<CapabilityClaim>,
    val confidence: Confidence,
    val analyzedAt: Instant,
    val engineVersion: String,
    val isRemote: Boolean = false,
) {
    init {
        require(summary.isNotBlank()) { "Le résumé d’analyse ne peut pas être vide" }
        require(engineVersion.isNotBlank()) { "La version du moteur ne peut pas être vide" }
    }

    val confirmedClaims: List<CapabilityClaim>
        get() = claims.filter { it.status == ClaimStatus.CONFIRMED }

    val inferredClaims: List<CapabilityClaim>
        get() = claims.filter { it.status == ClaimStatus.INFERRED }
}
