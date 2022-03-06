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
import android.os.Binder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.network.bluetooth.BluetoothDiscovery
import ru.vizbash.grapevine.storage.messages.MessageEntity
import ru.vizbash.grapevine.ui.chat.ChatActivity
import ru.vizbash.grapevine.ui.main.MainActivity
import javax.inject.Inject

@AndroidEntryPoint
class ForegroundService : Service() {
    @Inject lateinit var grapevineService: GrapevineService
    @Inject lateinit var bluetoothDiscovery: BluetoothDiscovery

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var started = false

    private var bluetoothEnabled = MutableStateFlow(false)
    private var bluetoothHardwareEnabled = MutableStateFlow(false)
    private var bluetoothUserEnabled = MutableStateFlow(true)

    private lateinit var foregroundNotification: NotificationCompat.Builder

    companion object {
        private const val FOREGROUND_CHANNEL_ID = "status_channel"
        private const val MESSAGE_CHANNEL_ID = "message_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 10
        private const val ACTION_MARK_READ = "ru.vizbash.grapevine.mark_read"
        private const val ACTION_REPLY = "ru.vizbash.grapevine.reply"
        private const val ACTION_DISMISS = "ru.vizbash.grapevine.reply.dismiss"
        private const val KEY_REPLY_TEXT = "reply_text"
        private const val EXTRA_CHAT_ID = "sender_id"
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) {
                return
            }

            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)) {
                BluetoothAdapter.STATE_ON -> {
                    bluetoothHardwareEnabled.value = true
                    bluetoothEnabled.value = bluetoothUserEnabled.value
                }
                BluetoothAdapter.STATE_OFF -> {
                    bluetoothHardwareEnabled.value = false
                    bluetoothEnabled.value = false
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
            }
        }
    }

    inner class ServiceBinder : Binder() {
        val grapevineService = this@ForegroundService.grapevineService

        val bluetoothHardwareEnabled = this@ForegroundService.bluetoothHardwareEnabled.asStateFlow()
        val bluetoothEnabled = this@ForegroundService.bluetoothEnabled.asStateFlow()

        fun setBluetoothUserEnabled(enabled: Boolean) {
            bluetoothUserEnabled.value = enabled
            this@ForegroundService.bluetoothEnabled.value =
                bluetoothHardwareEnabled.value && bluetoothUserEnabled.value
        }

        fun suppressChatNotifications(chatId: Long) {
            NotificationManagerCompat.from(this@ForegroundService).cancel(chatId.toInt())
            suppressedChats.add(chatId)
        }

        fun enableChatNotifications(chatId: Long) {
            suppressedChats.remove(chatId)
        }
    }

    override fun onBind(intent: Intent?) = ServiceBinder()

    override fun onCreate() {
        super.onCreate()

        registerReceiver(
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
        )
        registerReceiver(
            notificationActionReceiver,
            IntentFilter().apply {
                addAction(ACTION_MARK_READ)
                addAction(ACTION_REPLY)
                addAction(ACTION_DISMISS)
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

        unregisterReceiver(bluetoothStateReceiver)
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
            showMessageNotifications()
        }

        bluetoothHardwareEnabled.value = bluetoothDiscovery.isAdapterEnabled
        bluetoothEnabled.value = bluetoothHardwareEnabled.value && bluetoothUserEnabled.value

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

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannels(listOf(statusChannel, messageChannel))
        }
    }

    private fun getForegroundText(): String {
        val bluetoothStatus = getString(if (bluetoothEnabled.value) {
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

    private val messageGroups = mutableMapOf<Long, MutableList<MessageEntity>>()
    private val suppressedChats = mutableSetOf<Long>()

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

    private suspend fun showNotification(
        notificationManager: NotificationManagerCompat,
        msg: MessageEntity,
    ) {
        messageGroups.getOrPut(msg.chatId) { mutableListOf() }.add(msg)

        val style = createNotificationStyle(msg.chatId)
        val replyAction = createReplyAction(msg)
        val markReadAction = createMarkReadAction(msg)

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
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setDeleteIntent(dismissPendingIntent)
            .build()

        notificationManager.notify(msg.chatId.toInt(), notification)
    }

    private suspend fun showMessageNotifications() {
        val notificationManager = NotificationManagerCompat.from(this)

        for (msg in grapevineService.ingoingMessages) {
            if (msg.chatId !in suppressedChats) {
                showNotification(notificationManager, msg)
            }
        }
    }

    private fun onReplyAction(intent: Intent) {
        val chatId = intent.getLongExtra(EXTRA_CHAT_ID, 0)
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY_TEXT)
            ?: return

        val notificationManager = NotificationManagerCompat.from(this@ForegroundService)

        coroutineScope.launch {
            grapevineService.getContact(chatId)?.let {
                val msg = grapevineService.sendMessage(it, text.toString())
                showNotification(notificationManager, msg)
            }
        }
    }

    private fun onMarkReadAction(intent: Intent) {
        val chatId = intent.getLongExtra(EXTRA_CHAT_ID, 0)
        val messages = messageGroups[chatId] ?: return

        NotificationManagerCompat.from(this@ForegroundService)
            .cancel(chatId.toInt())

        coroutineScope.launch {
            for (msg in messages) {
                grapevineService.markAsRead(msg.id, chatId)
            }
        }
    }
}