package com.bluescope.ai.bluetooth.scanner

import java.util.Locale

internal enum class RawTransport {
    BLE,
    CLASSIC,
}

/**
 * Internal, session-scoped representation. It deliberately does not attempt to link rotating addresses or
 * the BLE and Classic identities of a dual-mode device.
 */
internal data class RawBluetoothObservation(
    val address: String,
    val transport: RawTransport,
    val advertisingSid: Int? = null,
    val name: String? = null,
    val rssi: Int? = null,
    val txPower: Int? = null,
    val serviceUuids: Set<String> = emptySet(),
    val manufacturerIds: Set<Int> = emptySet(),
    val firstSeenAtMillis: Long,
    val lastSeenAtMillis: Long,
) {
    init {
        require(address.isNotBlank())
        require(lastSeenAtMillis >= firstSeenAtMillis)
    }

    val sessionKey: ObservationSessionKey = ObservationSessionKey(
        transport = transport,
        normalizedAddress = address.uppercase(Locale.ROOT),
        advertisingSid = if (transport == RawTransport.BLE) advertisingSid else null,
    )
}

internal data class ObservationSessionKey(
    val transport: RawTransport,
    val normalizedAddress: String,
    val advertisingSid: Int?,
)

internal sealed interface MergeResult {
    val observation: RawBluetoothObservation

    data class Added(override val observation: RawBluetoothObservation) : MergeResult
    data class Updated(override val observation: RawBluetoothObservation) : MergeResult
}

/** Safe in-session deduplication only; no heuristic identity fusion is performed. */
internal class SessionObservationAccumulator {
    private val observations = linkedMapOf<ObservationSessionKey, RawBluetoothObservation>()

    fun merge(incoming: RawBluetoothObservation): MergeResult {
        val current = observations[incoming.sessionKey]
        val merged = current?.mergeWith(incoming) ?: incoming
        observations[incoming.sessionKey] = merged
        return if (current == null) MergeResult.Added(merged) else MergeResult.Updated(merged)
    }

    fun snapshot(): List<RawBluetoothObservation> = observations.values.toList()

    private fun RawBluetoothObservation.mergeWith(newer: RawBluetoothObservation) = copy(
        name = newer.name ?: name,
        rssi = newer.rssi ?: rssi,
        txPower = newer.txPower ?: txPower,
        serviceUuids = serviceUuids + newer.serviceUuids,
        manufacturerIds = manufacturerIds + newer.manufacturerIds,
        firstSeenAtMillis = minOf(firstSeenAtMillis, newer.firstSeenAtMillis),
        lastSeenAtMillis = maxOf(lastSeenAtMillis, newer.lastSeenAtMillis),
    )
}
