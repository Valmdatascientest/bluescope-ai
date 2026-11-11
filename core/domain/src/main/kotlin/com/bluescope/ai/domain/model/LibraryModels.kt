package com.bluescope.ai.domain.model

import java.time.Duration
import java.time.Instant

data class KnownDevice(
    val snapshot: BluetoothDeviceSnapshot,
    val latestGatt: GattSnapshot? = null,
    val latestAnalysis: DeviceAnalysis? = null,
    val isFavorite: Boolean = false,
    val note: String? = null,
    val lastVerifiedAt: Instant? = null,
)

enum class DeviceSort { LAST_SEEN_DESC, FIRST_SEEN_DESC, NAME_ASC, SIGNAL_DESC }

data class DeviceQuery(
    val text: String = "",
    val transports: Set<BluetoothTransport> = emptySet(),
    val categories: Set<DeviceCategory> = emptySet(),
    val favoritesOnly: Boolean = false,
    val sort: DeviceSort = DeviceSort.LAST_SEEN_DESC,
)

data class ExportRequest(
    val includeAddresses: Boolean = false,
    val includeRawPayloads: Boolean = false,
    val favoriteOnly: Boolean = false,
)

data class ExportPayload(
    val schemaVersion: Int,
    val json: String,
    val exportedAt: Instant,
    val deviceCount: Int,
) {
    init {
        require(schemaVersion > 0) { "La version d’export doit être positive" }
        require(json.isNotBlank()) { "Le contenu exporté ne peut pas être vide" }
        require(deviceCount >= 0) { "Le nombre de périphériques ne peut pas être négatif" }
    }
}

enum class ImportConflictPolicy { KEEP_EXISTING, REPLACE_IF_NEWER, KEEP_BOTH }

data class ImportReport(
    val imported: Int,
    val skipped: Int,
    val rejected: Int,
    val warnings: List<String> = emptyList(),
) {
    init {
        require(imported >= 0 && skipped >= 0 && rejected >= 0)
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val scanDuration: Duration = Duration.ofSeconds(15),
    val includeClassic: Boolean = true,
    val includeBonded: Boolean = true,
    val redactExports: Boolean = true,
    val remoteAiEnabled: Boolean = false,
) {
    init {
        require(scanDuration in ScanConfig.MIN_DURATION..ScanConfig.MAX_DURATION)
    }
}

/** Explicit, revocable consent for a single remote inference request. */
data class AiPrivacyConsent private constructor(
    val grantedAt: Instant,
    val allowAdvertisedName: Boolean,
    val allowAddress: Boolean,
) {
    companion object {
        fun granted(
            allowAdvertisedName: Boolean = false,
            allowAddress: Boolean = false,
            at: Instant = Instant.now(),
        ): AiPrivacyConsent = AiPrivacyConsent(at, allowAdvertisedName, allowAddress)
    }
}
