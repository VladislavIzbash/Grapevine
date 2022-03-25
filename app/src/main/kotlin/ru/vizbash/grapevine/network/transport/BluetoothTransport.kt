package ru.vizbash.grapevine.network.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import ru.vizbash.grapevine.network.Router
import ru.vizbash.grapevine.network.SourceType
import ru.vizbash.grapevine.network.message.DirectMessages.DirectMessage
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import javax.inject.Inject
import kotlin.concurrent.thread

@SuppressLint("MissingPermission", "HardwareIds")
@ServiceScoped
class BluetoothTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val router: Router,
) {
    companion object {
        private const val TAG = "BluetoothTransport"

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

    @Volatile
    private var running = false

    @Volatile
    private var neighbor: BluetoothNeighbor? = null

    val isAdapterEnabled get() = adapter.isEnabled

    fun start() {
        check(adapter.isEnabled)

        if (running) {
            return
        }

        running = true
        Log.i(TAG, "Starting")

        startDiscover()
    }

    fun stop() {
        if (!running) {
            return
        }

        running = false

        neighbor?.disconnectCb?.invoke()
        neighbor = null

        Log.i(TAG, "Stopped")
    }

    private fun startDiscover() {
        val serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(
            SERVICE_NAME,
            SERVICE_UUID,
        )

        startAccepting(serverSocket)
        startBondedScan(serverSocket)
    }

    private fun startBondedScan(serverSocket: BluetoothServerSocket) = thread {
        while (running && neighbor == null) {
            val ret = scanBondedDevices()
            if (ret != null) {
                neighbor = ret
                router.addNeighbor(ret)
                startReceiving()
            } else {
                Thread.sleep(BONDED_SCAN_INTERVAL_MS)
            }
        }
        serverSocket.close()
    }.apply {
        name = "BtScanThread"
    }

    private fun startAccepting(serverSocket: BluetoothServerSocket) = thread {
        try {
            val socket = serverSocket.accept()
            neighbor = BluetoothNeighbor(socket)

            Log.i(TAG, "Accepted ${socket.remoteDevice.address} (${socket.remoteDevice.name})")
            router.addNeighbor(neighbor!!)
            startReceiving()
        } catch (e: IOException) {
        }
        serverSocket.close()
    }.apply {
        name = "BtAcceptThread"
    }

    private fun startReceiving() = thread {
        try {
            while (running && neighbor != null) {
                val msg = DirectMessage.parseDelimitedFrom(neighbor!!.socket.inputStream)
                Log.d(TAG, "read ${msg.serializedSize} bytes")

                neighbor!!.receiveCb(msg)
            }
        } catch (e: Exception) {
            if (e is IOException) {
                if (neighbor != null) {
                    val addr = neighbor!!.socket.remoteDevice.address
                    Log.d(TAG, "$addr closed connection: ${e.message}")
                    neighbor!!.disconnectCb()
                    neighbor!!.socket.close()
                    neighbor = null

                    startDiscover()
                }
                return@thread
            }
        }
    }

    private fun scanBondedDevices(): BluetoothNeighbor? {
        for (device in adapter.bondedDevices) {
            if (!running || neighbor != null) {
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

    fun macToInt(mac: String): Int {
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
                msg.writeDelimitedTo(socket.outputStream)
                Log.d(TAG, "written ${msg.toByteArray().size} bytes")
            } catch (e: IOException) {
                if (neighbor != null) {
                    Log.d(TAG, "${socket.remoteDevice.address} closed connection: ${e.message}")
                    disconnectCb()
                    socket.close()
                    neighbor = null

                    startDiscover()
                }
            }
        }

        override fun setOnReceive(cb: (DirectMessage) -> Unit) {
            receiveCb = cb
        }

        override fun setOnDisconnect(cb: () -> Unit) {
            disconnectCb = cb
        }

        override fun hashCode() = socket.hashCode()

        override fun equals(other: Any?) = other is BluetoothNeighbor && socket == other.socket

        override fun toString(): String = socket.remoteDevice.address
    }
}