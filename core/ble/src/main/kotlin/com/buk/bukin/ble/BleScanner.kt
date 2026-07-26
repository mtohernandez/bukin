package com.buk.bukin.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.os.SystemClock
import com.buk.bukin.domain.crypto.AdvertisementPayload
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** What the radio heard. Typed — no `ScanResult` or `BluetoothDevice` crosses this boundary. */
sealed interface ScanEvent {

    /**
     * A well-formed BukIn advertisement. [code] is opaque here: the collaborator holds no
     * key and cannot validate it, only relay it. Postgres decides in session 3 — invariant
     * 4 in `architecture.md`.
     *
     * [seenAtElapsedRealtime] uses the monotonic clock on purpose; wall time can jump.
     */
    data class Sighting(
        val instanciaId: Int,
        val code: ByteArray,
        val rssi: Int,
        val seenAtElapsedRealtime: Long,
    ) : ScanEvent {
        override fun equals(other: Any?): Boolean =
            this === other || (
                other is Sighting &&
                    instanciaId == other.instanciaId &&
                    code.contentEquals(other.code) &&
                    rssi == other.rssi &&
                    seenAtElapsedRealtime == other.seenAtElapsedRealtime
                )

        override fun hashCode(): Int {
            var result = instanciaId
            result = 31 * result + code.contentHashCode()
            result = 31 * result + rssi
            result = 31 * result + seenAtElapsedRealtime.hashCode()
            return result
        }
    }

    /** `ScanCallback.onScanFailed`. Surfaced, never swallowed — a silent scan is the worst outcome. */
    data class Failed(val errorCode: Int) : ScanEvent
}

/**
 * The collaborator side. Listens; never transmits.
 *
 * That asymmetry is the whole scale argument: a passive receiver costs the host's radio
 * nothing, so 5 phones in the room and 300 phones in the room are identical from the
 * broadcaster's point of view.
 */
object BleScanner {

    /**
     * Emits every BukIn advertisement in range until the collector goes away.
     *
     * `awaitClose` stops the scan. That is not decoration — a leaked scan callback drains
     * the battery and eventually gets the app throttled by the platform, and it is the
     * classic way this kind of code goes wrong.
     */
    fun scan(context: Context): Flow<ScanEvent> = callbackFlow {
        val scanner = context.getSystemService(BluetoothManager::class.java)
            ?.adapter
            ?.bluetoothLeScanner

        if (scanner == null) {
            // No adapter, or it is off. BleCapability is what explains that to the user;
            // this flow just ends rather than pretending to listen.
            close()
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val uuids = result.scanRecord?.serviceUuids ?: return
                uuids.forEach { parcel ->
                    AdvertisementPayload.decode(parcel.uuid)?.let { decoded ->
                        trySend(
                            ScanEvent.Sighting(
                                instanciaId = decoded.instanciaId,
                                code = decoded.code,
                                rssi = result.rssi,
                                seenAtElapsedRealtime = SystemClock.elapsedRealtime(),
                            ),
                        )
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                trySend(ScanEvent.Failed(errorCode))
            }
        }

        // Match only the 4-byte magic. Filtering happens in the Bluetooth controller rather
        // than in app code, which keeps the CPU asleep for every unrelated advertisement in
        // a crowded room — and there are a lot of them.
        val filter = ScanFilter.Builder()
            .setServiceUuid(
                ParcelUuid(AdvertisementPayload.FILTER),
                ParcelUuid(AdvertisementPayload.MASK),
            )
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        var started = false
        try {
            scanner.startScan(listOf(filter), settings, callback)
            started = true
        } catch (e: SecurityException) {
            // Permission revoked between the preflight and here. Report, do not crash.
            trySend(ScanEvent.Failed(ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED))
            close(e)
        }

        awaitClose {
            if (started) {
                runCatching { scanner.stopScan(callback) }
            }
        }
    }
}
