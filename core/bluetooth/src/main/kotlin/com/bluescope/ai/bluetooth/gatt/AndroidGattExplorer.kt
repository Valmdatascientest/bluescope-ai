package com.bluescope.ai.bluetooth.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.bluescope.ai.bluetooth.permission.BluetoothPermissionPolicy
import com.bluescope.ai.domain.model.BinaryData
import com.bluescope.ai.domain.model.BluetoothDeviceSnapshot
import com.bluescope.ai.domain.model.BluetoothTransport
import com.bluescope.ai.domain.model.ExplorationConsent
import com.bluescope.ai.domain.model.GattCharacteristicInfo
import com.bluescope.ai.domain.model.GattDescriptorInfo
import com.bluescope.ai.domain.model.GattError
import com.bluescope.ai.domain.model.GattEvent
import com.bluescope.ai.domain.model.GattProperty
import com.bluescope.ai.domain.model.GattReadStatus
import com.bluescope.ai.domain.model.GattServiceInfo
import com.bluescope.ai.domain.model.GattSnapshot
import com.bluescope.ai.domain.repository.BluetoothScanner
import com.bluescope.ai.domain.repository.GattExplorer
import java.time.Clock
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/** Read-only GATT discovery. It never writes, subscribes, pairs, or calls hidden cache APIs. */
class AndroidGattExplorer(
    context: Context,
    private val scanner: BluetoothScanner? = null,
    private val clock: Clock = Clock.systemUTC(),
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : GattExplorer {
    private val appContext = context.applicationContext
    private val active = AtomicReference<GattControl?>(null)

    override fun explore(
        device: BluetoothDeviceSnapshot,
        consent: ExplorationConsent,
    ): Flow<GattEvent> = flow {
        scanner?.stop()
        emitAll(explorePlatform(device, consent))
    }

    override suspend fun cancel() {
        active.get()?.cancel?.set(true)
    }

    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun explorePlatform(
        snapshot: BluetoothDeviceSnapshot,
        consent: ExplorationConsent,
    ): Flow<GattEvent> = callbackFlow {
        val control = GattControl()
        if (!active.compareAndSet(null, control)) {
            trySend(GattEvent.Failed(snapshot.id, GattError.PlatformFailure(-1, "exploration_already_active")))
            close()
            return@callbackFlow
        }

        fun finish(event: GattEvent) {
            if (!control.terminal.compareAndSet(false, true)) return
            trySend(event)
            control.closeGatt()
            close()
        }

        if (BluetoothPermissionPolicy.missingConnectPermissions(appContext, sdkInt).isNotEmpty()) {
            finish(GattEvent.Failed(snapshot.id, GattError.PermissionDenied))
            return@callbackFlow
        }
        if (snapshot.transport !in setOf(BluetoothTransport.BLE, BluetoothTransport.DUAL)) {
            finish(GattEvent.Failed(snapshot.id, GattError.UnsupportedTransport))
            return@callbackFlow
        }
        val address = snapshot.address?.value
        if (address == null || !BluetoothAdapter.checkBluetoothAddress(address)) {
            finish(GattEvent.Failed(snapshot.id, GattError.PlatformFailure(-1, "invalid_or_missing_address")))
            return@callbackFlow
        }
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            finish(GattEvent.Failed(snapshot.id, GattError.UnsupportedTransport))
            return@callbackFlow
        }
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) {
            finish(GattEvent.Failed(snapshot.id, GattError.BluetoothDisabled))
            return@callbackFlow
        }

        val readQueue = ArrayDeque<BluetoothGattCharacteristic>()
        val reads = Collections.synchronizedMap(
            IdentityHashMap<BluetoothGattCharacteristic, CharacteristicRead>(),
        )

        fun BluetoothGattService.toInfo(): GattServiceInfo = GattServiceInfo(
            uuid = uuid,
            type = GattMappers.serviceType(type),
            characteristics = characteristics.orEmpty().map { characteristic ->
                val read = reads[characteristic]
                GattCharacteristicInfo(
                    uuid = characteristic.uuid,
                    properties = GattMappers.properties(characteristic.properties),
                    descriptors = characteristic.descriptors.orEmpty().map { descriptor ->
                        GattDescriptorInfo(uuid = descriptor.uuid)
                    },
                    value = read?.value,
                    readStatus = read?.status ?: GattReadStatus.NOT_ATTEMPTED,
                )
            },
        )

        fun complete(gatt: BluetoothGatt) {
            val services = gatt.services.orEmpty().map { it.toInfo() }
            finish(
                GattEvent.Completed(
                    GattSnapshot(
                        deviceId = snapshot.id,
                        services = services,
                        discoveredAt = clock.instant(),
                    ),
                ),
            )
        }

        lateinit var readNext: (BluetoothGatt) -> Unit
        readNext = { gatt ->
            if (!control.terminal.get()) {
                val characteristic = readQueue.pollFirst()
                if (characteristic == null) {
                    complete(gatt)
                } else {
                    trySend(
                        GattEvent.ReadingCharacteristic(
                            serviceUuid = characteristic.service.uuid,
                            characteristicUuid = characteristic.uuid,
                        ),
                    )
                    val started = runCatching { gatt.readCharacteristic(characteristic) }.getOrDefault(false)
                    if (!started) {
                        reads[characteristic] = CharacteristicRead(GattReadStatus.FAILED)
                        readNext(gatt)
                    }
                }
            }
        }

        fun handleRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray?,
            status: Int,
        ) {
            if (control.terminal.get()) return
            val readStatus = GattMappers.readStatus(status)
            reads[characteristic] = CharacteristicRead(
                status = readStatus,
                value = value
                    ?.takeIf { readStatus == GattReadStatus.SUCCESS }
                    ?.copyOf(minOf(value.size, MAX_READ_BYTES))
                    ?.let(BinaryData::of),
            )
            readNext(gatt)
        }

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (control.terminal.get()) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    finish(GattEvent.Failed(snapshot.id, GattMappers.error(status)))
                    return
                }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        trySend(GattEvent.Connected(snapshot.id))
                        trySend(GattEvent.DiscoveringServices(snapshot.id))
                        if (!runCatching { gatt.discoverServices() }.getOrDefault(false)) {
                            finish(GattEvent.Failed(snapshot.id, GattError.PlatformFailure(-1, "discovery_not_started")))
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        finish(GattEvent.Disconnected(snapshot.id))
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (control.terminal.get()) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    finish(GattEvent.Failed(snapshot.id, GattMappers.error(status)))
                    return
                }
                val services = gatt.services.orEmpty()
                services.forEach { service -> trySend(GattEvent.ServiceDiscovered(service.toInfo())) }
                if (!consent.allowCharacteristicReads) {
                    complete(gatt)
                    return
                }
                services
                    .asSequence()
                    .flatMap { it.characteristics.orEmpty().asSequence() }
                    .filter { GattProperty.READ in GattMappers.properties(it.properties) }
                    .take(MAX_CHARACTERISTIC_READS)
                    .forEach(readQueue::addLast)
                readNext(gatt)
            }

            @Deprecated("Used on Android 12L and older")
            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    handleRead(gatt, characteristic, characteristic.value, status)
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                handleRead(gatt, characteristic, value, status)
            }

            override fun onServiceChanged(gatt: BluetoothGatt) {
                if (!control.terminal.get()) {
                    readQueue.clear()
                    reads.clear()
                    trySend(GattEvent.DiscoveringServices(snapshot.id))
                    if (!runCatching { gatt.discoverServices() }.getOrDefault(false)) {
                        finish(GattEvent.Failed(snapshot.id, GattError.PlatformFailure(-1, "rediscovery_not_started")))
                    }
                }
            }
        }

        trySend(GattEvent.Connecting(snapshot.id))
        runCatching { adapter.cancelDiscovery() }
        try {
            val remote = adapter.getRemoteDevice(address)
            @Suppress("DEPRECATION")
            val connection = remote.connectGatt(
                appContext,
                false,
                callback,
                BluetoothDevice.TRANSPORT_LE,
                BluetoothDevice.PHY_LE_1M_MASK,
            )
            control.gatt.set(connection)
            if (control.gatt.get() == null) {
                finish(GattEvent.Failed(snapshot.id, GattError.PlatformFailure(-1, "connect_returned_null")))
            }
        } catch (_: SecurityException) {
            finish(GattEvent.Failed(snapshot.id, GattError.PermissionDenied))
        } catch (error: IllegalArgumentException) {
            finish(GattEvent.Failed(snapshot.id, GattError.PlatformFailure(-1, "invalid_address")))
        } catch (error: RuntimeException) {
            finish(GattEvent.Failed(snapshot.id, GattError.PlatformFailure(-1, error.javaClass.simpleName)))
        }

        val cancelJob = launch {
            while (!control.cancel.get() && !control.terminal.get()) delay(CANCEL_POLL_MILLIS)
            if (control.cancel.get()) finish(GattEvent.Disconnected(snapshot.id))
        }
        val timeoutJob = launch {
            delay(EXPLORATION_TIMEOUT_MILLIS)
            finish(GattEvent.Failed(snapshot.id, GattError.Timeout))
        }

        awaitClose {
            cancelJob.cancel()
            timeoutJob.cancel()
            control.closeGatt()
            active.compareAndSet(control, null)
        }
    }

    private class GattControl {
        val gatt = AtomicReference<BluetoothGatt?>(null)
        val terminal = AtomicBoolean(false)
        val cancel = AtomicBoolean(false)

        @SuppressLint("MissingPermission")
        fun closeGatt() {
            gatt.getAndSet(null)?.let { connection ->
                runCatching { connection.disconnect() }
                runCatching { connection.close() }
            }
        }
    }

    private data class CharacteristicRead(
        val status: GattReadStatus,
        val value: BinaryData? = null,
    )

    private companion object {
        const val MAX_CHARACTERISTIC_READS = 64
        const val MAX_READ_BYTES = 4_096
        const val EXPLORATION_TIMEOUT_MILLIS = 30_000L
        const val CANCEL_POLL_MILLIS = 50L
    }
}
