package com.bluescope.ai.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluescope.ai.domain.model.AppPreferences
import com.bluescope.ai.domain.model.ExportRequest
import com.bluescope.ai.domain.model.ImportConflictPolicy
import com.bluescope.ai.domain.model.ThemeMode
import com.bluescope.ai.domain.repository.DeviceRepository
import com.bluescope.ai.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val deviceRepository: DeviceRepository,
) : ViewModel() {
    val preferences: StateFlow<AppPreferences> = preferencesRepository.preferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppPreferences(),
    )
    private val _exportJson = MutableStateFlow<String?>(null)
    val exportJson: StateFlow<String?> = _exportJson.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setTheme(theme: ThemeMode) = update { it.copy(themeMode = theme) }
    fun setClassic(enabled: Boolean) = update { it.copy(includeClassic = enabled) }
    fun setBonded(enabled: Boolean) = update { it.copy(includeBonded = enabled) }
    fun setScanDuration(seconds: Int) = update { it.copy(scanDuration = Duration.ofSeconds(seconds.toLong())) }
    fun setRemoteAi(enabled: Boolean) = update { it.copy(remoteAiEnabled = enabled) }
    fun deleteAll() = viewModelScope.launch { deviceRepository.deleteAll() }
    fun prepareExport() = viewModelScope.launch {
        runCatching { deviceRepository.export(ExportRequest()) }
            .onSuccess { _exportJson.value = it.json }
            .onFailure { _message.value = "Export impossible : ${it.message.orEmpty()}" }
    }
    fun exportConsumed() { _exportJson.value = null }
    fun importJson(json: String) = viewModelScope.launch {
        val report = deviceRepository.import(json, ImportConflictPolicy.REPLACE_IF_NEWER)
        _message.value = if (report.rejected == 0) {
            "Import terminé : ${report.imported} ajouté(s), ${report.skipped} ignoré(s)."
        } else {
            report.warnings.firstOrNull() ?: "Import refusé."
        }
    }
    fun clearMessage() { _message.value = null }

    private fun update(transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch { preferencesRepository.update(transform) }
    }
}
