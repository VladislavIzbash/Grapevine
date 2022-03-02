package ru.vizbash.grapevine.service

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import dagger.hilt.android.AndroidEntryPoint
import ru.vizbash.grapevine.network.bluetooth.BluetoothDiscovery
import javax.inject.Inject

@AndroidEntryPoint
class ForegroundService : Service() {
    @Inject lateinit var grapevineService: GrapevineService
    @Inject lateinit var bluetoothDiscovery: BluetoothDiscovery

    private var started = false

    private var bluetoothHardwareEnabled = false
    private var bluetoothUserEnabled = false

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) {
                return
            }

            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)) {
                BluetoothAdapter.STATE_ON -> {
                    bluetoothHardwareEnabled = true
                    if (bluetoothUserEnabled) {
                        bluetoothDiscovery.start()
                    }
                }
                BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                    bluetoothHardwareEnabled = false
                    if (bluetoothUserEnabled) {
                        bluetoothDiscovery.stop()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            grapevineService.start()
            started = true
        }

        // TODO: Foreground notification

        return START_STICKY
    }

    inner class GrapevineBinder : Binder() {
        val grapevineService = this@ForegroundService.grapevineService

        fun setBluetoothEnabled(enabled: Boolean) {
            if (bluetoothHardwareEnabled) {
                if (enabled) {
                    bluetoothDiscovery.start()
                } else {
                    bluetoothDiscovery.stop()
                }
            }

            bluetoothUserEnabled = enabled
        }
    }

    override fun onBind(intent: Intent?) = GrapevineBinder()

    override fun onDestroy() {
        grapevineService.stop()
    }
}