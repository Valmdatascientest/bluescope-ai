package com.bluescope.ai.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluescope.ai.domain.model.DeviceQuery
import com.bluescope.ai.domain.model.KnownDevice
import com.bluescope.ai.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class LibraryFilter(val text: String = "", val favoritesOnly: Boolean = false)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    repository: DeviceRepository,
) : ViewModel() {
    val filter = MutableStateFlow(LibraryFilter())

    @OptIn(ExperimentalCoroutinesApi::class)
    val devices: StateFlow<List<KnownDevice>> = filter
        .flatMapLatest { current ->
            repository.observeDevices(
                DeviceQuery(text = current.text, favoritesOnly = current.favoritesOnly),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) = filter.update { it.copy(text = value.take(80)) }
    fun toggleFavorites() = filter.update { it.copy(favoritesOnly = !it.favoritesOnly) }
}
