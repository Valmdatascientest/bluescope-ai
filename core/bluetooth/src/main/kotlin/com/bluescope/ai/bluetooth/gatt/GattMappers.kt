package com.bluescope.ai.bluetooth.gatt

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.bluescope.ai.domain.model.GattError
import com.bluescope.ai.domain.model.GattProperty
import com.bluescope.ai.domain.model.GattReadStatus
import com.bluescope.ai.domain.model.GattServiceType

internal object GattMappers {
    fun properties(value: Int): Set<GattProperty> = buildSet {
        if (value has BluetoothGattCharacteristic.PROPERTY_BROADCAST) add(GattProperty.BROADCAST)
        if (value has BluetoothGattCharacteristic.PROPERTY_READ) add(GattProperty.READ)
        if (value has BluetoothGattCharacteristic.PROPERTY_WRITE) add(GattProperty.WRITE)
        if (value has BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) add(GattProperty.WRITE_WITHOUT_RESPONSE)
        if (value has BluetoothGattCharacteristic.PROPERTY_NOTIFY) add(GattProperty.NOTIFY)
        if (value has BluetoothGattCharacteristic.PROPERTY_INDICATE) add(GattProperty.INDICATE)
        if (value has BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) add(GattProperty.SIGNED_WRITE)
        if (value has BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS) add(GattProperty.EXTENDED_PROPERTIES)
    }

    fun serviceType(type: Int): GattServiceType = when (type) {
        BluetoothGattService.SERVICE_TYPE_PRIMARY -> GattServiceType.PRIMARY
        BluetoothGattService.SERVICE_TYPE_SECONDARY -> GattServiceType.SECONDARY
        else -> GattServiceType.UNKNOWN
    }

    fun readStatus(status: Int): GattReadStatus = when (status) {
        BluetoothGatt.GATT_SUCCESS -> GattReadStatus.SUCCESS
        BluetoothGatt.GATT_READ_NOT_PERMITTED -> GattReadStatus.NOT_PERMITTED
        BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION,
        BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION,
        -> GattReadStatus.AUTHENTICATION_REQUIRED
        else -> GattReadStatus.FAILED
    }

    fun error(status: Int): GattError = when (status) {
        BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION,
        BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION,
        -> GattError.AuthenticationRequired
        BluetoothGatt.GATT_READ_NOT_PERMITTED -> GattError.ReadNotPermitted
        else -> GattError.PlatformFailure(status)
    }

    private infix fun Int.has(flag: Int): Boolean = this and flag != 0
}
