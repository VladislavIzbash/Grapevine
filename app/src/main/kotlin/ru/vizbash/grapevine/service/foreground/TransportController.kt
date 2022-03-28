package ru.vizbash.grapevine.service.foreground

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.flow.*
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.network.transport.BluetoothTransport
import ru.vizbash.grapevine.network.transport.WifiTransport
import ru.vizbash.grapevine.service.foreground.ForegroundService.Companion.TRANSPORT_BLUETOOTH
import ru.vizbash.grapevine.service.foreground.ForegroundService.Companion.TRANSPORT_WIFI
import javax.inject.Inject

@ServiceScoped
class TransportController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothTransport: BluetoothTransport,
    private val wifiTransport: WifiTransport,
) {
    companion object {
        private const val WIFI_ENABLED_KEY = "wifi_enabled"
        private const val BT_ENABLED_KEY = "bluetooth_enabled"
    }

    private val sharedPrefs = context.getSharedPreferences("transport", Context.MODE_PRIVATE)

    private var locationEnabled = true

    var wifiUserEnabled = sharedPrefs.getBoolean(WIFI_ENABLED_KEY, false)
        set(value) {
            field = value
            updateState()
            sharedPrefs.edit().putBoolean(WIFI_ENABLED_KEY, value).apply()
        }
    private var wifiHardwareEnabled = false
    val wifiEnabled get() = wifiUserEnabled && wifiHardwareEnabled && locationEnabled

    var btUserEnabled = sharedPrefs.getBoolean(BT_ENABLED_KEY, false)
        set(value) {
            field = value
            updateState()
            sharedPrefs.edit().putBoolean(BT_ENABLED_KEY, value).apply()
        }
    private var btHardwareEnabled = bluetoothTransport.isAdapterEnabled
    val btEnabled get() = btUserEnabled && btHardwareEnabled && locationEnabled

    private var onStateChanged: () -> Unit = {}

    private val broadcastManager = LocalBroadcastManager.getInstance(context)

    private val hardwareStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                        BluetoothAdapter.STATE_ON -> btHardwareEnabled = true
                        BluetoothAdapter.STATE_OFF -> btHardwareEnabled = false
                    }
                    updateState()
                }
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    when (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)) {
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED -> wifiHardwareEnabled = true
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED -> wifiHardwareEnabled = false
                    }
                    updateState()
                }
                LocationManager.MODE_CHANGED_ACTION -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val locationManager = context.getSystemService(Service.LOCATION_SERVICE)
                                as LocationManager
                        locationEnabled = locationManager.isLocationEnabled
                    }
                    updateState()
                }
            }
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val locationManager = context.getSystemService(Service.LOCATION_SERVICE)
                    as LocationManager

            locationEnabled = locationManager.isLocationEnabled
        }

        updateState()
    }

    fun start() {
        val intentFilter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        context.registerReceiver(hardwareStateReceiver, intentFilter)
    }

    fun setOnStateChanged(cb: () -> Unit) {
        onStateChanged = cb
    }

    private fun updateState() {
        if (btEnabled) {
            bluetoothTransport.start()
        } else {
            bluetoothTransport.stop()
        }
        if (wifiEnabled) {
            wifiTransport.start()
        } else {
            wifiTransport.stop()
        }

        onStateChanged()
        broadcastState()
    }

    fun broadcastState() {
        sendStateIntent(TRANSPORT_BLUETOOTH, btEnabled, false)
        sendStateIntent(TRANSPORT_BLUETOOTH, btHardwareEnabled, true)

        sendStateIntent(TRANSPORT_WIFI, wifiEnabled, false)
        sendStateIntent(TRANSPORT_WIFI, wifiHardwareEnabled, true)
    }

    private fun sendStateIntent(type: Int, state: Boolean, isHw: Boolean) {
        val action = if (isHw) {
            ForegroundService.ACTION_TRANSPORT_HARDWARE_STATE_CHANGED
        } else {
            ForegroundService.ACTION_TRANSPORT_STATE_CHANGED
        }

        val intent = Intent(action).apply {
            putExtra(ForegroundService.EXTRA_TRANSPORT_TYPE, type)
            putExtra(ForegroundService.EXTRA_STATE, state)
        }
        broadcastManager.sendBroadcast(intent)
    }

    fun stop() {
        context.unregisterReceiver(hardwareStateReceiver)
    }
}