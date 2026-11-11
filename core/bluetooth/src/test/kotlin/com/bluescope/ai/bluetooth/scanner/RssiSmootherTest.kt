package com.bluescope.ai.bluetooth.scanner

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RssiSmootherTest {
    @Test
    fun `smooths noisy readings without treating rssi as distance`() {
        val key = ObservationSessionKey(RawTransport.BLE, "AA", 1)
        val smoother = RssiSmoother(alpha = 0.5)

        assertThat(smoother.add(key, -80)).isEqualTo(-80)
        assertThat(smoother.add(key, -40)).isEqualTo(-60)
    }
}
