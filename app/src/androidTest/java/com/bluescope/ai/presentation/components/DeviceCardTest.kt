package com.bluescope.ai.presentation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.bluescope.ai.designsystem.theme.BlueScopeTheme
import com.bluescope.ai.domain.model.BluetoothDeviceSnapshot
import com.bluescope.ai.domain.model.BluetoothTransport
import com.bluescope.ai.domain.model.DiscoverySource
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class DeviceCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cardShowsIdentityTransportAndAccessibleSignal() {
        val now = Instant.parse("2026-11-11T10:00:00Z")
        val snapshot = BluetoothDeviceSnapshot(
            name = "Thermomètre salon",
            transport = BluetoothTransport.BLE,
            rssi = -61,
            firstSeenAt = now,
            lastSeenAt = now,
            source = DiscoverySource.BLE_ADVERTISEMENT,
        )

        composeRule.setContent {
            BlueScopeTheme {
                DeviceCard(snapshot = snapshot, onClick = {})
            }
        }

        composeRule.onNodeWithText("Thermomètre salon").assertIsDisplayed()
        composeRule.onNodeWithText("BLE").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Signal -61 dBm, niveau 3 sur 4").assertIsDisplayed()
    }
}
