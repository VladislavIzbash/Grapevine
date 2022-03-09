package ru.vizbash.grapevine.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Binder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import androidx.navigation.NavDeepLinkBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.network.bluetooth.discovery.BluetoothDiscovery
import ru.vizbash.grapevine.network.discovery.WifiDiscovery
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import ru.vizbash.grapevine.storage.messages.MessageEntity
import ru.vizbash.grapevine.ui.chat.ChatActivity
import ru.vizbash.grapevine.ui.main.MainActivity
import javax.inject.Inject

@AndroidEntryPoint
class ForegroundService : Service() {
    @Inject lateinit var grapevineService: GrapevineService
    @Inject lateinit var bluetoothDiscovery: BluetoothDiscovery
    @Inject lateinit var wifiDiscovery: WifiDiscovery

    companion object {
        private const val FOREGROUND_CHANNEL_ID = "status_channel"
        private const val MESSAGE_CHANNEL_ID = "message_channel"
        private const val INVITATIONS_CHANNEL_ID = "invitations_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 10
        private const val ACTION_MARK_READ = "ru.vizbash.grapevine.action.MARK_READ"
        private const val ACTION_REPLY = "ru.vizbash.grapevine.action.REPLY"
        private const val ACTION_DISMISS = "ru.vizbash.grapevine.action.DISMISS"
        private const val EXTRA_CHAT_ID = "chat_id"
        private const val KEY_REPLY_TEXT = "reply_text"
        private const val ACTION_ACCEPT_INVITATION = "ru.vizbash.grapevine.action.ACCEPT_INVITATION"
        private const val ACTION_REJECT_INVITATION = "ru.vizbash.grapevine.action.REJECT_INVITATION"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var started = false

    private val _bluetoothHardwareEnabled = MutableStateFlow(false)
    val bluetoothHardwareEnabled = _bluetoothHardwareEnabled.asStateFlow()

    val bluetoothUserEnabled = MutableStateFlow(false)

    val bluetoothEnabled = bluetoothHardwareEnabled
        .combine(bluetoothUserEnabled, Boolean::and)
        .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    private val _wifiHardwareEnabled = MutableStateFlow(false)
    val wifiHardwareEnabled = _wifiHardwareEnabled.asStateFlow()

    val wifiUserEnabled = MutableStateFlow(false)

    val wifiEnabled = wifiHardwareEnabled
        .combine(wifiUserEnabled, Boolean::and)
        .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    private lateinit var foregroundNotification: NotificationCompat.Builder

    private val notificationManager by lazy {
        NotificationManagerCompat.from(this)
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                        BluetoothAdapter.STATE_ON -> _bluetoothHardwareEnabled.value = true
                        BluetoothAdapter.STATE_OFF -> _bluetoothHardwareEnabled.value = false
                    }
                }
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    when (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)) {
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED -> _wifiHardwareEnabled.value = true
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED -> _wifiHardwareEnabled.value = false
                    }
                }
                LocationManager.MODE_CHANGED_ACTION -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
                        _wifiHardwareEnabled.value = locationManager.isLocationEnabled
                        _bluetoothHardwareEnabled.value = locationManager.isLocationEnabled
                    }
                }
            }
        }
    }

    private val notificationActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_MARK_READ -> onMarkReadAction(intent)
                ACTION_REPLY -> onReplyAction(intent)
                ACTION_DISMISS -> {
                    val chatId = intent.getLongExtra(EXTRA_CHAT_ID, 0)
                    messageGroups.remove(chatId)
                }
                ACTION_ACCEPT_INVITATION -> onInvitationAction(intent, true)
                ACTION_REJECT_INVITATION -> onInvitationAction(intent, false)
            }
        }
    }

    inner class ServiceBinder : Binder() {
        val grapevineService = this@ForegroundService.grapevineService
        val foregroundService = this@ForegroundService
    }

    override fun onBind(intent: Intent?) = ServiceBinder()

    override fun onCreate() {
        super.onCreate()

        registerReceiver(
            stateReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(LocationManager.MODE_CHANGED_ACTION)
            },
        )
        registerReceiver(
            notificationActionReceiver,
            IntentFilter().apply {
                addAction(ACTION_MARK_READ)
                addAction(ACTION_REPLY)
                addAction(ACTION_DISMISS)
                addAction(ACTION_ACCEPT_INVITATION)
                addAction(ACTION_REJECT_INVITATION)
            },
        )

        registerNotificationChannels()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        foregroundNotification = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_notification)
            .setContentTitle(getString(R.string.grapevine_service))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }

    override fun onDestroy() {
        super.onDestroy()

        bluetoothDiscovery.stop()
        grapevineService.stop()
        coroutineScope.cancel()

        unregisterReceiver(stateReceiver)
        unregisterReceiver(notificationActionReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            grapevineService.start()
            started = true
        }

        foregroundNotification.setContentText(getForegroundText())
        startForeground(FOREGROUND_NOTIFICATION_ID, foregroundNotification.build())

        coroutineScope.launch {
            bluetoothEnabled.collect {
                if (it) {
                    bluetoothDiscovery.start()
                } else {
                    bluetoothDiscovery.stop()
                }
                updateForegroundNotification()
            }
        }
        coroutineScope.launch {
            wifiEnabled.collect {
                if (it) {
                    wifiDiscovery.start()
                } else {
                    wifiDiscovery.stop()
                }
                updateForegroundNotification()
            }
        }

        coroutineScope.launch {
            showMessageNotifications()
        }
        coroutineScope.launch {
            showInvitationNotifications()
        }

        _bluetoothHardwareEnabled.value = bluetoothDiscovery.isAdapterEnabled

        return START_STICKY
    }

    private fun registerNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val statusChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                getString(R.string.status_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.status_channel_desc)
            }

            val messageChannel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                getString(R.string.message_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.message_channel_desc)
            }

            val invitationsChannel = NotificationChannel(
                INVITATIONS_CHANNEL_ID,
                "Приглашения",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.invitations_channel_desc)
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannels(listOf(
                statusChannel,
                messageChannel,
                invitationsChannel,
            ))
        }
    }

    private fun getForegroundText(): String {
        val bluetoothStatus = getString(if (bluetoothEnabled.value) {
            R.string.on
        } else {
            R.string.off
        })
        val wifiStatus = getString(if (wifiEnabled.value) {
            R.string.on
        } else {
            R.string.off
        })

        return getString(R.string.status_text, bluetoothStatus, wifiStatus)
    }

    private fun updateForegroundNotification() {
        foregroundNotification.setContentText(getForegroundText())
        NotificationManagerCompat.from(this).notify(
            FOREGROUND_NOTIFICATION_ID,
            foregroundNotification.build(),
        )
    }

    private val messageGroups = mutableMapOf<Long, MutableList<MessageEntity>>()
    private val suppressedChats = mutableSetOf<Long>()

    fun suppressChatNotifications(chatId: Long) {
        NotificationManagerCompat.from(this@ForegroundService).cancel(chatId.toInt())
        suppressedChats.add(chatId)
    }

    fun enableChatNotifications(chatId: Long) {
        suppressedChats.remove(chatId)
    }

    private var showInvitations = false

    fun suppressContactInvitationNotifications() {
        showInvitations = false

        coroutineScope.launch {
            grapevineService.contactList.first()
                .filter { it.state == ContactEntity.State.INGOING }
                .forEach {
                    notificationManager.cancel(it.nodeId.toInt())
                }
        }
    }

    fun enableContactInvitationNotifications() {
        showInvitations = true
    }

    private suspend fun createNotificationStyle(chatId: Long): NotificationCompat.MessagingStyle {
        val sender = grapevineService.getContact(chatId) ?: throw IllegalArgumentException()

        val contactPerson = Person.Builder()
            .setName(sender.username)
            .setKey(sender.nodeId.toString())
        sender.photo?.let {
            contactPerson.setIcon(IconCompat.createWithBitmap(it))
        }

        val myPerson by lazy {
            val person = Person.Builder()
                .setName(grapevineService.currentProfile.username)
                .setKey(grapevineService.currentProfile.nodeId.toString())

            grapevineService.currentProfile.photo?.let {
                person.setIcon(IconCompat.createWithBitmap(it))
            }
            person
        }

        val style = NotificationCompat.MessagingStyle(contactPerson.build())
        for (msg in messageGroups[chatId]!!) {
            val text = StringBuilder(msg.text).apply {
                if (msg.originalMessageId != null) {
                    append("\n${getString(R.string.notification_forwarded_message)}")
                }
                if (msg.file != null) {
                    append("\n${getString(R.string.notification_attachment, msg.file.name)}")
                }
            }.toString()

            val person = if (msg.senderId == grapevineService.currentProfile.nodeId) {
                myPerson
            } else {
                contactPerson
            }

            style.addMessage(text, msg.timestamp.time, person.build())
        }
        return style
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun createReplyAction(msg: MessageEntity): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel(getString(R.string.reply))
            .build()

        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            msg.chatId.toInt(),
            Intent(ACTION_REPLY).apply {
                putExtra(EXTRA_CHAT_ID, msg.chatId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Action.Builder(
            R.drawable.ic_reply,
            getString(R.string.reply),
            replyPendingIntent,
        ).addRemoteInput(remoteInput).build()
    }

    private fun createMarkReadAction(msg: MessageEntity): NotificationCompat.Action {
        val markReadPendingIntent = PendingIntent.getBroadcast(
            this,
            msg.chatId.toInt(),
            Intent(ACTION_MARK_READ).apply {
                putExtra(EXTRA_CHAT_ID, msg.chatId)
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Action(
            R.drawable.ic_check,
            getString(R.string.mark_as_read),
            markReadPendingIntent,
        )
    }

    private suspend fun showMessageNotification(msg: MessageEntity) {
        messageGroups.getOrPut(msg.chatId) { mutableListOf() }.add(msg)

        val dismissPendingIntent = PendingIntent.getBroadcast(
            this,
            msg.chatId.toInt(),
            Intent(ACTION_DISMISS).apply {
                putExtra(EXTRA_CHAT_ID, msg.chatId)
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val contentPendingIntent = PendingIntent.getActivity(
            this,
            msg.chatId.toInt(),
            Intent(this, ChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(ChatActivity.EXTRA_CHAT_ID, msg.chatId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_notification)
            .setStyle(createNotificationStyle(msg.chatId))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(createReplyAction(msg))
            .addAction(createMarkReadAction(msg))
            .setDeleteIntent(dismissPendingIntent)
            .build()

        notificationManager.notify(msg.chatId.toInt(), notification)
    }

    private suspend fun showMessageNotifications() {
        for (msg in grapevineService.ingoingMessages) {
            if (msg.chatId !in suppressedChats) {
                showMessageNotification(msg)
            }
        }
    }

    private fun createAcceptAction(contact: ContactEntity): NotificationCompat.Action {
        val acceptPendingIntent = PendingIntent.getBroadcast(
            this,
            contact.nodeId.toInt(),
            Intent(ACTION_ACCEPT_INVITATION).apply {
                putExtra(EXTRA_CHAT_ID, contact.nodeId)
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Action(
            R.drawable.ic_check,
            getString(R.string.accept),
            acceptPendingIntent,
        )
    }

    private fun createRejectAction(contact: ContactEntity): NotificationCompat.Action {
        val rejectPendingIntent = PendingIntent.getBroadcast(
            this,
            contact.nodeId.toInt(),
            Intent(ACTION_REJECT_INVITATION).apply {
                putExtra(EXTRA_CHAT_ID, contact.nodeId)
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Action(
            R.drawable.ic_clear,
            getString(R.string.reject),
            rejectPendingIntent,
        )
    }

    private suspend fun showInvitationNotifications() {
        for (contact in grapevineService.ingoingInvitations) {
            if (!showInvitations) {
                continue
            }

            val contentPendingIntent = NavDeepLinkBuilder(this)
                .setGraph(R.navigation.main_nav)
                .setDestination(R.id.contactsFragment)
                .createPendingIntent()

            val notification = NotificationCompat.Builder(this, INVITATIONS_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_notification)
                .setContentTitle(contact.username)
                .setContentText(getString(R.string.invitation_message_text))
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .addAction(createAcceptAction(contact))
                .addAction(createRejectAction(contact))
                .build()

            notificationManager.notify(contact.nodeId.toInt(), notification)
        }
    }

    private fun onReplyAction(intent: Intent) {
        val chatId = intent.getLongExtra(EXTRA_CHAT_ID, 0)
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY_TEXT)
            ?: return

        coroutineScope.launch {
            grapevineService.getContact(chatId)?.let {
                val msg = grapevineService.sendMessage(it, text.toString())
                showMessageNotification(msg)
            }
        }
    }

    private fun onMarkReadAction(intent: Intent) {
        val chatId = intent.getLongExtra(EXTRA_CHAT_ID, -1)
        val messages = messageGroups[chatId] ?: return

        notificationManager.cancel(chatId.toInt())

        coroutineScope.launch {
            for (msg in messages) {
                grapevineService.markAsRead(msg.id, chatId)
            }
        }
    }

    private fun onInvitationAction(intent: Intent, accepted: Boolean) {
        val contactId = intent.getLongExtra(EXTRA_CHAT_ID, -1)

        notificationManager.cancel(contactId.toInt())

        coroutineScope.launch {
            grapevineService.getContact(contactId)?.let {
                if (accepted) {
                    grapevineService.acceptContact(it)
                } else {
                    grapevineService.rejectContact(it)
                }
            }
        }
    }
}