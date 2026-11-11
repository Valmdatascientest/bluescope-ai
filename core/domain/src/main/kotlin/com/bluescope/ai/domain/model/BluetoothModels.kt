package com.bluescope.ai.domain.model

import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class BluetoothTransport { BLE, CLASSIC, DUAL, UNKNOWN }

enum class BluetoothAddressType { PUBLIC, RANDOM, ANONYMOUS, UNKNOWN }

enum class BondState { NONE, BONDING, BONDED, UNKNOWN }

enum class Freshness { LIVE, CACHED }

enum class DiscoverySource {
    BLE_ADVERTISEMENT,
    CLASSIC_DISCOVERY,
    PAIRED_CACHE,
    GATT,
    IMPORTED,
}

data class ObservedAddress(
    val value: String,
    val type: BluetoothAddressType = BluetoothAddressType.UNKNOWN,
    /** Only true when the platform provides evidence that this address is reusable. */
    val isStable: Boolean = false,
) {
    init {
        require(value.isNotBlank()) { "Une adresse observée ne peut pas être vide" }
    }

    /** A representation suitable for UI diagnostics without exposing the full identifier. */
    fun redacted(): String = value
        .split(':')
        .takeIf { it.size >= 2 }
        ?.let { parts -> "••:••:••:••:${parts.takeLast(2).joinToString(":")}" }
        ?: "••••${value.takeLast(4)}"
}

data class BluetoothDeviceSnapshot(
    val id: DeviceId = DeviceId.random(),
    val name: String? = null,
    val address: ObservedAddress? = null,
    val transport: BluetoothTransport = BluetoothTransport.UNKNOWN,
    val rssi: Int? = null,
    val txPower: Int? = null,
    val bondState: BondState = BondState.UNKNOWN,
    val deviceClass: Int? = null,
    val appearance: Int? = null,
    val serviceUuids: Set<UUID> = emptySet(),
    val manufacturerData: Map<Int, BinaryData> = emptyMap(),
    val serviceData: Map<UUID, BinaryData> = emptyMap(),
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val freshness: Freshness = Freshness.LIVE,
    val source: DiscoverySource,
    val connectable: Boolean? = null,
) {
    init {
        require(!lastSeenAt.isBefore(firstSeenAt)) {
            "lastSeenAt ne peut pas précéder firstSeenAt"
        }
        require(rssi == null || rssi in -127..20) { "RSSI Bluetooth invalide: $rssi" }
        require(txPower == null || txPower in -127..126) { "Tx Power invalide: $txPower" }
        require(manufacturerData.keys.all { it in 0..0xFFFF }) {
            "Un Company Identifier doit tenir sur 16 bits"
        }
    }

    val displayName: String get() = name?.takeIf(String::isNotBlank) ?: "Périphérique sans nom"
}

data class ScanConfig(
    val duration: Duration = Duration.ofSeconds(15),
    val scanBle: Boolean = true,
    val scanClassic: Boolean = true,
    val includeBonded: Boolean = true,
    val lowLatency: Boolean = true,
) {
    init {
        require(scanBle || scanClassic) { "Au moins un transport doit être scanné" }
        require(duration in MIN_DURATION..MAX_DURATION) {
            "La durée de scan doit être comprise entre 5 et 60 secondes"
        }
    }

    companion object {
        val MIN_DURATION: Duration = Duration.ofSeconds(5)
        val MAX_DURATION: Duration = Duration.ofSeconds(60)
    }
}

enum class ScanPhase { BLE, CLASSIC }

data class ScanSession(
    val id: ScanSessionId = ScanSessionId.random(),
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val requestedTransports: Set<BluetoothTransport>,
) {
    init {
        require(endedAt == null || !endedAt.isBefore(startedAt)) {
            "La fin de session ne peut pas précéder son début"
        }
        require(requestedTransports.isNotEmpty()) { "Une session doit demander un transport" }
    }
}

sealed interface BluetoothError {
    data object PermissionDenied : BluetoothError
    data object BluetoothDisabled : BluetoothError
    data object Unsupported : BluetoothError
    data object Throttled : BluetoothError
    data object AlreadyScanning : BluetoothError
    data object OutOfResources : BluetoothError
    data class Unexpected(val platformCode: Int? = null, val detail: String? = null) : BluetoothError
}

sealed interface ScanEvent {
    data class Started(val session: ScanSession) : ScanEvent
    data class PhaseChanged(val sessionId: ScanSessionId, val phase: ScanPhase) : ScanEvent
    data class DeviceFound(val sessionId: ScanSessionId, val device: BluetoothDeviceSnapshot) : ScanEvent
    data class DeviceUpdated(val sessionId: ScanSessionId, val device: BluetoothDeviceSnapshot) : ScanEvent
    data class Finished(val session: ScanSession) : ScanEvent
    data class Failed(val sessionId: ScanSessionId?, val error: BluetoothError) : ScanEvent
}

data class BluetoothObservation(
    val sessionId: ScanSessionId,
    val deviceId: DeviceId,
    val observedAt: Instant,
    val snapshot: BluetoothDeviceSnapshot,
)
