package ru.vizbash.grapevine.service.foreground

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import ru.vizbash.grapevine.network.dispatch.GrapevineNetwork
import javax.inject.Inject

@AndroidEntryPoint
class ForegroundService : Service() {
    @Inject lateinit var grapevineNetwork: GrapevineNetwork
    @Inject lateinit var transportController: TransportController
    @Inject lateinit var notificationSender: NotificationSender

    companion object {
        private const val TAG = "ForegroundService"

        const val ACTION_WIFI_HARDWARE_STATE_CHANGED
            = "ru.vizbash.grapevine.action.WIFI_HARDWARE_STATE_CHANGED"
        const val ACTION_WIFI_STATE_CHANGED
            = "ru.vizbash.grapevine.action.WIFI_STATE_CHANGED"
        const val ACTION_BLUETOOTH_HARDWARE_STATE_CHANGED
            = "ru.vizbash.grapevine.action.BLUETOOTH_HARDWARE_STATE_CHANGED"
        const val ACTION_BLUETOOTH_STATE_CHANGED
            = "ru.vizbash.grapevine.action.BLUETOOTH_STATE_CHANGED"
        const val ACTION_ENABLE_WIFI = "ru.vizbash.grapevine.action.ENABLE_WIFI"
        const val ACTION_ENABLE_BLUETOOTH = "ru.vizbash.grapevine.action.ENABLE_BLUETOOTH"
        const val EXTRA_STATE = "state"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        transportController.start(coroutineScope)
        notificationSender.start(coroutineScope) { notifId, notif ->
            startForeground(notifId, notif)
        }
        grapevineNetwork.start()


        Log.i(TAG, "Started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Received ${intent?.action}")

        when (intent?.action) {
            ACTION_ENABLE_WIFI -> {
                transportController.wifiUserEnabled.value = intent.getBooleanExtra(EXTRA_STATE, false)
            }
            ACTION_ENABLE_BLUETOOTH -> {
                transportController.bluetoothUserEnabled.value = intent.getBooleanExtra(EXTRA_STATE, false)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        coroutineScope.cancel()
        transportController.stop()
        notificationSender.stop()

        Log.i(TAG, "Destroyed")
    }
}