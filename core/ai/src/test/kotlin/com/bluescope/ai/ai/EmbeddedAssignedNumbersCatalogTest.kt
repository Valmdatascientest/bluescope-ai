package com.bluescope.ai.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EmbeddedAssignedNumbersCatalogTest {
    private val catalog = EmbeddedAssignedNumbersCatalog()

    @Test
    fun `normalizes 16 bit and standard 128 bit uuids to the same key`() {
        assertThat(normalizeBluetoothUuid("180F"))
            .isEqualTo(normalizeBluetoothUuid("0000180f-0000-1000-8000-00805f9b34fb"))
    }

    @Test
    fun `resolves a standardized service without guessing an unknown one`() {
        assertThat(catalog.service("180D")?.name).isEqualTo("Heart Rate")
        assertThat(catalog.service("f000aa00-0451-4000-b000-000000000000")).isNull()
    }

    @Test
    fun `resolves company id as registry owner`() {
        assertThat(catalog.company(0x004C)?.name).isEqualTo("Apple")
        assertThat(catalog.company(0xFFFF)).isNull()
    }

    @Test
    fun `keeps catalogue provenance`() {
        assertThat(catalog.sourceLabel).isEqualTo("Bluetooth SIG Assigned Numbers")
        assertThat(catalog.snapshotVersion).isNotEmpty()
    }
}
