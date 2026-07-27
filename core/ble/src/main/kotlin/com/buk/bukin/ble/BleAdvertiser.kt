package com.buk.bukin.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import com.buk.bukin.domain.crypto.AdvertisementPayload
import com.buk.bukin.domain.crypto.RotatingCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** What the host's radio is doing. */
sealed interface AdvertisingEvent {

    /** Live, with the code currently on the air and when this window expires. */
    data class Broadcasting(
        val instanciaId: Int,
        val code: ByteArray,
        val counter: Long,
        val windowEndsAtEpochMillis: Long,
    ) : AdvertisingEvent {
        override fun equals(other: Any?): Boolean =
            this === other || (
                other is Broadcasting &&
                    instanciaId == other.instanciaId &&
                    code.contentEquals(other.code) &&
                    counter == other.counter &&
                    windowEndsAtEpochMillis == other.windowEndsAtEpochMillis
                )

        override fun hashCode(): Int {
            var result = instanciaId
            result = 31 * result + code.contentHashCode()
            result = 31 * result + counter.hashCode()
            result = 31 * result + windowEndsAtEpochMillis.hashCode()
            return result
        }
    }

    /** `AdvertiseCallback.onStartFailure`, with the platform's code for translation upstairs. */
    data class Failed(val errorCode: Int) : AdvertisingEvent
}

/**
 * The host side. Broadcasts a rotating code and never connects to anything.
 *
 * Non-connectable with no scan response, which keeps the packet `ADV_NONCONN_IND`. That
 * matters at scale: a scan response would invite a `SCAN_REQ` from every active scanner in
 * range, turning 300 silent listeners into 300 transmitters fighting over three advertising
 * channels. One-way broadcast is the reason this design reaches auditorium size.
 */
object BleAdvertiser {

    /** No adapter, or it is off — distinct from the platform's own failure codes, which are 1..5. */
    const val ERROR_NO_ADVERTISER: Int = -1

    /** `startAdvertising` threw SecurityException: BLUETOOTH_ADVERTISE was revoked under us. */
    const val ERROR_PERMISSION_DENIED: Int = -2

    /**
     * Advertises [instanciaId] with a code that re-derives every 30 seconds, until the
     * collector goes away.
     *
     * A plain `flow` rather than `callbackFlow`: each window is one request/response
     * against `AdvertiseCallback`, not a stream, so `suspendCancellableCoroutine` fits and
     * the `finally` makes the teardown impossible to miss. The rule that matters — never
     * leak a registration — is kept either way.
     *
     * [clockOffsetSeconds] is 0 today; session 3 fills it from the server when the session
     * opens, which is what stops a host phone with automatic time switched off from
     * generating codes that are rejected 100% of the time.
     */
    fun advertise(
        context: Context,
        key: ByteArray,
        instanciaId: Int,
        clockOffsetSeconds: Long = 0,
    ): Flow<AdvertisingEvent> = flow {
        val advertiser = context.getSystemService(BluetoothManager::class.java)
            ?.adapter
            ?.bluetoothLeAdvertiser

        if (advertiser == null) {
            emit(AdvertisingEvent.Failed(ERROR_NO_ADVERTISER))
            return@flow
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        var current: AdvertiseCallback? = null
        try {
            while (true) {
                val nowMillis = System.currentTimeMillis()
                val counter = RotatingCode.counterFor(nowMillis / 1000, clockOffsetSeconds)
                val code = RotatingCode.derive(key, instanciaId, counter)
                val uuid = AdvertisementPayload.encode(instanciaId, code)

                val data = AdvertiseData.Builder()
                    // Including the device name is the single most common cause of
                    // ADVERTISE_FAILED_DATA_TOO_LARGE, and it would leak the host's phone
                    // name to the whole room for no benefit.
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addServiceUuid(ParcelUuid(uuid))
                    .build()

                current?.let { runCatching { advertiser.stopAdvertising(it) } }

                val event = suspendCancellableCoroutine { continuation ->
                    val callback = object : AdvertiseCallback() {
                        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    AdvertisingEvent.Broadcasting(
                                        instanciaId = instanciaId,
                                        code = code,
                                        counter = counter,
                                        windowEndsAtEpochMillis = windowEndMillis(counter, clockOffsetSeconds),
                                    ),
                                )
                            }
                        }

                        override fun onStartFailure(errorCode: Int) {
                            if (continuation.isActive) {
                                continuation.resume(AdvertisingEvent.Failed(errorCode))
                            }
                        }
                    }
                    current = callback
                    try {
                        advertiser.startAdvertising(settings, data, callback)
                    } catch (e: SecurityException) {
                        current = null
                        if (continuation.isActive) {
                            continuation.resume(AdvertisingEvent.Failed(ERROR_PERMISSION_DENIED))
                        }
                    }
                }

                emit(event)
                // A start failure is a property of the device or the data, not of this
                // window — retrying every 30s would just spin and hide the real message.
                if (event is AdvertisingEvent.Failed) return@flow

                // Sleep to the window boundary, not a flat 30s. Drifting off the boundary
                // would eventually put this host and the Mac beacon on different codes.
                delay((windowEndMillis(counter, clockOffsetSeconds) - System.currentTimeMillis()).coerceAtLeast(1))
            }
        } finally {
            current?.let { runCatching { advertiser.stopAdvertising(it) } }
        }
    }

    private fun windowEndMillis(counter: Long, clockOffsetSeconds: Long): Long =
        ((counter + 1) * RotatingCode.WINDOW_SECONDS - clockOffsetSeconds) * 1000
}
