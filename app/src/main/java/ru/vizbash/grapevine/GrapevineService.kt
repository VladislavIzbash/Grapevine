package ru.vizbash.grapevine

import android.app.Service
import android.content.Intent
import android.os.Binder
import dagger.hilt.android.AndroidEntryPoint
import ru.vizbash.grapevine.network.transport.BluetoothService
import javax.inject.Inject

@AndroidEntryPoint
class GrapevineService : Service() {
    @Inject lateinit var bluetoothService: BluetoothService

    override fun onBind(intent: Intent?) = GrapevineBinder()

    inner class GrapevineBinder : Binder() {
        fun getService(): GrapevineService = this@GrapevineService
    }
}