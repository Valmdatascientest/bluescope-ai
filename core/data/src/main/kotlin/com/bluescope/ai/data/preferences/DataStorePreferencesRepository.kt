package com.bluescope.ai.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.bluescope.ai.domain.model.AppPreferences
import com.bluescope.ai.domain.model.ScanConfig
import com.bluescope.ai.domain.model.ThemeMode
import com.bluescope.ai.domain.repository.PreferencesRepository
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStorePreferencesRepository(
    context: Context,
) : PreferencesRepository {
    private val dataStore = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
    )

    override val preferences: Flow<AppPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { values ->
            val duration = (values[Keys.SCAN_DURATION_SECONDS] ?: DEFAULT_SCAN_SECONDS)
                .coerceIn(
                    ScanConfig.MIN_DURATION.seconds.toInt(),
                    ScanConfig.MAX_DURATION.seconds.toInt(),
                )
            AppPreferences(
                themeMode = values[Keys.THEME]?.let { stored ->
                    ThemeMode.entries.firstOrNull { it.name == stored }
                } ?: ThemeMode.SYSTEM,
                scanDuration = Duration.ofSeconds(duration.toLong()),
                includeClassic = values[Keys.INCLUDE_CLASSIC] ?: true,
                includeBonded = values[Keys.INCLUDE_BONDED] ?: true,
                redactExports = values[Keys.REDACT_EXPORTS] ?: true,
                remoteAiEnabled = values[Keys.REMOTE_AI] ?: false,
            )
        }

    override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        dataStore.edit { stored ->
            val current = stored.toDomain()
            val updated = transform(current)
            stored[Keys.THEME] = updated.themeMode.name
            stored[Keys.SCAN_DURATION_SECONDS] = updated.scanDuration.seconds.toInt()
            stored[Keys.INCLUDE_CLASSIC] = updated.includeClassic
            stored[Keys.INCLUDE_BONDED] = updated.includeBonded
            stored[Keys.REDACT_EXPORTS] = updated.redactExports
            stored[Keys.REMOTE_AI] = updated.remoteAiEnabled
        }
    }

    override suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.toDomain(): AppPreferences {
        val duration = (this[Keys.SCAN_DURATION_SECONDS] ?: DEFAULT_SCAN_SECONDS)
            .coerceIn(ScanConfig.MIN_DURATION.seconds.toInt(), ScanConfig.MAX_DURATION.seconds.toInt())
        return AppPreferences(
            themeMode = this[Keys.THEME]?.let { value ->
                ThemeMode.entries.firstOrNull { it.name == value }
            } ?: ThemeMode.SYSTEM,
            scanDuration = Duration.ofSeconds(duration.toLong()),
            includeClassic = this[Keys.INCLUDE_CLASSIC] ?: true,
            includeBonded = this[Keys.INCLUDE_BONDED] ?: true,
            redactExports = this[Keys.REDACT_EXPORTS] ?: true,
            remoteAiEnabled = this[Keys.REMOTE_AI] ?: false,
        )
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val SCAN_DURATION_SECONDS = intPreferencesKey("scan_duration_seconds")
        val INCLUDE_CLASSIC = booleanPreferencesKey("include_classic")
        val INCLUDE_BONDED = booleanPreferencesKey("include_bonded")
        val REDACT_EXPORTS = booleanPreferencesKey("redact_exports")
        val REMOTE_AI = booleanPreferencesKey("remote_ai")
    }

    private companion object {
        const val FILE_NAME = "settings.preferences_pb"
        const val DEFAULT_SCAN_SECONDS = 15
    }
}
