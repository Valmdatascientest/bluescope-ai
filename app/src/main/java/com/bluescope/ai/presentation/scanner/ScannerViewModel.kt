package com.bluescope.ai.presentation.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluescope.ai.domain.model.BluetoothDeviceSnapshot
import com.bluescope.ai.domain.model.BluetoothError
import com.bluescope.ai.domain.model.ScanConfig
import com.bluescope.ai.domain.model.ScanEvent
import com.bluescope.ai.domain.model.ScanPhase
import com.bluescope.ai.domain.model.ScanSession
import com.bluescope.ai.domain.repository.BluetoothScanner
import com.bluescope.ai.domain.repository.DeviceRepository
import com.bluescope.ai.domain.repository.IdentificationEngine
import com.bluescope.ai.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScannerUiState(
    val isScanning: Boolean = false,
    val phase: ScanPhase? = null,
    val devices: List<BluetoothDeviceSnapshot> = emptyList(),
    val query: String = "",
    val error: String? = null,
) {
    val filteredDevices: List<BluetoothDeviceSnapshot>
        get() = devices
            .filter { query.isBlank() || it.displayName.contains(query, ignoreCase = true) }
            .sortedByDescending { it.rssi ?: Int.MIN_VALUE }
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val scanner: BluetoothScanner,
    private val repository: DeviceRepository,
    private val identificationEngine: IdentificationEngine,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()
    private var scanJob: Job? = null
    private var currentSession: ScanSession? = null

    fun startScan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            val preferences = preferencesRepository.preferences.first()
            val config = ScanConfig(
                duration = preferences.scanDuration,
                scanBle = true,
                scanClassic = preferences.includeClassic,
                includeBonded = preferences.includeBonded,
            )
            _uiState.update { it.copy(isScanning = true, phase = null, devices = emptyList(), error = null) }
            try {
                scanner.scan(config).collect { event ->
                    when (event) {
                        is ScanEvent.Started -> {
                            currentSession = event.session
                            repository.startSession(event.session)
                        }
                        is ScanEvent.PhaseChanged -> _uiState.update { it.copy(phase = event.phase) }
                        is ScanEvent.DeviceFound -> persistAndDisplay(event.sessionId, event.device, analyze = true)
                        is ScanEvent.DeviceUpdated -> persistAndDisplay(event.sessionId, event.device, analyze = false)
                        is ScanEvent.Finished -> {
                            repository.finishSession(event.session)
                            currentSession = null
                            _uiState.update { it.copy(isScanning = false, phase = null) }
                        }
                        is ScanEvent.Failed -> {
                            currentSession?.let { session ->
                                runCatching { repository.finishSession(session.copy(endedAt = Instant.now())) }
                            }
                            currentSession = null
                            _uiState.update {
                                it.copy(isScanning = false, phase = null, error = event.error.toFrenchMessage())
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                currentSession?.let { session ->
                    runCatching { repository.finishSession(session.copy(endedAt = Instant.now())) }
                }
                currentSession = null
                _uiState.update {
                    it.copy(isScanning = false, phase = null, error = "Le scan a été interrompu : ${error.message.orEmpty()}")
                }
            }
        }.also { job ->
            job.invokeOnCompletion { _uiState.update { it.copy(isScanning = false, phase = null) } }
        }
    }

    fun stopScan() {
        viewModelScope.launch { scanner.stop() }
    }

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value.take(80)) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun persistAndDisplay(
        sessionId: com.bluescope.ai.domain.model.ScanSessionId,
        snapshot: BluetoothDeviceSnapshot,
        analyze: Boolean,
    ) {
        runCatching {
            val actualId = repository.recordObservation(sessionId, snapshot)
            val persisted = snapshot.copy(id = actualId)
            _uiState.update { current ->
                val updated = current.devices.associateBy { it.id.value }.toMutableMap()
                updated[actualId.value] = persisted
                current.copy(devices = updated.values.toList())
            }
            if (analyze) repository.saveAnalysis(identificationEngine.analyze(persisted))
        }.onFailure { error ->
            _uiState.update { it.copy(error = "Observation non enregistrée : ${error.message.orEmpty()}") }
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
    }
}

private fun BluetoothError.toFrenchMessage(): String = when (this) {
    BluetoothError.PermissionDenied -> "Autorisation Bluetooth requise."
    BluetoothError.BluetoothDisabled -> "Le Bluetooth est désactivé."
    BluetoothError.Unsupported -> "Bluetooth n’est pas disponible sur cet appareil."
    BluetoothError.Throttled -> "Android limite temporairement les scans. Réessayez dans un instant."
    BluetoothError.AlreadyScanning -> "Un scan est déjà en cours."
    BluetoothError.OutOfResources -> "Ressources Bluetooth indisponibles."
    is BluetoothError.Unexpected -> "Erreur Bluetooth${detail?.let { " : $it" }.orEmpty()}"
}
