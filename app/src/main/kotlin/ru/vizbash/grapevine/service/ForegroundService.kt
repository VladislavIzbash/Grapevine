package ru.vizbash.grapevine.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.network.bluetooth.BluetoothDiscovery
import ru.vizbash.grapevine.ui.main.MainActivity
import javax.inject.Inject

@AndroidEntryPoint
class ForegroundService : Service() {
    @Inject lateinit var grapevineService: GrapevineService
    @Inject lateinit var bluetoothDiscovery: BluetoothDiscovery

    private var started = false

    private var bluetoothHardwareEnabled = false
    private var bluetoothUserEnabled = true

    private lateinit var foregroundNotification: NotificationCompat.Builder

    companion object {
        private const val FOREGROUND_CHANNEL_ID = "status_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 10
    }

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
                    updateForegroundNotification()
                }
                BluetoothAdapter.STATE_OFF -> {
                    bluetoothHardwareEnabled = false
                    if (bluetoothUserEnabled) {
                        bluetoothDiscovery.stop()
                    }
                    updateForegroundNotification()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)

        createForegroundChannel()

        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        foregroundNotification = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_notification)
            .setContentTitle(getString(R.string.grapevine_service))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }

    private fun createForegroundChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val statusChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                getString(R.string.status_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.status_channel_desc)
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(statusChannel)
        }
    }

    private fun getForegroundText(): String {
        val bluetoothStatus = getString(if (bluetoothUserEnabled && bluetoothHardwareEnabled) {
            R.string.on
        } else {
            R.string.off
        })

        return getString(R.string.status_text, bluetoothStatus, getString(R.string.off))
    }

    private fun updateForegroundNotification() {
        foregroundNotification.setContentText(getForegroundText())
        NotificationManagerCompat.from(this).notify(
            FOREGROUND_NOTIFICATION_ID,
            foregroundNotification.build(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            grapevineService.start()
            started = true
        }

        bluetoothHardwareEnabled = bluetoothDiscovery.isAdapterEnabled

        foregroundNotification.setContentText(getForegroundText())
        startForeground(FOREGROUND_NOTIFICATION_ID, foregroundNotification.build())

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
            updateForegroundNotification()
        }
    }

    override fun onBind(intent: Intent?) = GrapevineBinder()

    override fun onDestroy() {
        super.onDestroy()
        bluetoothDiscovery.stop()
        grapevineService.stop()
        unregisterReceiver(bluetoothStateReceiver)
    }
}