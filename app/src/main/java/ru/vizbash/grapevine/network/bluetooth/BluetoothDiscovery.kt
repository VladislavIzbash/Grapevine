package ru.vizbash.grapevine.network.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import ru.vizbash.grapevine.TAG
import ru.vizbash.grapevine.network.Neighbor
import ru.vizbash.grapevine.network.Router
import ru.vizbash.grapevine.network.SourceType
import ru.vizbash.grapevine.network.messages.direct.DirectMessage
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import javax.inject.Inject
import kotlin.concurrent.thread

@SuppressLint("MissingPermission", "HardwareIds")
@ServiceScoped
class BluetoothDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val router: Router,
) {
    companion object {
        private const val BONDED_SCAN_INTERVAL_MS = 10000L

        private const val SERVICE_NAME = "Grapevine"
        private val SERVICE_UUID = UUID.fromString("f8393dd1-32a9-49ef-9768-749bafed80ed")
    }

    private val adapter by lazy {
        context.getSystemService(BluetoothManager::class.java).adapter
    }

    private val myAddress by lazy {
        macToInt(adapter.address)
    }

    private enum class State { STOPPED, SEARCHING, CONNECTED }

    @Volatile
    private var state = State.STOPPED

    @Volatile
    private var neighbor: BluetoothNeighbor? = null

    private val searchThreads = mutableListOf<Thread>()

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_UUID -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val uuids = intent.getParcelableArrayListExtra<ParcelUuid>(BluetoothDevice.EXTRA_UUID)

                    println("${device!!.name} has ${uuids!!.map(ParcelUuid::getUuid)}")
                }
            }
        }
    }

    private fun changeState(newState: State) {
        val oldState = state
        state = newState

        when (oldState) {
            State.STOPPED-> when (state) {
                State.SEARCHING -> {
//                    context.registerReceiver(scanReceiver, IntentFilter(BluetoothDevice.ACTION_UUID))
                    enterDiscoverState()
                }
                State.CONNECTED -> throw IllegalStateException()
                State.STOPPED -> {}
            }
            State.SEARCHING -> when (state) {
                State.STOPPED -> {
//                    context.unregisterReceiver(scanReceiver)
                    searchThreads.forEach(Thread::join)
                    searchThreads.clear()
                }
                State.CONNECTED -> {
//                    context.unregisterReceiver(scanReceiver)


                    enterConnectedState()
                }
                State.SEARCHING -> {}
            }
            State.CONNECTED -> when (state) {
                State.STOPPED -> {
                    neighbor!!.disconnectCb()
                    neighbor!!.socket.close()
                    neighbor = null
                }
                State.SEARCHING -> {
                    neighbor!!.disconnectCb()
                    neighbor!!.socket.close()
                    neighbor = null
                    enterDiscoverState()
                }
                State.CONNECTED -> {}
            }
        }
    }

    fun start() {
        if (!adapter.isEnabled) {
            Log.i(TAG, "Bluetooth is not enabled")
            return
        }

        if (state == State.STOPPED) {
            Log.i(TAG, "Starting bluetooth dicovery")
            changeState(State.SEARCHING)
        }
    }

    fun stop() {
        changeState(State.STOPPED)
    }

    private fun enterDiscoverState() {
        val serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(
            SERVICE_NAME,
            SERVICE_UUID,
        )

        searchThreads += thread {
            while (state == State.SEARCHING) {
                scanBondedDevices()?.let {
                    neighbor = it
                    router.addNeighbor(it)
                    changeState(State.CONNECTED)
                }
            }
            serverSocket.close()
        }.apply {
            name = "BtDiscoveryThread"
        }

        searchThreads += thread {
            try {
                val socket = serverSocket.accept()
                neighbor = BluetoothNeighbor(socket)

                Log.i(TAG, "Accepted ${socket.remoteDevice.address} (${socket.remoteDevice.name})")
                router.addNeighbor(neighbor!!)

                changeState(State.CONNECTED)
            } catch (e: IOException) {
            }
            serverSocket.close()
        }.apply {
            name = "BtAcceptThread"
        }
    }

    private fun scanBondedDevices(): BluetoothNeighbor? {
        for (device in adapter.bondedDevices) {
            if (state != State.SEARCHING) {
                return null
            }

            val deviceAddress = macToInt(device.address)
            if (myAddress > deviceAddress) {
                Log.d(TAG, "Waiting ${device.address} (${device.name}) to connect")
                continue
            }

            Log.d(TAG, "Connecting to bonded device ${device.address} (${device.name})")

            val socket = device.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID)
            try {
                socket.connect()
                Log.i(TAG, "Connected to ${device.address} (${device.name})")
                return BluetoothNeighbor(socket)
            } catch (e: IOException) {
                socket.close()
            }
        }

        return null
    }

    private fun enterConnectedState() {
        thread {
            searchThreads.forEach(Thread::join)
            searchThreads.clear()

            val buffer = ByteArray(1024)

            try {
                while (state == State.CONNECTED) {
                    val size = neighbor!!.socket.inputStream.read(buffer)

                    val msg = DirectMessage.parseFrom(buffer.sliceArray(0 until size))
                    neighbor!!.receiveCb(msg)
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    val addr = neighbor!!.socket.remoteDevice.address
                    Log.d(TAG, "$addr closed connection: ${e.message}")
                    changeState(State.SEARCHING)
                    return@thread
                }
            }
        }
    }

    private fun macToInt(mac: String): Int {
        val addressBytes = mac.split(':')
            .map { it.toUByte(16).toByte() }
            .toByteArray()

        return ByteBuffer.allocate(Long.SIZE_BYTES).run {
            put(addressBytes)
            rewind()
            getInt()
        }
    }


    private inner class BluetoothNeighbor(
        val socket: BluetoothSocket,
    ) : Neighbor {
        var receiveCb: (DirectMessage) -> Unit = {}
            private set
        var disconnectCb: () -> Unit = {}
            private set

        override val sourceType = SourceType.BLUETOOTH

        override fun send(msg: DirectMessage) {
            try {
                socket.outputStream.write(msg.toByteArray())
            } catch (e: IOException) {
                Log.d(TAG, "${socket.remoteDevice.address} closed connection: ${e.message}")
                changeState(State.SEARCHING)
            }
        }

        override fun setOnReceive(cb: (DirectMessage) -> Unit) {
            receiveCb = cb
        }

        override fun setOnDisconnect(cb: () -> Unit) {
            disconnectCb = cb
        }

        override fun hashCode() = socket.remoteDevice.address.hashCode()

        override fun equals(other: Any?) = other is BluetoothNeighbor
                && socket.remoteDevice.address == other.socket.remoteDevice.address

        override fun toString(): String = socket.remoteDevice.address
    }
}