package com.bluescope.ai.bluetooth.scanner

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdvertisingDataParserTest {
    @Test
    fun `reads little endian appearance`() {
        val record = byteArrayOf(
            2, 0x01, 0x06,
            3, 0x19, 0x41, 0x03,
            0,
        )

        assertThat(AdvertisingDataParser.appearance(record)).isEqualTo(0x0341)
    }

    @Test
    fun `truncated field is rejected without throwing`() {
        assertThat(AdvertisingDataParser.appearance(byteArrayOf(5, 0x19, 1))).isNull()
    }

    @Test
    fun `oversized input is rejected`() {
        assertThat(AdvertisingDataParser.appearance(ByteArray(1_651))).isNull()
    }
}
