package com.buk.bukin.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Why Bluetooth cannot be used right now, or [Ready] if it can.
 *
 * Deliberately carries no strings. This module owns the radio; the feature modules own the
 * sentences, because a sentence belongs next to the screen that shows it and the project
 * keeps exactly one strings.xml.
 */
sealed interface BleStatus {

    /** Everything the requested role needs is present and granted. */
    data object Ready : BleStatus

    /** No Bluetooth hardware at all. Nothing the user can do; say so and stop. */
    data object NoAdapter : BleStatus

    /** Hardware present, switched off. One tap away from working. */
    data object Disabled : BleStatus

    /** Runtime permissions not granted. [permissions] is what to ask for. */
    data class PermissionsMissing(val permissions: List<String>) : BleStatus

    /**
     * The chipset is central-only — it can listen but not broadcast. Host role only.
     * Bluetooth 4.1-and-lower hardware lands here and no amount of permission fixes it.
     */
    data object CannotAdvertise : BleStatus
}

/**
 * The preflight. Ordered, because the order is what makes the recovery message right: a
 * device with Bluetooth switched off should be told to switch it on, not told it lacks a
 * permission it was never asked for.
 *
 * Invariant 7 in `architecture.md`: every BLE failure has a distinct, actionable message.
 * There is no path here that returns "something went wrong".
 */
object BleCapability {

    /**
     * What to request for a role, for this OS version.
     *
     * On API 31+ the Bluetooth permissions stand alone and **no location grant is
     * involved**. Below 31 the platform had no such split and scanning was gated on
     * ACCESS_FINE_LOCATION, which is why the legacy branch exists at all.
     */
    fun requiredPermissions(forHost: Boolean): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            buildList {
                add(Manifest.permission.BLUETOOTH_SCAN)
                if (forHost) {
                    add(Manifest.permission.BLUETOOTH_ADVERTISE)
                    // The foreground service must hold this at the moment it starts.
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun check(context: Context, forHost: Boolean = false): BleStatus {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return BleStatus.NoAdapter

        if (!adapter.isEnabled) return BleStatus.Disabled

        val missing = requiredPermissions(forHost).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) return BleStatus.PermissionsMissing(missing)

        // Peripheral mode is hardware-gated, so this is checked last — it is the only
        // failure a user genuinely cannot resolve, and only the host role hits it.
        if (forHost && !adapter.isMultipleAdvertisementSupported) return BleStatus.CannotAdvertise

        return BleStatus.Ready
    }

    /**
     * [check] re-run on every adapter transition.
     *
     * This is what turns "the user switched Bluetooth off mid-session" from a hang into a
     * recovery screen. Without it the scan simply stops producing and the UI sits on
     * SCANNING forever, which is precisely the silent-failure mode this app exists to fix.
     *
     * It does not observe permission changes — Android has no broadcast for those — so the
     * caller re-collects after a permission result. Revoking a permission from Settings
     * kills the process anyway, so the gap is not reachable in practice.
     */
    fun statusFlow(context: Context, forHost: Boolean = false): Flow<BleStatus> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(check(context, forHost))
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(check(context, forHost))
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
