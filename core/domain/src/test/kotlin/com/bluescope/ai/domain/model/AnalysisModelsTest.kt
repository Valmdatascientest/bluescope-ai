package com.bluescope.ai.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Assert.assertThrows
import org.junit.Test

class AnalysisModelsTest {
    private val directEvidence = TechnicalEvidence(
        type = EvidenceType.CHARACTERISTIC_UUID,
        value = "00002a19-0000-1000-8000-00805f9b34fb",
        source = EvidenceSource.GATT_DISCOVERY,
        observedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun `confirmed claim must have direct evidence`() {
        val catalogOnly = TechnicalEvidence(
            type = EvidenceType.LOCAL_CATALOG,
            value = "Battery Level mapping",
            source = EvidenceSource.OFFICIAL_ASSIGNED_NUMBER,
        )
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityClaim(
                title = "Niveau de batterie",
                explanation = "Le catalogue associe un UUID à la batterie.",
                status = ClaimStatus.CONFIRMED,
                confidence = Confidence.of(0.8),
                evidence = listOf(catalogOnly),
            )
        }
    }

    @Test
    fun `inferred claim must cite evidence`() {
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityClaim(
                title = "Audio",
                explanation = "Fonction possible.",
                status = ClaimStatus.INFERRED,
                confidence = Confidence.of(0.5),
            )
        }
    }

    @Test
    fun `analysis separates confirmed and inferred claims`() {
        val confirmed = CapabilityClaim(
            title = "Caractéristique batterie découverte",
            explanation = "La caractéristique est présente dans GATT.",
            status = ClaimStatus.CONFIRMED,
            confidence = Confidence.of(0.99),
            evidence = listOf(directEvidence),
        )
        val inferred = CapabilityClaim(
            title = "Affichage de l’autonomie",
            explanation = "L’appareil peut probablement exposer son autonomie.",
            status = ClaimStatus.INFERRED,
            confidence = Confidence.of(0.7),
            evidence = listOf(directEvidence),
        )
        val analysis = DeviceAnalysis(
            deviceId = DeviceId("device-1"),
            category = DeviceCategory.SENSOR,
            summary = "Périphérique avec information de batterie.",
            claims = listOf(confirmed, inferred),
            confidence = Confidence.of(0.8),
            analyzedAt = Instant.parse("2026-01-01T00:00:00Z"),
            engineVersion = "test",
        )
        assertThat(analysis.confirmedClaims).containsExactly(confirmed)
        assertThat(analysis.inferredClaims).containsExactly(inferred)
    }

    @Test
    fun `technical evidence is bounded`() {
        assertThrows(IllegalArgumentException::class.java) {
            TechnicalEvidence(
                type = EvidenceType.MANUFACTURER_DATA,
                value = "A".repeat(TechnicalEvidence.MAX_VALUE_LENGTH + 1),
                source = EvidenceSource.BLE_ADVERTISEMENT,
            )
        }
    }
}
