package ru.vizbash.grapevine.service.foreground

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.service.ChatService
import ru.vizbash.grapevine.service.MessageService
import javax.inject.Inject

@ServiceScoped
class NotificationSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transportController: TransportController,
    private val chatService: ChatService,
    private val messageService: MessageService,
) {
    companion object {
        private const val FOREGROUND_CHANNEL_ID = "status_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 10
        
        private const val MESSAGE_CHANNEL_ID = "message_channel"
        private const val INVITATIONS_CHANNEL_ID = "invitations_channel"
    }

    fun start(coroutineScope: CoroutineScope, startForeground: (Int, Notification) -> Unit) {
//        val pendingIntent = PendingIntent.getActivity(
//            this,
//            0,
//            Intent(this, MainActivity::class.java),
//            PendingIntent.FLAG_IMMUTABLE,
//        )

        registerChannels()

        val fgNotification = NotificationCompat.Builder(context, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_notification)
            .setContentTitle(context.getString(R.string.grapevine_service))
//            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentText(transportController.statusText.value)

        startForeground(FOREGROUND_NOTIFICATION_ID, fgNotification.build())

        val notificationManager = NotificationManagerCompat.from(context)

        coroutineScope.launch {
            transportController.statusText.collect {
                fgNotification.setContentText(it)
                notificationManager.notify(FOREGROUND_NOTIFICATION_ID, fgNotification.build())
            }
        }
    }

    fun stop() {
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
}