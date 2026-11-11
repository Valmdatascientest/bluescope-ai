package com.bluescope.ai.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluescope.ai.domain.model.DeviceQuery
import com.bluescope.ai.domain.model.KnownDevice
import com.bluescope.ai.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: DeviceRepository,
) : ViewModel() {
    val devices: StateFlow<List<KnownDevice>> = repository.observeDevices(DeviceQuery()).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
}
