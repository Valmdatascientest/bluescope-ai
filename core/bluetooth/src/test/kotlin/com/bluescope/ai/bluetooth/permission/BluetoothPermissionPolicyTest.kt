package com.bluescope.ai.bluetooth.permission

import android.Manifest
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BluetoothPermissionPolicyTest {
    @Test
    fun `api 30 scan requires fine location`() {
        assertThat(BluetoothPermissionPolicy.scanPermissions(30))
            .containsExactly(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Test
    fun `api 31 scan requires nearby scan and connect`() {
        assertThat(BluetoothPermissionPolicy.scanPermissions(31)).containsExactly(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    }

    @Test
    fun `legacy connect has no runtime permission`() {
        assertThat(BluetoothPermissionPolicy.connectPermissions(30)).isEmpty()
    }
}
