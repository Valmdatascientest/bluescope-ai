package com.bluescope.ai.bluetooth.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.bluescope.ai.bluetooth.permission.BluetoothPermissionPolicy
import com.bluescope.ai.domain.model.BinaryData
import com.bluescope.ai.domain.model.BluetoothAddressType
import com.bluescope.ai.domain.model.BluetoothDeviceSnapshot
import com.bluescope.ai.domain.model.BluetoothError
import com.bluescope.ai.domain.model.BluetoothTransport
import com.bluescope.ai.domain.model.BondState
import com.bluescope.ai.domain.model.DeviceId
import com.bluescope.ai.domain.model.DiscoverySource
import com.bluescope.ai.domain.model.Freshness
import com.bluescope.ai.domain.model.ObservedAddress
import com.bluescope.ai.domain.model.ScanConfig
import com.bluescope.ai.domain.model.ScanEvent
import com.bluescope.ai.domain.model.ScanPhase
import com.bluescope.ai.domain.model.ScanSession
import com.bluescope.ai.domain.repository.BluetoothScanner
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground, user-driven scanner. BLE and Classic never run concurrently and every platform callback is
 * scoped to one collection of [scan]. The class deliberately performs no cross-session identity inference.
 */
class AndroidBluetoothScanner(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : BluetoothScanner {
    private val appContext = context.applicationContext
    private val active = AtomicReference<ScanControl?>(null)

    override fun scan(config: ScanConfig): Flow<ScanEvent> = flow {
        val control = ScanControl()
        if (!active.compareAndSet(null, control)) {
            emit(ScanEvent.Failed(sessionId = null, error = BluetoothError.AlreadyScanning))
            return@flow
        }

        val startedAt = clock.instant()
        val requested = buildSet {
            if (config.scanBle) add(BluetoothTransport.BLE)
            if (config.scanClassic) add(BluetoothTransport.CLASSIC)
        }
        val session = ScanSession(startedAt = startedAt, requestedTransports = requested)
        val observed = linkedMapOf<ObservationSessionKey, BluetoothDeviceSnapshot>()
        emit(ScanEvent.Started(session))

        try {
            if (BluetoothPermissionPolicy.missingScanPermissions(appContext, sdkInt).isNotEmpty()) {
                emit(ScanEvent.Failed(session.id, BluetoothError.PermissionDenied))
                return@flow
            }
            val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null) {
                emit(ScanEvent.Failed(session.id, BluetoothError.Unsupported))
                return@flow
            }
            if (!adapter.isEnabledSafely()) {
                emit(ScanEvent.Failed(session.id, BluetoothError.BluetoothDisabled))
                return@flow
            }

            if (config.includeBonded) {
                adapter.bondedDevicesSafely().forEach { device ->
                    val now = clock.instant()
                    val snapshot = device.toSnapshot(
                        now = now,
                        transport = device.domainTransport(default = BluetoothTransport.CLASSIC),
                        source = DiscoverySource.PAIRED_CACHE,
                        freshness = Freshness.CACHED,
                    )
                    val key = ObservationSessionKey(
                        RawTransport.CLASSIC,
                        snapshot.address?.value.orEmpty().uppercase(),
                        null,
                    )
                    observed[key] = snapshot
                    emit(ScanEvent.DeviceFound(session.id, snapshot))
                }
            }

            val phases = listOfNotNull(
                ScanPhase.BLE.takeIf { config.scanBle },
                ScanPhase.CLASSIC.takeIf { config.scanClassic },
            )
            val phaseDuration = config.duration.dividedBy(phases.size.toLong())
            for (phase in phases) {
                if (control.stop.isCompleted || control.failure != null) break
                emit(ScanEvent.PhaseChanged(session.id, phase))
                val signals = when (phase) {
                    ScanPhase.BLE -> bleSignals(adapter, config.lowLatency)
                    ScanPhase.CLASSIC -> classicSignals(adapter)
                }
                collectPhase(signals, control, phaseDuration) { signal ->
                    when (signal) {
                        is PlatformScanSignal.Failure -> {
                            control.failure = signal.error
                            control.stop.complete(Unit)
                            emit(ScanEvent.Failed(session.id, signal.error))
                        }

                        is PlatformScanSignal.Observation -> {
                            val incoming = signal.value
                            val current = observed[incoming.key]
                            val merged = current?.merge(incoming.snapshot) ?: incoming.snapshot
                            observed[incoming.key] = merged
                            emit(
                                if (current == null) ScanEvent.DeviceFound(session.id, merged)
                                else ScanEvent.DeviceUpdated(session.id, merged),
                            )
                        }
                    }
                }
            }

            if (control.failure == null) {
                emit(ScanEvent.Finished(session.copy(endedAt = clock.instant())))
            }
        } catch (_: SecurityException) {
            emit(ScanEvent.Failed(session.id, BluetoothError.PermissionDenied))
        } finally {
            active.compareAndSet(control, null)
        }
    }

    override suspend fun stop() {
        active.get()?.stop?.complete(Unit)
    }

    private suspend fun collectPhase(
        source: Flow<PlatformScanSignal>,
        control: ScanControl,
        duration: Duration,
        onSignal: suspend (PlatformScanSignal) -> Unit,
    ) {
        val stopFlow = flow {
            control.stop.await()
            emit(PhaseItem.Stop)
        }
        val phaseFlow = merge(source.map { PhaseItem.Signal(it) }, stopFlow)
            .takeWhile { item -> item !is PhaseItem.Stop }
        withTimeoutOrNull(duration.toMillis()) {
            phaseFlow.collect { item -> onSignal((item as PhaseItem.Signal).value) }
        }
    }

    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun bleSignals(adapter: BluetoothAdapter, lowLatency: Boolean): Flow<PlatformScanSignal> = callbackFlow {
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            trySend(PlatformScanSignal.Failure(BluetoothError.Unsupported))
            close()
            return@callbackFlow
        }
        val settings = ScanSettings.Builder()
            .setScanMode(if (lowLatency) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_BALANCED)
            .build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                result.toObservation(clock.instant())?.let { trySend(PlatformScanSignal.Observation(it)) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                val now = clock.instant()
                results.forEach { result ->
                    result.toObservation(now)?.let { trySend(PlatformScanSignal.Observation(it)) }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                trySend(PlatformScanSignal.Failure(errorCode.toBluetoothError()))
                close()
            }
        }
        try {
            scanner.startScan(null, settings, callback)
        } catch (_: SecurityException) {
            trySend(PlatformScanSignal.Failure(BluetoothError.PermissionDenied))
            close()
        } catch (error: RuntimeException) {
            trySend(PlatformScanSignal.Failure(BluetoothError.Unexpected(detail = error.javaClass.simpleName)))
            close()
        }
        awaitClose {
            runCatching { scanner.stopScan(callback) }
        }
    }

    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun classicSignals(adapter: BluetoothAdapter): Flow<PlatformScanSignal> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.bluetoothDeviceExtra() ?: return
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                            .takeUnless { it == Short.MIN_VALUE }
                            ?.toInt()
                        val now = clock.instant()
                        device.toClassicObservation(now, rssi)?.let {
                            trySend(PlatformScanSignal.Observation(it))
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> close()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        var registered = false
        try {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            registered = true
            if (!adapter.startDiscovery()) {
                trySend(PlatformScanSignal.Failure(BluetoothError.Unexpected(detail = "classic_discovery_not_started")))
                close()
            }
        } catch (_: SecurityException) {
            trySend(PlatformScanSignal.Failure(BluetoothError.PermissionDenied))
            close()
        } catch (error: RuntimeException) {
            trySend(PlatformScanSignal.Failure(BluetoothError.Unexpected(detail = error.javaClass.simpleName)))
            close()
        }
        awaitClose {
            runCatching { adapter.cancelDiscovery() }
            if (registered) runCatching { appContext.unregisterReceiver(receiver) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun ScanResult.toObservation(now: Instant): PlatformObservation? {
        val platformDevice = device ?: return null
        val address = runCatching { platformDevice.address }.getOrNull()?.takeIf(String::isNotBlank) ?: return null
        val sid = advertisingSid.takeUnless { it == ScanResult.SID_NOT_PRESENT }
        val record = scanRecord
        val manufacturerData = buildMap {
            val sparse = record?.manufacturerSpecificData ?: return@buildMap
            for (index in 0 until sparse.size()) {
                put(sparse.keyAt(index), BinaryData.of(sparse.valueAt(index).boundedCopy()))
            }
        }
        val serviceData = record?.serviceData.orEmpty().mapNotNull { (uuid, value) ->
            uuid?.uuid?.let { it to BinaryData.of(value.boundedCopy()) }
        }.toMap()
        val serviceUuids = record?.serviceUuids.orEmpty().mapNotNull { it?.uuid }.toSet()
        val snapshot = platformDevice.toSnapshot(
            now = now,
            nameOverride = record?.deviceName,
            transport = platformDevice.domainTransport(BluetoothTransport.BLE),
            source = DiscoverySource.BLE_ADVERTISEMENT,
            rssi = rssi.normalizeRssi(),
            txPower = record?.txPowerLevel?.takeUnless { it == Int.MIN_VALUE }?.normalizeTxPower(),
            appearance = AdvertisingDataParser.appearance(record?.bytes),
            serviceUuids = serviceUuids,
            manufacturerData = manufacturerData,
            serviceData = serviceData,
            connectable = isConnectable,
        )
        return PlatformObservation(
            key = ObservationSessionKey(RawTransport.BLE, address.uppercase(), sid),
            snapshot = snapshot,
        )
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toClassicObservation(now: Instant, rssi: Int?): PlatformObservation? {
        val address = runCatching { address }.getOrNull()?.takeIf(String::isNotBlank) ?: return null
        return PlatformObservation(
            key = ObservationSessionKey(RawTransport.CLASSIC, address.uppercase(), null),
            snapshot = toSnapshot(
                now = now,
                transport = domainTransport(BluetoothTransport.CLASSIC),
                source = DiscoverySource.CLASSIC_DISCOVERY,
                rssi = rssi?.normalizeRssi(),
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toSnapshot(
        now: Instant,
        nameOverride: String? = null,
        transport: BluetoothTransport,
        source: DiscoverySource,
        freshness: Freshness = Freshness.LIVE,
        rssi: Int? = null,
        txPower: Int? = null,
        appearance: Int? = null,
        serviceUuids: Set<UUID> = emptySet(),
        manufacturerData: Map<Int, BinaryData> = emptyMap(),
        serviceData: Map<UUID, BinaryData> = emptyMap(),
        connectable: Boolean? = null,
    ): BluetoothDeviceSnapshot = BluetoothDeviceSnapshot(
        name = nameOverride ?: runCatching { name }.getOrNull(),
        address = runCatching {
            val domainAddressType = if (sdkInt >= 35) {
                addressType.toDomainAddressType()
            } else {
                BluetoothAddressType.UNKNOWN
            }
            ObservedAddress(
                value = address,
                type = domainAddressType,
                isStable = bondState == BluetoothDevice.BOND_BONDED ||
                    domainAddressType == BluetoothAddressType.PUBLIC,
            )
        }.getOrNull(),
        transport = transport,
        rssi = rssi,
        txPower = txPower,
        bondState = runCatching { bondState.toDomainBondState() }.getOrDefault(BondState.UNKNOWN),
        deviceClass = runCatching { bluetoothClass?.deviceClass }.getOrNull(),
        appearance = appearance,
        serviceUuids = serviceUuids.ifEmpty {
            runCatching { uuids?.map { it.uuid }?.toSet().orEmpty() }.getOrDefault(emptySet())
        },
        manufacturerData = manufacturerData,
        serviceData = serviceData,
        firstSeenAt = now,
        lastSeenAt = now,
        freshness = freshness,
        source = source,
        connectable = connectable,
    )

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.domainTransport(default: BluetoothTransport): BluetoothTransport =
        runCatching {
            when (type) {
                BluetoothDevice.DEVICE_TYPE_DUAL -> BluetoothTransport.DUAL
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> BluetoothTransport.CLASSIC
                BluetoothDevice.DEVICE_TYPE_LE -> BluetoothTransport.BLE
                else -> default
            }
        }.getOrDefault(default)

    @SuppressLint("MissingPermission")
    private fun BluetoothAdapter.isEnabledSafely(): Boolean = runCatching { isEnabled }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    private fun BluetoothAdapter.bondedDevicesSafely(): Set<BluetoothDevice> =
        runCatching { bondedDevices.orEmpty() }.getOrDefault(emptySet())

    private fun Intent.bluetoothDeviceExtra(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private fun BluetoothDeviceSnapshot.merge(newer: BluetoothDeviceSnapshot): BluetoothDeviceSnapshot = copy(
        name = newer.name ?: name,
        address = newer.address ?: address,
        transport = when {
            transport == newer.transport -> transport
            transport == BluetoothTransport.UNKNOWN -> newer.transport
            newer.transport == BluetoothTransport.UNKNOWN -> transport
            else -> BluetoothTransport.DUAL
        },
        rssi = newer.rssi ?: rssi,
        txPower = newer.txPower ?: txPower,
        bondState = if (newer.bondState == BondState.UNKNOWN) bondState else newer.bondState,
        deviceClass = newer.deviceClass ?: deviceClass,
        appearance = newer.appearance ?: appearance,
        serviceUuids = serviceUuids + newer.serviceUuids,
        manufacturerData = manufacturerData + newer.manufacturerData,
        serviceData = serviceData + newer.serviceData,
        firstSeenAt = minOf(firstSeenAt, newer.firstSeenAt),
        lastSeenAt = maxOf(lastSeenAt, newer.lastSeenAt),
        freshness = newer.freshness,
        source = newer.source,
        connectable = newer.connectable ?: connectable,
    )

    private fun Int.toDomainBondState(): BondState = when (this) {
        BluetoothDevice.BOND_NONE -> BondState.NONE
        BluetoothDevice.BOND_BONDING -> BondState.BONDING
        BluetoothDevice.BOND_BONDED -> BondState.BONDED
        else -> BondState.UNKNOWN
    }

    private fun Int.toDomainAddressType(): BluetoothAddressType = when (this) {
        BluetoothDevice.ADDRESS_TYPE_PUBLIC -> BluetoothAddressType.PUBLIC
        BluetoothDevice.ADDRESS_TYPE_RANDOM -> BluetoothAddressType.RANDOM
        BluetoothDevice.ADDRESS_TYPE_ANONYMOUS -> BluetoothAddressType.ANONYMOUS
        else -> BluetoothAddressType.UNKNOWN
    }

    private fun Int.toBluetoothError(): BluetoothError = when (this) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> BluetoothError.AlreadyScanning
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED,
        ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES,
        -> BluetoothError.OutOfResources
        ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> BluetoothError.Throttled
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> BluetoothError.Unsupported
        else -> BluetoothError.Unexpected(platformCode = this)
    }

    private fun ByteArray.boundedCopy(): ByteArray = copyOf(minOf(size, MAX_PAYLOAD_BYTES))
    private fun Int.normalizeRssi(): Int? = takeIf { it in -127..20 }
    private fun Int.normalizeTxPower(): Int? = takeIf { it in -127..126 }

    private class ScanControl {
        val stop = CompletableDeferred<Unit>()
        var failure: BluetoothError? = null
    }

    private sealed interface PhaseItem {
        data class Signal(val value: PlatformScanSignal) : PhaseItem
        data object Stop : PhaseItem
    }

    private sealed interface PlatformScanSignal {
        data class Observation(val value: PlatformObservation) : PlatformScanSignal
        data class Failure(val error: BluetoothError) : PlatformScanSignal
    }

    private data class PlatformObservation(
        val key: ObservationSessionKey,
        val snapshot: BluetoothDeviceSnapshot,
    )

    private companion object {
        const val MAX_PAYLOAD_BYTES = 1_024
    }
}
