package com.bluescope.ai.bluetooth.gatt

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.bluescope.ai.domain.model.GattError
import com.bluescope.ai.domain.model.GattProperty
import com.bluescope.ai.domain.model.GattReadStatus
import com.bluescope.ai.domain.model.GattServiceType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GattMappersTest {
    @Test
    fun `maps combined characteristic properties`() {
        val flags = BluetoothGattCharacteristic.PROPERTY_READ or
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE

        assertThat(GattMappers.properties(flags)).containsExactly(
            GattProperty.READ,
            GattProperty.NOTIFY,
            GattProperty.WRITE_WITHOUT_RESPONSE,
        )
    }

    @Test
    fun `does not confuse notify support with a confirmed subscription`() {
        assertThat(GattMappers.properties(BluetoothGattCharacteristic.PROPERTY_NOTIFY))
            .containsExactly(GattProperty.NOTIFY)
    }

    @Test
    fun `maps service type`() {
        assertThat(GattMappers.serviceType(BluetoothGattService.SERVICE_TYPE_PRIMARY))
            .isEqualTo(GattServiceType.PRIMARY)
        assertThat(GattMappers.serviceType(99)).isEqualTo(GattServiceType.UNKNOWN)
    }

    @Test
    fun `maps permission and authentication failures separately`() {
        assertThat(GattMappers.readStatus(BluetoothGatt.GATT_READ_NOT_PERMITTED))
            .isEqualTo(GattReadStatus.NOT_PERMITTED)
        assertThat(GattMappers.error(BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION))
            .isEqualTo(GattError.AuthenticationRequired)
    }
}
