package com.bluescope.ai.domain.model

import java.time.Instant
import java.util.UUID

enum class GattServiceType { PRIMARY, SECONDARY, UNKNOWN }

enum class GattProperty {
    BROADCAST,
    READ,
    WRITE,
    WRITE_WITHOUT_RESPONSE,
    NOTIFY,
    INDICATE,
    SIGNED_WRITE,
    EXTENDED_PROPERTIES,
}

enum class GattReadStatus {
    NOT_ATTEMPTED,
    SUCCESS,
    NOT_PERMITTED,
    AUTHENTICATION_REQUIRED,
    FAILED,
}

data class GattDescriptorInfo(
    val uuid: UUID,
    val value: BinaryData? = null,
    val readStatus: GattReadStatus = GattReadStatus.NOT_ATTEMPTED,
)

data class GattCharacteristicInfo(
    val uuid: UUID,
    val properties: Set<GattProperty>,
    val descriptors: List<GattDescriptorInfo> = emptyList(),
    val value: BinaryData? = null,
    val readStatus: GattReadStatus = GattReadStatus.NOT_ATTEMPTED,
) {
    val isReadable: Boolean get() = GattProperty.READ in properties
    val supportsNotifications: Boolean get() = GattProperty.NOTIFY in properties
    val supportsIndications: Boolean get() = GattProperty.INDICATE in properties
}

data class GattServiceInfo(
    val uuid: UUID,
    val type: GattServiceType = GattServiceType.UNKNOWN,
    val characteristics: List<GattCharacteristicInfo> = emptyList(),
)

data class GattSnapshot(
    val deviceId: DeviceId,
    val services: List<GattServiceInfo>,
    val discoveredAt: Instant,
    val freshness: Freshness = Freshness.LIVE,
)

/** Proof that the user explicitly approved this one active exploration. */
data class ExplorationConsent private constructor(
    val grantedAt: Instant,
    val allowCharacteristicReads: Boolean,
) {
    companion object {
        fun granted(
            allowCharacteristicReads: Boolean = false,
            at: Instant = Instant.now(),
        ): ExplorationConsent = ExplorationConsent(
            grantedAt = at,
            allowCharacteristicReads = allowCharacteristicReads,
        )
    }
}

sealed interface GattError {
    data object PermissionDenied : GattError
    data object BluetoothDisabled : GattError
    data object UnsupportedTransport : GattError
    data object Timeout : GattError
    data object AuthenticationRequired : GattError
    data object ReadNotPermitted : GattError
    data object Disconnected : GattError
    data class PlatformFailure(val status: Int, val detail: String? = null) : GattError
}

sealed interface GattEvent {
    data class Connecting(val deviceId: DeviceId) : GattEvent
    data class Connected(val deviceId: DeviceId) : GattEvent
    data class DiscoveringServices(val deviceId: DeviceId) : GattEvent
    data class ServiceDiscovered(val service: GattServiceInfo) : GattEvent
    data class ReadingCharacteristic(val serviceUuid: UUID, val characteristicUuid: UUID) : GattEvent
    data class Completed(val snapshot: GattSnapshot) : GattEvent
    data class Disconnected(val deviceId: DeviceId) : GattEvent
    data class Failed(val deviceId: DeviceId, val error: GattError) : GattEvent
}
