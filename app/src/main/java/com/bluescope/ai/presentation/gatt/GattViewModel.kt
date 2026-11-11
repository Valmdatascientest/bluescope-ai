package com.bluescope.ai.presentation.gatt

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluescope.ai.domain.model.DeviceId
import com.bluescope.ai.domain.model.ExplorationConsent
import com.bluescope.ai.domain.model.GattError
import com.bluescope.ai.domain.model.GattEvent
import com.bluescope.ai.domain.model.GattServiceInfo
import com.bluescope.ai.domain.repository.DeviceRepository
import com.bluescope.ai.domain.repository.GattExplorer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GattUiState(
    val needsConsent: Boolean = true,
    val isExploring: Boolean = false,
    val status: String = "En attente de votre confirmation",
    val services: List<GattServiceInfo> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class GattViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DeviceRepository,
    private val explorer: GattExplorer,
) : ViewModel() {
    private val deviceId = DeviceId(checkNotNull(savedStateHandle["deviceId"]))
    private val _uiState = MutableStateFlow(GattUiState())
    val uiState: StateFlow<GattUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    fun explore(readValues: Boolean) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            val known = repository.observeDevice(deviceId).first() ?: run {
                _uiState.update { it.copy(error = "Périphérique introuvable") }
                return@launch
            }
            _uiState.value = GattUiState(
                needsConsent = false,
                isExploring = true,
                status = "Connexion…",
            )
            explorer.explore(known.snapshot, ExplorationConsent.granted(readValues)).collect { event ->
                when (event) {
                    is GattEvent.Connecting -> status("Connexion au périphérique…")
                    is GattEvent.Connected -> status("Connecté")
                    is GattEvent.DiscoveringServices -> status("Découverte des services…")
                    is GattEvent.ServiceDiscovered -> _uiState.update {
                        it.copy(services = (it.services + event.service).distinctBy { service -> service.uuid })
                    }
                    is GattEvent.ReadingCharacteristic -> status("Lecture des valeurs autorisées…")
                    is GattEvent.Completed -> {
                        repository.saveGatt(event.snapshot)
                        _uiState.update {
                            it.copy(
                                isExploring = false,
                                status = "Exploration terminée",
                                services = event.snapshot.services,
                            )
                        }
                    }
                    is GattEvent.Disconnected -> _uiState.update {
                        it.copy(isExploring = false, status = "Déconnecté")
                    }
                    is GattEvent.Failed -> _uiState.update {
                        it.copy(isExploring = false, error = event.error.toFrenchMessage())
                    }
                }
            }
        }
    }

    fun cancel() = viewModelScope.launch { explorer.cancel() }
    private fun status(value: String) = _uiState.update { it.copy(status = value) }
}

private fun GattError.toFrenchMessage(): String = when (this) {
    GattError.PermissionDenied -> "Autorisation de connexion Bluetooth manquante."
    GattError.BluetoothDisabled -> "Le Bluetooth est désactivé."
    GattError.UnsupportedTransport -> "Ce périphérique n’expose pas de transport BLE explorable."
    GattError.Timeout -> "Le périphérique n’a pas répondu à temps."
    GattError.AuthenticationRequired -> "Appairage ou chiffrement requis par le périphérique."
    GattError.ReadNotPermitted -> "Lecture refusée par le périphérique."
    GattError.Disconnected -> "Le périphérique s’est déconnecté."
    is GattError.PlatformFailure -> "Échec GATT (code $status)."
}
