package ru.vizbash.grapevine.network.bluetooth

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothService : Service() {
    @Inject lateinit var discovery: TestDiscovery

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        discovery.start()

        return START_STICKY
    }

    override fun onDestroy() {
        discovery.stop()
    }
}