package com.bluescope.ai.bluetooth.scanner

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionObservationAccumulatorTest {
    private fun observation(
        address: String,
        transport: RawTransport = RawTransport.BLE,
        sid: Int? = 1,
        rssi: Int = -70,
        at: Long = 1_000,
    ) = RawBluetoothObservation(
        address = address,
        transport = transport,
        advertisingSid = sid,
        rssi = rssi,
        firstSeenAtMillis = at,
        lastSeenAtMillis = at,
    )

    @Test
    fun `same transport address and sid updates one observation`() {
        val accumulator = SessionObservationAccumulator()
        assertThat(accumulator.merge(observation("aa:bb", rssi = -80))).isInstanceOf(MergeResult.Added::class.java)
        assertThat(accumulator.merge(observation("AA:BB", rssi = -50, at = 2_000)))
            .isInstanceOf(MergeResult.Updated::class.java)

        assertThat(accumulator.snapshot()).hasSize(1)
        assertThat(accumulator.snapshot().single().rssi).isEqualTo(-50)
        assertThat(accumulator.snapshot().single().firstSeenAtMillis).isEqualTo(1_000)
        assertThat(accumulator.snapshot().single().lastSeenAtMillis).isEqualTo(2_000)
    }

    @Test
    fun `rotated address is never silently merged`() {
        val accumulator = SessionObservationAccumulator()
        accumulator.merge(observation("AA:00"))
        accumulator.merge(observation("AA:01"))

        assertThat(accumulator.snapshot()).hasSize(2)
    }

    @Test
    fun `classic and ble identities remain distinct`() {
        val accumulator = SessionObservationAccumulator()
        accumulator.merge(observation("AA:00", RawTransport.BLE))
        accumulator.merge(observation("AA:00", RawTransport.CLASSIC, sid = null))

        assertThat(accumulator.snapshot()).hasSize(2)
    }

    @Test
    fun `different advertising sid remains distinct`() {
        val accumulator = SessionObservationAccumulator()
        accumulator.merge(observation("AA:00", sid = 1))
        accumulator.merge(observation("AA:00", sid = 2))

        assertThat(accumulator.snapshot()).hasSize(2)
    }
}
