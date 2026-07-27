package com.buk.bukin.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the host session is doing, as the UI needs to see it. */
sealed interface HostState {
    data object Stopped : HostState
    data object Starting : HostState
    data class Broadcasting(
        val instanciaId: Int,
        val code: ByteArray,
        val counter: Long,
        val windowEndsAtEpochMillis: Long,
    ) : HostState {
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

    data class Failed(val errorCode: Int) : HostState
}

/**
 * Where the host's live state lives, so the screen can read it without binding to the
 * service.
 *
 * ponytail: process-global. The service is a singleton and there is exactly one host
 * session at a time, so a binder and a connection callback would be forty lines of
 * ceremony around a value that has one writer. Swap for binding if a second host surface
 * ever needs its own session.
 */
object HostSession {
    private val _state = MutableStateFlow<HostState>(HostState.Stopped)
    val state: StateFlow<HostState> = _state.asStateFlow()

    /**
     * The correction currently applied to this device's clock, in seconds.
     *
     * Published so the diagnostics screen can show it. A host phone with automatic time
     * switched off generates codes that are rejected every single time, and the beacon looks
     * perfectly healthy while it happens — this number is the only visible symptom.
     */
    private val _clockOffsetSeconds = MutableStateFlow(0L)
    val clockOffsetSeconds: StateFlow<Long> = _clockOffsetSeconds.asStateFlow()

    internal fun set(state: HostState) {
        _state.value = state
    }

    internal fun setClockOffset(seconds: Long) {
        _clockOffsetSeconds.value = seconds
    }
}

/**
 * Keeps the host broadcasting when the screen locks.
 *
 * The `connectedDevice` type in the manifest is what makes that legal on Android 14+;
 * without it the platform kills the service and the room silently stops hearing the host
 * mid-class, which is the failure nobody would diagnose on demo day.
 */
class HostAdvertisingService : Service() {

    // A Service has no viewModelScope. Field + cancel in onDestroy is the structured
    // equivalent here: the scope's lifetime is exactly the service's.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val key = intent?.getByteArrayExtra(EXTRA_KEY)
        val instanciaId = intent?.getIntExtra(EXTRA_INSTANCIA_ID, 0) ?: 0
        if (key == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // The Spanish arrives from the feature module. This module never holds a string —
        // the project keeps one strings.xml and it lives next to the screens.
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(title, text),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )

        val clockOffsetSeconds = intent.getLongExtra(EXTRA_CLOCK_OFFSET, 0)
        HostSession.setClockOffset(clockOffsetSeconds)

        HostSession.set(HostState.Starting)
        scope.launch {
            BleAdvertiser.advertise(
                context = applicationContext,
                key = key,
                instanciaId = instanciaId,
                clockOffsetSeconds = clockOffsetSeconds,
            ).collect { event ->
                HostSession.set(
                    when (event) {
                        is AdvertisingEvent.Broadcasting -> HostState.Broadcasting(
                            instanciaId = event.instanciaId,
                            code = event.code,
                            counter = event.counter,
                            windowEndsAtEpochMillis = event.windowEndsAtEpochMillis,
                        )
                        is AdvertisingEvent.Failed -> HostState.Failed(event.errorCode)
                    },
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        HostSession.set(HostState.Stopped)
        super.onDestroy()
    }

    private fun buildNotification(title: String, text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, title, NotificationManager.IMPORTANCE_LOW),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            // A platform drawable: no asset to ship, and it says "Bluetooth" at a glance.
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "bukin_host"
        private const val NOTIFICATION_ID = 1

        private const val EXTRA_KEY = "key"
        private const val EXTRA_INSTANCIA_ID = "instancia_id"
        private const val EXTRA_CLOCK_OFFSET = "clock_offset"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"

        fun start(
            context: Context,
            key: ByteArray,
            instanciaId: Int,
            notificationTitle: String,
            notificationText: String,
            clockOffsetSeconds: Long = 0,
        ) {
            val intent = Intent(context, HostAdvertisingService::class.java).apply {
                putExtra(EXTRA_KEY, key)
                putExtra(EXTRA_INSTANCIA_ID, instanciaId)
                putExtra(EXTRA_CLOCK_OFFSET, clockOffsetSeconds)
                putExtra(EXTRA_TITLE, notificationTitle)
                putExtra(EXTRA_TEXT, notificationText)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HostAdvertisingService::class.java))
        }
    }
}
