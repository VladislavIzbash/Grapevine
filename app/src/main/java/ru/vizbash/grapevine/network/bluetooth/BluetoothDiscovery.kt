package ru.vizbash.grapevine.network.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.CountDownTimer
import android.util.Log
import com.google.protobuf.InvalidProtocolBufferException
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import ru.vizbash.grapevine.TAG
import ru.vizbash.grapevine.network.Neighbor
import ru.vizbash.grapevine.network.Router
import ru.vizbash.grapevine.network.SourceType
import ru.vizbash.grapevine.network.messages.direct.DirectMessage
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.thread

@SuppressLint("MissingPermission")
@ServiceScoped
class BluetoothDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val router: Router,
) {
    companion object {
        private const val PAIRED_SCAN_INTERVAL_MS = 10000

        private const val MAX_CONNECTIONS = 4

        private const val BT_SERVICE_NAME = "Grapevine"
        private val BT_SERVICE_UUID = UUID.fromString("f8393dd1-32a9-49ef-9768-749bafed80ed")
    }

    private data class SendRequest(val neighbor: BluetoothNeighbor, val message: DirectMessage)

    @Volatile var isRunning = false
        private set

    private val bluetoothAdapter by lazy {
        context.getSystemService(BluetoothManager::class.java).adapter
    }

    private val sendQueue = ArrayBlockingQueue<SendRequest>(MAX_CONNECTIONS * 10)

    private var serverSocket: BluetoothServerSocket? = null

    private val threads = Collections.synchronizedList(mutableListOf<Thread>())
    private val neighbors = Collections.synchronizedSet(mutableSetOf<BluetoothNeighbor>())

    fun start() {
        if (isRunning) {
            return
        }

        Log.i(TAG, "Starting bluetooth service")

        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth adapter is disabled, stopping")
            return
        }

        isRunning = true

        threads += thread {
            acceptLoop()
        }.apply {
            name = "BluetoothAcceptThread"
        }

        threads += thread {
            var elapsedMs = 0

            addPairedDevices()

            while (isRunning) {
                Thread.sleep(100)
                elapsedMs += 100

                if (elapsedMs >= PAIRED_SCAN_INTERVAL_MS) {
                    addPairedDevices()
                    elapsedMs = 0
                }
            }
        }.apply {
            name = "BluetoothPairedScanThread"
        }

        threads += thread {
            senderLoop()
        }.apply {
            name = "BluetoothSenderThread"
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null

        threads.forEach(Thread::join)
        threads.clear()

        for (neighbor in neighbors) {
            neighbor.disconnectCb()
            neighbor.socket.close()
        }
        neighbors.clear()

        Log.i(TAG, "Stopped bluetooth service")
    }

    private fun addNeighbor(socket: BluetoothSocket){
        val neighbor = BluetoothNeighbor(socket)
        if (neighbor in neighbors) {
            socket.close()
            return
        }

        neighbors.add(neighbor)
        router.addNeighbor(neighbor)

        threads += thread {
            receiverLoop(neighbor)
        }.apply {
            name = "BluetoothReceiverThread_${socket.remoteDevice.address}"
        }
    }

    private fun addPairedDevices() {
        for (device in bluetoothAdapter.bondedDevices) {
            val isConnected = neighbors.any { it.socket.remoteDevice.address == device.address }
            if (neighbors.size > MAX_CONNECTIONS || isConnected) {
                continue
            }

            Log.d(TAG, "Probing paired device ${device.address}")

            try {
                val socket = device.createRfcommSocketToServiceRecord(BT_SERVICE_UUID)
                socket.connect()

                addNeighbor(socket)
                Log.d(TAG, "Connected to ${device.address}")
            } catch (e: IOException) {
                Log.d(TAG, "Failed to connect to ${device.address}: ${e.message}")
            }
        }
    }

    private fun acceptLoop() {
        serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
            BT_SERVICE_NAME,
            BT_SERVICE_UUID,
        )

        while (isRunning && neighbors.size < MAX_CONNECTIONS) {
            try {
                val socket = serverSocket!!.accept()
                Log.d(TAG, "Accepted ${socket.remoteDevice.address}")
            } catch (e: IOException) {
            }
        }

        serverSocket?.close()
    }

    private fun senderLoop() {
        while (isRunning) {
            val (neighbor, msg) = sendQueue.poll(200, TimeUnit.MILLISECONDS) ?: continue

            try {
                neighbor.socket.outputStream.write(msg.toByteArray())
            } catch (e: IOException) {
                val addr = neighbor.socket.remoteDevice.address
                Log.d(TAG, "$addr closed connection: ${e.message}")

                neighbors.remove(neighbor)
                neighbor.disconnectCb()
            }
        }
    }

    private fun receiverLoop(neighbor: BluetoothNeighbor) {
        val addr = neighbor.socket.remoteDevice.address

        while (isRunning) {
            val buffer = ByteArray(512)

            try {
                val read = neighbor.socket.inputStream.read(buffer)
                if (read <= 0) {
                    Log.d(TAG, "$addr closed connection")
                    break
                }

                val msg = DirectMessage.parseFrom(buffer.sliceArray(0 until read))
                neighbor.receiveCb(msg)
            } catch (e: Exception) {
                when (e) {
                    is IOException -> {
                        Log.d(TAG, "$addr closed connection: ${e.message}")
                        neighbors.remove(neighbor)
                        neighbor.disconnectCb()
                        break
                    }
                    is InvalidProtocolBufferException -> {
                        Log.w(TAG, "$addr sent invalid message")
                    }
                }
            }
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
            sendQueue.add(SendRequest(this, msg))
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