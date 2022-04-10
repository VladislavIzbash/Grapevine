package ru.vizbash.grapevine.network.transport

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Looper
import android.util.Log
import com.google.protobuf.InvalidProtocolBufferException
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import ru.vizbash.grapevine.network.Router
import ru.vizbash.grapevine.network.message.DirectMessages.DirectMessage
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.*
import javax.inject.Inject
import kotlin.concurrent.thread

@SuppressLint("MissingPermission")
@ServiceScoped
class WifiTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val router: Router,
) : WifiP2pManager.DnsSdTxtRecordListener,
    WifiP2pManager.DnsSdServiceResponseListener,
    WifiP2pManager.ConnectionInfoListener
{
    companion object {
        private const val TAG = "WifiTransport"

        private const val SERVICE_NAME = "Grapevine"
        private const val SERVICE_TYPE = "_grapevine._tcp"
        private const val PORT = 54013
        private const val TICK_INTERVAL_MS = 200L
    }

    var packetsSent = 0
        private set
    var packetsReceived = 0
        private set

    private val p2pManager by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    }
    private lateinit var p2pChannel: WifiP2pManager.Channel

    @Volatile
    private var started = false

    private lateinit var looper: Looper
    private val tickTimer = Timer()
    private lateinit var serviceInfo: WifiP2pDnsSdServiceInfo

    private val neighbors = mutableSetOf<WifiNeighbor>()

    private val p2pConnectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) {
                p2pManager.requestConnectionInfo(p2pChannel, this@WifiTransport)
            }
        }
    }

    fun start() {
        if (started) {
            return
        }

        started = true
        Log.i(TAG, "Starting")

        context.registerReceiver(
            p2pConnectionReceiver,
            IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION),
        )

        startThread()
    }

    private fun startThread() = thread {
        Looper.prepare()
        looper = Looper.myLooper()!!

        p2pChannel = p2pManager.initialize(context, looper, null)

        p2pManager.setDnsSdResponseListeners(p2pChannel, this, this)

        val serverChannel = ServerSocketChannel.open().apply {
            configureBlocking(false)
            bind(InetSocketAddress(PORT))
        }

        registerService()

        tickTimer.scheduleAtFixedRate(ServerTask(serverChannel), 100, TICK_INTERVAL_MS)
        Looper.loop()

        p2pChannel.close()
        serverChannel.close()
    }.apply {
        name = "WifiThread"
    }

    fun stop() {
        if (!started) {
            return
        }

        started = false

        context.unregisterReceiver(p2pConnectionReceiver)
        tickTimer.cancel()

        p2pManager.removeLocalService(p2pChannel, serviceInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                looper.quit()
                Log.i(TAG, "Stopped")
            }

            override fun onFailure(reason: Int) {
                looper.quit()
            }
        })
    }

    private fun registerService() {
        serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
            SERVICE_NAME,
            SERVICE_TYPE,
            emptyMap(),
        )

        p2pManager.addLocalService(p2pChannel, serviceInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Added local service")
                onServiceRegistered()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "addLocalService() failed with code $reason")
                stop()
            }
        })
    }

    private fun onServiceRegistered() {
        val request = WifiP2pDnsSdServiceRequest.newInstance(SERVICE_NAME, SERVICE_TYPE)
        p2pManager.addServiceRequest(p2pChannel, request, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Added service request")
                onServiceRequestDone()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "addServiceRequest() failed with code $reason")
                stop()
            }
        })
    }

    private fun onServiceRequestDone() {
        p2pManager.discoverServices(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovering services")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "discoverServices() failed with code $reason")
                stop()
            }
        })
    }

    override fun onDnsSdTxtRecordAvailable(
        fullDomain: String,
        record: MutableMap<String, String>,
        device: WifiP2pDevice,
    ) {
        Log.d(TAG, "Discovered ${device.deviceAddress} (${device.deviceName})")

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        p2pManager.connect(p2pChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connecting to ${device.deviceAddress}")
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "connect() failed with code $reason")
            }
        })
    }

    override fun onDnsSdServiceAvailable(
        instanceName: String,
        registrationType: String,
        device: WifiP2pDevice,
    ) {
        Log.d(TAG, "Service discovered ${device.deviceAddress} (${device.deviceName})")
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        if (!info.groupFormed) {
            Log.w(TAG, "Group is not formed")
            return
        } else {
            Log.i(TAG, "Group is formed")
        }

        if (info.isGroupOwner) {
            return
        }

        val address = info.groupOwnerAddress.hostAddress
        val channel = try {
            SocketChannel.open(InetSocketAddress(address, PORT)).apply {
                configureBlocking(false)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to connect to $address: ${e.message}")
            return
        }

        Log.d(TAG, "Connected to $address")

        synchronized(this) {
            val neighbor = WifiNeighbor(channel)
            if (neighbors.add(neighbor)) {
                router.addNeighbor(neighbor)
            }
        }
    }

    private inner class ServerTask(
        private val serverChannel: ServerSocketChannel,
    ) : TimerTask() {
        private val buffers = mutableMapOf<WifiNeighbor, ByteBuffer>()
        private val sizeBuf = ByteBuffer.allocate(Int.SIZE_BYTES).apply {
            order(ByteOrder.BIG_ENDIAN)
        }

        override fun run() = synchronized(this@WifiTransport) {
            serverChannel.accept()?.let {
                it.configureBlocking(false)

                Log.d(TAG, "Accepted ${it.remoteAddress}")

                val neighbor = WifiNeighbor(it)
                if (neighbors.add(neighbor)) {
                    router.addNeighbor(neighbor)
                }
            }

            for (neighbor in neighbors) {
                val buffer = if (buffers[neighbor] == null) {
                    sizeBuf.rewind()
                    if (!readFrom(neighbor, sizeBuf)) {
                        continue
                    }

                    val size = sizeBuf.getInt(0)
                    if (size > 250_000) {
                        continue
                    }

                    val newBuffer = ByteBuffer.allocate(size)
                    buffers[neighbor] = newBuffer

                    Log.d(TAG, "allocated $size bytes for message")
                    newBuffer
                } else {
                    buffers[neighbor]!!
                }

                if (readFrom(neighbor, buffer)) {
                    Log.d(TAG, "read partial message")
                }

                if (buffer.position() == buffer.capacity()) {
                    buffer.flip()
                    try {
                        val msg = DirectMessage.parseFrom(buffer)
                        packetsReceived++

                        neighbor.receiveCb(msg)
                    } catch (e: InvalidProtocolBufferException) {
                    }
                    buffers.remove(neighbor)
                }
            }
        }
    }

    private fun readFrom(neighbor: WifiNeighbor, buffer: ByteBuffer): Boolean {
        val read = neighbor.channel.read(buffer)
        return when {
            read == 0 -> false
            read < 0 -> {
                Log.d(TAG, "$neighbor disconnected")
                neighbor.disconnectCb()
                neighbors.remove(neighbor)
                false
            }
            else -> true
        }
    }

    private inner class WifiNeighbor(val channel: SocketChannel) : Neighbor {
        var receiveCb: (DirectMessage) -> Unit = {}
            private set
        var disconnectCb: () -> Unit = {}
            private set

        override fun send(msg: DirectMessage) {
            try {
                val msgBytes = msg.toByteArray()

                val sizeBytes = ByteBuffer.allocate(Int.SIZE_BYTES).apply {
                    order(ByteOrder.BIG_ENDIAN)
                    putInt(msgBytes.size)
                    rewind()
                }
                channel.write(sizeBytes)

                val buf = ByteBuffer.wrap(msgBytes)
                while (buf.remaining() > 0) {
                    channel.write(buf)
                }

                packetsSent++

                Log.d(TAG, "written ${buf.capacity()} bytes")
            } catch (e: IOException) {
                Log.d(TAG, "$this closed connection: ${e.message}")

                disconnectCb()

                synchronized(this@WifiTransport) {
                    neighbors.remove(this)
                }
            }
        }

        override fun setOnReceive(cb: (DirectMessage) -> Unit) {
            receiveCb = cb
        }

        override fun setOnDisconnect(cb: () -> Unit) {
            disconnectCb = cb
        }

        override fun hashCode() = channel.remoteAddress.hashCode()

        override fun equals(other: Any?) = other is WifiNeighbor
                && other.channel.remoteAddress == other.channel.remoteAddress

        override fun toString(): String = channel.remoteAddress.toString()
    }
}