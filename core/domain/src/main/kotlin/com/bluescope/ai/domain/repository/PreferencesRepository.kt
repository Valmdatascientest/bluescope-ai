package com.bluescope.ai.domain.repository

import com.bluescope.ai.domain.model.AppPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val preferences: Flow<AppPreferences>
    suspend fun update(transform: (AppPreferences) -> AppPreferences)
    suspend fun reset()
}
