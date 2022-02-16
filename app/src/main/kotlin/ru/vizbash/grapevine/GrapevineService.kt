package ru.vizbash.grapevine

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import ru.vizbash.grapevine.network.NetworkController
import javax.inject.Inject

@AndroidEntryPoint
class GrapevineService : Service() {
    @Inject lateinit var controller: NetworkController

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        controller.start()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        controller.stop()
    }
}