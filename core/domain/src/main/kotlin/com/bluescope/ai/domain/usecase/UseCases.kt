package com.bluescope.ai.domain.usecase

import com.bluescope.ai.domain.model.BluetoothDeviceSnapshot
import com.bluescope.ai.domain.model.DeviceAnalysis
import com.bluescope.ai.domain.model.DeviceId
import com.bluescope.ai.domain.model.DeviceQuery
import com.bluescope.ai.domain.model.GattServiceInfo
import com.bluescope.ai.domain.model.KnownDevice
import com.bluescope.ai.domain.model.ScanConfig
import com.bluescope.ai.domain.model.ScanEvent
import com.bluescope.ai.domain.repository.BluetoothScanner
import com.bluescope.ai.domain.repository.DeviceRepository
import com.bluescope.ai.domain.repository.IdentificationEngine
import kotlinx.coroutines.flow.Flow

class StartScanUseCase(private val scanner: BluetoothScanner) {
    operator fun invoke(config: ScanConfig): Flow<ScanEvent> = scanner.scan(config)
    suspend fun stop() = scanner.stop()
}

class ObserveKnownDevicesUseCase(private val repository: DeviceRepository) {
    operator fun invoke(query: DeviceQuery = DeviceQuery()): Flow<List<KnownDevice>> =
        repository.observeDevices(query)
}

class ObserveDeviceUseCase(private val repository: DeviceRepository) {
    operator fun invoke(id: DeviceId): Flow<KnownDevice?> = repository.observeDevice(id)
}

class AnalyzeDeviceUseCase(
    private val engine: IdentificationEngine,
    private val repository: DeviceRepository,
) {
    suspend operator fun invoke(
        snapshot: BluetoothDeviceSnapshot,
        gattServices: List<GattServiceInfo> = emptyList(),
    ): DeviceAnalysis {
        val analysis = engine.analyze(snapshot, gattServices)
        repository.saveAnalysis(analysis)
        return analysis
    }
}
