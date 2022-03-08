package ru.vizbash.grapevine.network.discovery

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import ru.vizbash.grapevine.network.Neighbor
import ru.vizbash.grapevine.network.Router
import ru.vizbash.grapevine.network.SourceType
import ru.vizbash.grapevine.network.messages.direct.DirectMessage
import ru.vizbash.grapevine.util.TAG
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.*
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlin.random.Random

@ServiceScoped
@SuppressLint("MissingPermission")
class WifiDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val router: Router,
) : WifiP2pManager.DnsSdTxtRecordListener, WifiP2pManager.DnsSdServiceResponseListener {

    companion object {
        private const val SERVICE_NAME = "Grapevine"
        private const val SERVICE_TYPE = "_grapevine._tcp"
        private const val TICK_INTERVAL_MS = 200L
    }

    private val p2pManager by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    }
    private lateinit var p2pChannel: WifiP2pManager.Channel

    private val myConnKey = Random.nextLong()

    @Volatile
    private var running = false

    private lateinit var looper: Looper
    private lateinit var serviceInfo: WifiP2pDnsSdServiceInfo

    private val neighbors = mutableSetOf<WifiNeighbor>()

    fun start() {
        if (running) {
            return
        }

        running = true

        Log.i(TAG, "Starting")

        startThread()
    }

    private fun startThread() = thread {
        Looper.prepare()
        looper = Looper.myLooper()!!

        p2pChannel = p2pManager.initialize(context, looper, null)

        p2pManager.setDnsSdResponseListeners(p2pChannel, this, this)

        val serverChannel = ServerSocketChannel.open().apply {
            configureBlocking(false)
            bind(InetSocketAddress(0))
        }

        serviceInfo = registerService(serverChannel.socket().localPort)

        Timer().scheduleAtFixedRate(TickTask(serverChannel), 100, TICK_INTERVAL_MS)
        Looper.loop()

        p2pChannel.close()
        serverChannel.close()
    }.apply {
        name = "WifiThread"
    }

    fun stop() {
        if (!running) {
            return
        }

        running = false
        Log.i(this@WifiDiscovery.TAG, "Stopping")

        p2pManager.removeLocalService(p2pChannel, serviceInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                looper.quit()
            }

            override fun onFailure(reason: Int) {
                looper.quit()
            }
        })
    }

    private fun registerService(port: Int): WifiP2pDnsSdServiceInfo {
        val info = WifiP2pDnsSdServiceInfo.newInstance(
            SERVICE_NAME,
            SERVICE_TYPE,
            mapOf(
                "port" to port.toString(),
                "conn_key" to myConnKey.toString(),
            ),
        )

        p2pManager.addLocalService(p2pChannel, info, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(this@WifiDiscovery.TAG, "Added local service")
                onServiceRegistered()
            }

            override fun onFailure(reason: Int) {
                Log.e(this@WifiDiscovery.TAG, "addLocalService() failed with code $reason")
                stop()
            }
        })

        return info
    }

    private fun onServiceRegistered() {
        val request = WifiP2pDnsSdServiceRequest.newInstance(SERVICE_NAME, SERVICE_TYPE)
        p2pManager.addServiceRequest(p2pChannel, request, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(this@WifiDiscovery.TAG, "Added service request")
                onServiceRequestDone()
            }

            override fun onFailure(reason: Int) {
                Log.e(this@WifiDiscovery.TAG, "addServiceRequest() failed with code $reason")
                stop()
            }
        })
    }

    private fun onServiceRequestDone() {
        p2pManager.discoverServices(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(this@WifiDiscovery.TAG, "Discovering services")
            }

            override fun onFailure(reason: Int) {
                Log.e(this@WifiDiscovery.TAG, "discoverServices() failed with code $reason")
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

        val connKey = record["conn_key"] ?: return
        val port = record["port"] ?: return

        if (myConnKey > connKey.toLong()) {
            Log.d(TAG, "Waiting ${device.deviceAddress} to connect")
            return
        }

        p2pManager.connect(p2pChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(this@WifiDiscovery.TAG, "Connected to ${device.deviceAddress}")
                onConnected(port.toInt())
            }

            override fun onFailure(reason: Int) {
                Log.e(this@WifiDiscovery.TAG, "connect() failed with code $reason")
                stop()
            }
        })
    }

    override fun onDnsSdServiceAvailable(
        instanceName: String,
        registrationType: String,
        device: WifiP2pDevice,
    ) {}

    private fun onConnected(port: Int) {
        p2pManager.requestConnectionInfo(p2pChannel) { info ->
            val address = info.groupOwnerAddress.hostAddress
            val channel = SocketChannel.open(InetSocketAddress(address, port)).apply {
                configureBlocking(false)
            }

            synchronized(this) {
                neighbors.add(WifiNeighbor(channel))
            }
        }
    }

    private inner class TickTask(
        private val serverChannel: ServerSocketChannel,
    ) : TimerTask() {
        override fun run() = synchronized(this@WifiDiscovery) {
            val clientChannel = serverChannel.accept()
            if (clientChannel != null) {
                clientChannel.configureBlocking(false)
                neighbors.add(WifiNeighbor(clientChannel))
            }

//            for (neighbor in neighbors) {
//                neighbor.channel.socket().getInputStream()
//                DirectMessage.parseFrom(neighbor.channel)
//            }
        }
    }

    private inner class WifiNeighbor(
        val channel: SocketChannel,
    ) : Neighbor {
        var receiveCb: (DirectMessage) -> Unit = {}
            private set
        var disconnectCb: () -> Unit = {}
            private set

        override val sourceType = SourceType.WIFI

        override fun send(msg: DirectMessage) {
            try {
//                msg.writeDelimitedTo(channel.socket().getOutputStream())
            } catch (e: IOException) {
                Log.d(TAG, "$this closed connection: ${e.message}")

                disconnectCb()

                synchronized(this@WifiDiscovery) {
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