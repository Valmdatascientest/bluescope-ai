package com.bluescope.ai.presentation.device

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluescope.ai.domain.model.DeviceId
import com.bluescope.ai.domain.model.KnownDevice
import com.bluescope.ai.domain.repository.DeviceRepository
import com.bluescope.ai.domain.repository.IdentificationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DeviceRepository,
    private val identificationEngine: IdentificationEngine,
) : ViewModel() {
    private val deviceId = DeviceId(checkNotNull(savedStateHandle["deviceId"]))
    val device: StateFlow<KnownDevice?> = repository.observeDevice(deviceId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    fun toggleFavorite() = viewModelScope.launch {
        device.value?.let { repository.setFavorite(deviceId, !it.isFavorite) }
    }

    fun setNote(note: String) = viewModelScope.launch { repository.setNote(deviceId, note) }

    fun refreshAnalysis() = viewModelScope.launch {
        device.value?.let { known ->
            val analysis = identificationEngine.analyze(
                known.snapshot,
                known.latestGatt?.services.orEmpty(),
            )
            repository.saveAnalysis(analysis)
        }
    }

    fun delete() = viewModelScope.launch { repository.deleteDevice(deviceId) }
}
