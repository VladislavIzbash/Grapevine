package ru.vizbash.grapevine.service.foreground

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import ru.vizbash.grapevine.network.dispatch.NetworkController
import javax.inject.Inject

@AndroidEntryPoint
class ForegroundService : Service() {
    @Inject lateinit var networkController: NetworkController
    @Inject lateinit var transportController: TransportController
    @Inject lateinit var notificationSender: NotificationSender

    companion object {
        private const val TAG = "ForegroundService"

        const val ACTION_ENABLE_TRANSPORT = "ru.vizbash.grapevine.action.ENABLE_TRANSPORT"
        const val ACTION_GET_TRANSPORT_STATE = "ru.vizbash.grapevine.action.ACTION_GET_TRANSPORT_STATE"

        const val ACTION_TRANSPORT_STATE_CHANGED
            = "ru.vizbash.grapevine.action.ACTION_TRANSPORT_STATE_CHANGED"
        const val ACTION_TRANSPORT_HARDWARE_STATE_CHANGED
            = "ru.vizbash.grapevine.action.ACTION_TRANSPORT_HARDWARE_STATE_CHANGED"

        const val EXTRA_STATE = "state"

        const val EXTRA_TRANSPORT_TYPE = "transport_type"
        const val TRANSPORT_WIFI = 0
        const val TRANSPORT_BLUETOOTH = 1
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        transportController.start()
        notificationSender.start(coroutineScope) { notifId, notif ->
            startForeground(notifId, notif)
        }
        networkController.start()

        Log.i(TAG, "Started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Received ${intent?.action}")

        when (intent?.action) {
            ACTION_ENABLE_TRANSPORT -> {
                val enabled = intent.getBooleanExtra(EXTRA_STATE, false)

                when (intent.getIntExtra(EXTRA_TRANSPORT_TYPE, -1)) {
                    TRANSPORT_WIFI -> transportController.wifiUserEnabled = enabled
                    TRANSPORT_BLUETOOTH -> transportController.btUserEnabled = enabled
                }
            }
            ACTION_GET_TRANSPORT_STATE -> transportController.broadcastState()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        coroutineScope.cancel()
        transportController.stop()
        notificationSender.stop()

        Log.i(TAG, "Destroyed")
    }
}