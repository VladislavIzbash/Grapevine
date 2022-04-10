package ru.vizbash.grapevine.service.foreground

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.network.transport.BluetoothTransport
import ru.vizbash.grapevine.network.transport.WifiTransport
import ru.vizbash.grapevine.service.ChatService
import ru.vizbash.grapevine.service.MessageService
import ru.vizbash.grapevine.ui.main.MainActivity
import javax.inject.Inject

@ServiceScoped
class NotificationSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatNotificationSender: ChatNotificationSender,
    private val transportController: TransportController,
    private val bluetoothTransport: BluetoothTransport,
    private val wifiTransport: WifiTransport,
    private val chatService: ChatService,
    private val messageService: MessageService,
) : ChatNotificationSender.MessageActionListener {
    companion object {
        private const val FOREGROUND_CHANNEL_ID = "status_channel"
        const val MESSAGE_CHANNEL_ID = "message_channel"

        private const val FOREGROUND_NOTIFICATION_ID = 10

        private const val STATS_UPDATE_INTERVAL = 10_000L
    }

    private lateinit var coroutineScope: CoroutineScope

    private val mutedChats = mutableSetOf<Long>()

    fun start(coroutineScope: CoroutineScope, startForeground: (Int, Notification) -> Unit) {
        this.coroutineScope = coroutineScope

        registerChannels()
        chatNotificationSender.register(this)

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, ForegroundService::class.java).apply {
                action = ForegroundService.ACTION_STOP_SERVICE
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val fgNotification = NotificationCompat.Builder(context, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_notification)
            .setContentTitle(context.getString(R.string.grapevine_service))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(getStatusText()))
            .addAction(R.drawable.ic_stop, context.getString(R.string.stop), stopIntent)

        startForeground(FOREGROUND_NOTIFICATION_ID, fgNotification.build())

        val notificationManager = NotificationManagerCompat.from(context)

        transportController.setOnStateChanged {
            fgNotification.setStyle(NotificationCompat.BigTextStyle()
                .bigText(getStatusText()))
            notificationManager.notify(FOREGROUND_NOTIFICATION_ID, fgNotification.build())
        }
        coroutineScope.launch {
            while (true) {
                delay(STATS_UPDATE_INTERVAL)

                fgNotification.setStyle(NotificationCompat.BigTextStyle()
                    .bigText(getStatusText()))
                notificationManager.notify(FOREGROUND_NOTIFICATION_ID, fgNotification.build())
            }
        }
        coroutineScope.launch {
            for (msg in messageService.ingoingMessages) {
                if (msg.chatId != 0L && msg.chatId !in mutedChats) {
                    chatNotificationSender.notify(msg)
                }
            }
        }
        coroutineScope.launch {
            for (chat in chatService.ingoingChatInvitations) {
                if (chat.id !in mutedChats) {
                    chatNotificationSender.notifyInvitation(chat)
                }
            }
        }
        coroutineScope.launch {
            for (chat in chatService.ingoingChatKicks) {
                if (chat.id !in mutedChats) {
                    chatNotificationSender.notifyKick(chat)
                }
            }
        }
    }

    fun stop() {
        transportController.setOnStateChanged {  }
        chatNotificationSender.unregister()
        coroutineScope.cancel()
    }

    fun muteChat(chatId: Long) {
        chatNotificationSender.cancelChatNotifications(chatId)
        mutedChats.add(chatId)
    }

    fun unmuteChat(chatId: Long) {
        mutedChats.remove(chatId)
    }

    private fun registerChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val statusChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                context.getString(R.string.status_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.status_channel_desc)
            }

            val messageChannel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                context.getString(R.string.message_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.message_channel_desc)
            }

            val notificationManager = context.getSystemService(Service.NOTIFICATION_SERVICE)
                    as NotificationManager
            notificationManager.createNotificationChannels(listOf(statusChannel, messageChannel))
        }
    }

    private fun getStatusText(): String {
        val bluetoothStatus = if (transportController.btEnabled) R.string.on else R.string.off
        val wifiStatus = if (transportController.wifiEnabled) R.string.on else R.string.off

        return context.getString(
            R.string.status_text,
            context.getString(bluetoothStatus),
            context.getString(wifiStatus),
            wifiTransport.packetsSent + bluetoothTransport.packetsSent,
            wifiTransport.packetsReceived + bluetoothTransport.packetsReceived,
        )
    }

    override fun onMarkAsRead(chatId: Long) {
        coroutineScope.launch {
            for (msg in messageService.getUnread(chatId)) {
                messageService.markAsRead(msg)
            }
        }
    }

    override fun onReply(chatId: Long, text: String) {
        coroutineScope.launch {
            val msg = messageService.sendMessage(chatId, text)
            chatNotificationSender.notify(msg)
        }
    }
}