package com.bluescope.ai.domain.repository

import com.bluescope.ai.domain.model.BluetoothDeviceSnapshot
import com.bluescope.ai.domain.model.ExplorationConsent
import com.bluescope.ai.domain.model.GattEvent
import com.bluescope.ai.domain.model.ScanConfig
import com.bluescope.ai.domain.model.ScanEvent
import kotlinx.coroutines.flow.Flow

interface BluetoothScanner {
    fun scan(config: ScanConfig): Flow<ScanEvent>
    suspend fun stop()
}

interface GattExplorer {
    fun explore(
        device: BluetoothDeviceSnapshot,
        consent: ExplorationConsent,
    ): Flow<GattEvent>

    suspend fun cancel()
}
