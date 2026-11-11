package com.bluescope.ai.domain.repository

import com.bluescope.ai.domain.model.AiPrivacyConsent
import com.bluescope.ai.domain.model.BluetoothDeviceSnapshot
import com.bluescope.ai.domain.model.DeviceAnalysis
import com.bluescope.ai.domain.model.GattServiceInfo

interface IdentificationEngine {
    suspend fun analyze(
        snapshot: BluetoothDeviceSnapshot,
        gattServices: List<GattServiceInfo> = emptyList(),
    ): DeviceAnalysis
}

/** Optional extension point. It must never be called without the explicit consent parameter. */
interface RemoteInferenceProvider {
    suspend fun analyze(
        snapshot: BluetoothDeviceSnapshot,
        gattServices: List<GattServiceInfo>,
        consent: AiPrivacyConsent,
    ): DeviceAnalysis
}
