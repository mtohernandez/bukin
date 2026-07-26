package com.buk.bukin.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether this device can actually reach the internet right now.
 *
 * The platform already tracks this, so nothing here polls or pings. `registerDefault-
 * NetworkCallback` reports the network the app would use, which is the only one that
 * matters.
 *
 * [NetworkCapabilities.NET_CAPABILITY_VALIDATED] rather than "is Wi-Fi connected": being
 * associated to an access point is not the same as having a working connection. A phone
 * sitting on a captive portal, or one that has just reconnected and is still being probed,
 * is associated and useless. Validated is the platform's own answer to "did a real request
 * to the internet succeed", and it is what makes an offline screen clear itself at the
 * moment connectivity genuinely returns rather than the moment the radio associates.
 */
object NetworkMonitor {

    fun isOnline(context: Context): Flow<Boolean> = callbackFlow {
        val manager = context.applicationContext
            .getSystemService(ConnectivityManager::class.java)

        if (manager == null) {
            // No connectivity service at all. Claiming "offline" would permanently disable
            // saving; let the request itself be the judge instead.
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                trySend(
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }

        // Seed from the current state: the callback only fires on change, and a screen
        // opened while already online would otherwise wait for a transition that never comes.
        val actual = manager.activeNetwork?.let(manager::getNetworkCapabilities)
        trySend(
            actual?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false,
        )

        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
