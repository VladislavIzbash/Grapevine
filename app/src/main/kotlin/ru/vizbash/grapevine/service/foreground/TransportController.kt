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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.network.transport.BluetoothTransport
import ru.vizbash.grapevine.network.transport.WifiTransport
import ru.vizbash.grapevine.service.foreground.ForegroundService.Companion.ACTION_BLUETOOTH_HARDWARE_STATE_CHANGED
import ru.vizbash.grapevine.service.foreground.ForegroundService.Companion.ACTION_BLUETOOTH_STATE_CHANGED
import ru.vizbash.grapevine.service.foreground.ForegroundService.Companion.ACTION_WIFI_HARDWARE_STATE_CHANGED
import ru.vizbash.grapevine.service.foreground.ForegroundService.Companion.ACTION_WIFI_STATE_CHANGED
import javax.inject.Inject

@ServiceScoped
class TransportController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothTransport: BluetoothTransport,
    private val wifiTransport: WifiTransport,
) {
    companion object {
        private const val WIFI_ENABLED_KEY = "wifi_enabled"
        private const val BLUETOOTH_ENABLED_KEY = "bluetooth_enabled"
    }

    private val sharedPrefs = context.getSharedPreferences("transport", Context.MODE_PRIVATE)

    val wifiUserEnabled: MutableStateFlow<Boolean>
    private val wifiHardwareEnabled = MutableStateFlow(false)

    val bluetoothUserEnabled: MutableStateFlow<Boolean>
    private val bluetoothHardwareEnabled = MutableStateFlow(false)

    private val broadcastManager = LocalBroadcastManager.getInstance(context)

    private val hardwareStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                        BluetoothAdapter.STATE_ON -> bluetoothHardwareEnabled.value = true
                        BluetoothAdapter.STATE_OFF -> bluetoothHardwareEnabled.value = false
                    }
                }
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    when (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)) {
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED -> wifiHardwareEnabled.value = true
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED -> wifiHardwareEnabled.value = false
                    }
                }
                LocationManager.MODE_CHANGED_ACTION -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val locationManager = context.getSystemService(Service.LOCATION_SERVICE)
                                as LocationManager
                        wifiHardwareEnabled.value = locationManager.isLocationEnabled
                        bluetoothHardwareEnabled.value = locationManager.isLocationEnabled
                    }
                }
            }
        }
    }

    private val _statusText = MutableStateFlow(getStatusText())
    val statusText = _statusText.asStateFlow()

    init {
        val wifiUserEnabled = sharedPrefs.getBoolean(WIFI_ENABLED_KEY, false)
        this.wifiUserEnabled = MutableStateFlow(wifiUserEnabled)

        bluetoothHardwareEnabled.value = bluetoothTransport.isAdapterEnabled

        val bluetoothUserEnabled = sharedPrefs.getBoolean(BLUETOOTH_ENABLED_KEY, false)
        this.bluetoothUserEnabled = MutableStateFlow(bluetoothUserEnabled)
    }

    fun start(coroutineScope: CoroutineScope) {
        val intentFilter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        context.registerReceiver(hardwareStateReceiver, intentFilter)

        coroutineScope.launch {
            wifiHardwareEnabled.collect {
                sendStateIntent(it, ACTION_WIFI_HARDWARE_STATE_CHANGED)
            }
        }
        coroutineScope.launch {
            bluetoothHardwareEnabled.collect {
                sendStateIntent(it, ACTION_BLUETOOTH_HARDWARE_STATE_CHANGED)
            }
        }

        coroutineScope.launch {
            wifiUserEnabled
                .combine(wifiHardwareEnabled, Boolean::and)
                .collect { state ->
                    if (state) {
                        wifiTransport.start()
                    } else {
                        wifiTransport.stop()
                    }
                    sendStateIntent(state, ACTION_WIFI_STATE_CHANGED)
                    _statusText.value = getStatusText()
                }

        }
        coroutineScope.launch {
            bluetoothUserEnabled
                .combine(bluetoothHardwareEnabled, Boolean::and)
                .collect { state ->
                    if (state) {
                        bluetoothTransport.start()
                    } else {
                        bluetoothTransport.stop()
                    }
                    sendStateIntent(state, ACTION_BLUETOOTH_STATE_CHANGED)
                    _statusText.value = getStatusText()
                }

        }
    }

    private fun sendStateIntent(state: Boolean, action: String) {
        val intent = Intent(action).apply {
            putExtra(ForegroundService.EXTRA_STATE, state)
        }
        broadcastManager.sendBroadcast(intent)
    }

    fun stop() {
        context.unregisterReceiver(hardwareStateReceiver)

        sharedPrefs.edit()
            .putBoolean(WIFI_ENABLED_KEY, wifiUserEnabled.value)
            .putBoolean(BLUETOOTH_ENABLED_KEY, bluetoothUserEnabled.value)
            .apply()
    }

    private fun getStatusText(): String {
        val bluetoothStatus = if (bluetoothUserEnabled.value && bluetoothHardwareEnabled.value) {
            R.string.on
        } else {
            R.string.off
        }

        val wifiStatus = if (wifiUserEnabled.value && wifiHardwareEnabled.value) {
            R.string.on
        } else {
            R.string.off
        }

        return context.getString(
            R.string.status_text,
            context.getString(bluetoothStatus),
            context.getString(wifiStatus),
        )
    }
}