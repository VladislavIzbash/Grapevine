package ru.vizbash.grapevine.network.transport

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.google.protobuf.InvalidProtocolBufferException
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import ru.vizbash.grapevine.TAG
import ru.vizbash.grapevine.network.Router
import ru.vizbash.grapevine.network.messages.direct.DirectMessage
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.thread

private const val MAX_CONNECTIONS = 4

private const val BT_SERVICE_NAME = "Grapevine"
private val BT_SERVICE_UUID = UUID.fromString("f8393dd1-32a9-49ef-9768-749bafed80ed")

@ServiceScoped
class BluetoothService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val router: Router,
) {
    private data class SendRequest(val neighbor: BluetoothNeighbor, val message: DirectMessage)

    @Volatile var isRunning = false
        private set

    private val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter

    private val sendQueue = ArrayBlockingQueue<SendRequest>(MAX_CONNECTIONS * 10)

    private val threads = Collections.synchronizedList(mutableListOf<Thread>())
    private val neighbors = Collections.synchronizedList(mutableListOf<BluetoothNeighbor>())

    fun start() {
        if (isRunning) {
            return
        }

        Log.i(TAG, "Starting bluetooth service")

        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth adapter is disabled, stopping")
            return
        }

//        val filter = IntentFilter().apply {
//            addAction(BluetoothDevice.ACTION_FOUND)
//        }
//        context.registerReceiver(BtBroadcastReceiver(), filter)

        isRunning = true

        threads += thread {
            acceptLoop()
        }

        threads += thread {
            addPairedDevices()
        }

        threads += thread {
            senderLoop()
        }.apply {
            name = "BluetoothSenderThread"
        }
    }

    fun stop() {
        isRunning = false

        threads.forEach(Thread::join)
        threads.clear()

        neighbors.forEach {
            it.disconnectCb()
            it.socket.close()
        }
        neighbors.clear()

        Log.i(TAG, "Stopped bluetooth service")
    }

    private fun addNeighbor(socket: BluetoothSocket){
        val neighbor = BluetoothNeighbor(socket)
        neighbors.add(neighbor)

        threads += thread {
            receiverLoop(neighbor)
        }.apply {
            name = "BluetoothReceiverThread_${socket.remoteDevice.address}"
        }
    }

    private fun addPairedDevices() {
        for (device in bluetoothAdapter.bondedDevices) {
            Log.d(TAG, "Probing paired device ${device.address}")

            if (neighbors.size > MAX_CONNECTIONS) {
                continue
            }

            try {
                val socket = device.createRfcommSocketToServiceRecord(BT_SERVICE_UUID)
                socket.connect()
                addNeighbor(socket)
            } catch (e: IOException) {
                Log.d(TAG, "Failed to connect to ${device.address}: ${e.message}")
            }
        }
    }

    private fun acceptLoop() {
        val serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
            BT_SERVICE_NAME,
            BT_SERVICE_UUID,
        )

        while (isRunning && neighbors.size < MAX_CONNECTIONS) {
            try {
                val socket = serverSocket.accept(200) ?: continue
                addNeighbor(socket)
            } catch (e: IOException) {
                Log.w(TAG, "Failted to accept: ${e.message}")
            }
        }

        serverSocket.close()
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

                    neighbors.remove(neighbor)
                    neighbor.disconnectCb()
                    break
                }

                val msg = DirectMessage.parseFrom(buffer.sliceArray(0 until read))
                neighbor.receiveCb(msg)
            } catch (e: Exception) {
                if (e is IOException || e is InvalidProtocolBufferException) {
                    Log.w(TAG, "Error reading from $addr: ${e.message}")
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

        override fun send(msg: DirectMessage) {
            sendQueue.add(SendRequest(this, msg))
        }

        override fun setOnReceive(cb: (DirectMessage) -> Unit) {
            receiveCb = cb
        }

        override fun setOnDisconnect(cb: () -> Unit) {
            disconnectCb = cb
        }

        override fun disconnect() {
            socket.close()
        }
    }

//    private fun discoverAndConnect() {
//        if (!bluetoothAdapter.startDiscovery()) {
//            Log.e(TAG, "Failed to start discovery")
//            return
//        }
//    }


//    private class BtBroadcastReceiver : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            if (intent.action == BluetoothDevice.ACTION_FOUND) {
//                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
//
//                Log.d(TAG, "Found device $device")
//
//            }
//        }
//    }
}