package ru.vizbash.grapevine.service.foreground

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.service.ChatService
import ru.vizbash.grapevine.service.profile.ProfileProvider
import ru.vizbash.grapevine.storage.message.Message
import ru.vizbash.grapevine.ui.chat.ChatActivity

@ServiceScoped
class ChatMessageNotifier(
    @ApplicationContext private val context: Context,
    private val chatService: ChatService,
    private val profileProvider: ProfileProvider,
) {
    companion object {
        private const val ACTION_MARK_READ = "ru.vizbash.grapevine.action.ACTION_MARK_READ"
        private const val ACTION_REPLY = "ru.vizbash.grapevine.action.ACTION_REPLY"
        private const val ACTION_DISMISS = "ru.vizbash.grapevine.action.ACTION_DISMISS"

        private const val EXTRA_CHAT_ID = "chat_id"

        private const val KEY_REPLY_TEXT = "reply_text"
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private val messageStyles = mutableMapOf<Long, NotificationCompat.MessagingStyle>()

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_MARK_READ -> {

                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(ACTION_DISMISS)
            addAction(ACTION_MARK_READ)
            addAction(ACTION_REPLY)
        }
        context.registerReceiver(actionReceiver, filter)
    }

    fun unregister() {
        context.unregisterReceiver(actionReceiver)
    }

    private suspend fun addToStyle(msg: Message): NotificationCompat.MessagingStyle {
        val chat = chatService.getChatById(msg.chatId)!!

        val style = messageStyles.getOrPut(msg.chatId) {
            val chatPerson = Person.Builder()
                .setKey(msg.chatId.toString())
                .setName(chat.name)

            chat.photo?.let {
                chatPerson.setIcon(IconCompat.createWithBitmap(it))
            }
            NotificationCompat.MessagingStyle(chatPerson.build())
                .setGroupConversation(chat.isGroup)
                .setConversationTitle(chat.name)
        }

        val person = if (msg.senderId != profileProvider.profile.nodeId) {
            val sender = chatService.resolveNode(msg.senderId)!!

            Person.Builder().apply {
                setKey(msg.senderId.toString())
                setName(sender.username)
                sender.photo?.let {
                    setIcon(IconCompat.createWithBitmap(sender.photo))
                }
            }
        } else {
            Person.Builder().apply {
                setName(profileProvider.profile.username)
                profileProvider.profile.photo?.let {
                    setIcon(IconCompat.createWithBitmap(it))
                }
            }
        }

        style.addMessage(msg.text, msg.timestamp.time, person.build())
        return style
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    suspend fun notify(msg: Message) {
        val style = addToStyle(msg)

        val contentIntent = PendingIntent.getActivity(
            context,
            msg.chatId.toInt(),
            Intent(context, ChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(ChatActivity.EXTRA_CHAT_ID, msg.chatId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val dismissIntent = PendingIntent.getBroadcast(
            context,
            msg.chatId.toInt(),
            Intent(ACTION_DISMISS).apply {
                putExtra(EXTRA_CHAT_ID, msg.chatId)
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val markReadIntent = PendingIntent.getBroadcast(
            context,
            msg.chatId.toInt(),
            Intent(ACTION_MARK_READ).apply {
                putExtra(EXTRA_CHAT_ID, msg.chatId)
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.reply))
            .build()
        val replyIntent = PendingIntent.getBroadcast(
            context,
            msg.chatId.toInt(),
            Intent(ACTION_REPLY).apply {
                putExtra(EXTRA_CHAT_ID, msg.chatId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notif = NotificationCompat.Builder(context, NotificationSender.MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mail)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setDeleteIntent(dismissIntent)
            .addAction(NotificationCompat.Action(
                R.drawable.ic_check,
                context.getString(R.string.mark_as_read),
                markReadIntent,
            ))
            .addAction(NotificationCompat.Action.Builder(
                R.drawable.ic_reply,
                context.getString(R.string.reply),
                replyIntent,
            ).addRemoteInput(remoteInput).build())
            .build()

        notificationManager.notify(msg.chatId.toInt(), notif)
    }
}