package com.bluescope.ai.ai

import com.bluescope.ai.domain.model.BinaryData
import com.bluescope.ai.domain.model.BluetoothDeviceSnapshot
import com.bluescope.ai.domain.model.BluetoothTransport
import com.bluescope.ai.domain.model.ClaimStatus
import com.bluescope.ai.domain.model.DeviceCategory
import com.bluescope.ai.domain.model.DiscoverySource
import com.bluescope.ai.domain.model.GattCharacteristicInfo
import com.bluescope.ai.domain.model.GattProperty
import com.bluescope.ai.domain.model.GattReadStatus
import com.bluescope.ai.domain.model.GattServiceInfo
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Test

class OfflineIdentificationEngineTest {
    private val now = Instant.parse("2026-11-11T09:30:00Z")
    private val engine = OfflineIdentificationEngine(
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `advertised service confirms the protocol fact but keeps possible use inferred`() = runBlocking {
        val analysis = engine.analyze(
            snapshot(serviceUuids = setOf(uuid16("180D"))),
        )

        assertThat(analysis.category).isEqualTo(DeviceCategory.HEALTH)
        assertThat(analysis.claims.single { it.title.contains("Heart Rate annoncé") }.status)
            .isEqualTo(ClaimStatus.CONFIRMED)
        assertThat(analysis.claims.single { it.title.startsWith("Usage possible") }.status)
            .isEqualTo(ClaimStatus.INFERRED)
        assertThat(analysis.isRemote).isFalse()
        assertThat(analysis.analyzedAt).isEqualTo(now)
    }

    @Test
    fun `imported advertisement cannot become a confirmed live fact`() = runBlocking {
        val analysis = engine.analyze(
            snapshot(
                serviceUuids = setOf(uuid16("180F")),
                source = DiscoverySource.IMPORTED,
            ),
        )

        assertThat(analysis.claims.single { it.title.contains("Battery annoncé") }.status)
            .isEqualTo(ClaimStatus.INFERRED)
    }

    @Test
    fun `company id produces only a probable manufacturer assessment`() = runBlocking {
        val analysis = engine.analyze(
            snapshot(manufacturerData = mapOf(0x004C to BinaryData.of(1, 2, 3))),
        )

        assertThat(analysis.probableManufacturer?.name).isEqualTo("Apple")
        assertThat(analysis.probableManufacturer?.confidence?.value).isLessThan(1.0)
        assertThat(analysis.summary).contains("Constructeur probable")
        assertThat(analysis.claims.none { it.title.contains("constructeur confirmé", ignoreCase = true) })
            .isTrue()
    }

    @Test
    fun `GATT discovery and successful bounded read are confirmed separately`() = runBlocking {
        val batteryCharacteristic = GattCharacteristicInfo(
            uuid = uuid16("2A19"),
            properties = setOf(GattProperty.READ, GattProperty.NOTIFY),
            value = BinaryData.of(87),
            readStatus = GattReadStatus.SUCCESS,
        )

        val analysis = engine.analyze(
            snapshot(),
            gattServices = listOf(
                GattServiceInfo(
                    uuid = uuid16("180F"),
                    characteristics = listOf(batteryCharacteristic),
                ),
            ),
        )

        assertThat(analysis.claims.single { it.title == "Service GATT Battery découvert" }.status)
            .isEqualTo(ClaimStatus.CONFIRMED)
        assertThat(analysis.claims.single { it.title.startsWith("Lecture autorisée") }.status)
            .isEqualTo(ClaimStatus.CONFIRMED)
        assertThat(analysis.claims.single { it.title.startsWith("Lecture autorisée") }.evidence.single().value)
            .doesNotContain("57")
    }

    @Test
    fun `unknown signals stay unknown instead of being guessed`() = runBlocking {
        val analysis = engine.analyze(
            snapshot(
                name = "Mystery gadget",
                serviceUuids = setOf(UUID.fromString("f000aa00-0451-4000-b000-000000000000")),
            ),
        )

        assertThat(analysis.category).isEqualTo(DeviceCategory.UNKNOWN)
        assertThat(analysis.claims).hasSize(1)
        assertThat(analysis.claims.single().status).isEqualTo(ClaimStatus.UNKNOWN)
        assertThat(analysis.confidence.value).isEqualTo(0.20)
    }

    private fun snapshot(
        name: String? = "Capteur test",
        serviceUuids: Set<UUID> = emptySet(),
        manufacturerData: Map<Int, BinaryData> = emptyMap(),
        source: DiscoverySource = DiscoverySource.BLE_ADVERTISEMENT,
    ) = BluetoothDeviceSnapshot(
        name = name,
        transport = BluetoothTransport.BLE,
        serviceUuids = serviceUuids,
        manufacturerData = manufacturerData,
        firstSeenAt = now.minusSeconds(10),
        lastSeenAt = now,
        source = source,
    )

    private fun uuid16(short: String): UUID =
        UUID.fromString("0000${short.lowercase()}-0000-1000-8000-00805f9b34fb")
}
