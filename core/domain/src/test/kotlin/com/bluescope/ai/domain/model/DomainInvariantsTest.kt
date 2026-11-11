package com.bluescope.ai.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertThrows
import org.junit.Test

class DomainInvariantsTest {
    @Test
    fun `confidence accepts boundaries`() {
        assertThat(Confidence.of(0.0)).isEqualTo(Confidence.NONE)
        assertThat(Confidence.of(1.0)).isEqualTo(Confidence.CERTAIN)
    }

    @Test
    fun `confidence rejects non finite and out of range`() {
        listOf(-0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { Confidence.of(invalid) }
        }
    }

    @Test
    fun `scan config requires at least one transport`() {
        assertThrows(IllegalArgumentException::class.java) {
            ScanConfig(scanBle = false, scanClassic = false)
        }
    }

    @Test
    fun `scan config bounds radio intensive duration`() {
        assertThrows(IllegalArgumentException::class.java) {
            ScanConfig(duration = Duration.ofSeconds(61))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScanConfig(duration = Duration.ofSeconds(4))
        }
    }

    @Test
    fun `snapshot rejects inverted observation dates`() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        assertThrows(IllegalArgumentException::class.java) {
            BluetoothDeviceSnapshot(
                firstSeenAt = now,
                lastSeenAt = now.minusSeconds(1),
                source = DiscoverySource.BLE_ADVERTISEMENT,
            )
        }
    }

    @Test
    fun `address redaction preserves only last two groups`() {
        val address = ObservedAddress("AA:BB:CC:DD:EE:FF")
        assertThat(address.redacted()).isEqualTo("••:••:••:••:EE:FF")
    }
}
