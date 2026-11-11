package com.bluescope.ai.domain.repository

import com.bluescope.ai.domain.model.BluetoothDeviceSnapshot
import com.bluescope.ai.domain.model.DeviceAnalysis
import com.bluescope.ai.domain.model.DeviceId
import com.bluescope.ai.domain.model.DeviceQuery
import com.bluescope.ai.domain.model.ExportPayload
import com.bluescope.ai.domain.model.ExportRequest
import com.bluescope.ai.domain.model.GattSnapshot
import com.bluescope.ai.domain.model.ImportConflictPolicy
import com.bluescope.ai.domain.model.ImportReport
import com.bluescope.ai.domain.model.KnownDevice
import com.bluescope.ai.domain.model.ScanSession
import com.bluescope.ai.domain.model.ScanSessionId
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    fun observeDevices(query: DeviceQuery = DeviceQuery()): Flow<List<KnownDevice>>
    fun observeDevice(id: DeviceId): Flow<KnownDevice?>

    suspend fun startSession(session: ScanSession)
    suspend fun finishSession(session: ScanSession)
    suspend fun recordObservation(
        sessionId: ScanSessionId,
        snapshot: BluetoothDeviceSnapshot,
    ): DeviceId

    suspend fun saveGatt(snapshot: GattSnapshot)
    suspend fun saveAnalysis(analysis: DeviceAnalysis)
    suspend fun setFavorite(id: DeviceId, favorite: Boolean)
    suspend fun setNote(id: DeviceId, note: String?)
    suspend fun deleteDevice(id: DeviceId)
    suspend fun deleteAll()

    suspend fun export(request: ExportRequest): ExportPayload
    suspend fun import(payload: String, conflictPolicy: ImportConflictPolicy): ImportReport
}
