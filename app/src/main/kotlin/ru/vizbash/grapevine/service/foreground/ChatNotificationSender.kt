package ru.vizbash.grapevine.service.foreground

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.service.ChatService
import ru.vizbash.grapevine.service.profile.ProfileProvider
import ru.vizbash.grapevine.storage.chat.Chat
import ru.vizbash.grapevine.storage.message.Message
import ru.vizbash.grapevine.ui.chat.ChatActivity
import javax.inject.Inject

@ServiceScoped
class ChatNotificationSender @Inject constructor(
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

        private const val INVITATION_ID_OFFSET = 10
        private const val KICK_ID_OFFSET = 20
    }

    interface MessageActionListener {
        fun onMarkAsRead(chatId: Long)

        fun onReply(chatId: Long, text: String)
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    private val messageStyles = mutableMapOf<Long, NotificationCompat.MessagingStyle>()

    private val roundBitmapCache = mutableMapOf<Long, Bitmap>()

    private lateinit var messageActionListener: MessageActionListener

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val chatId = intent.getLongExtra(EXTRA_CHAT_ID, -1)

            when (intent.action) {
                ACTION_MARK_READ -> {
                    messageActionListener.onMarkAsRead(chatId)
                    notificationManager.cancel(chatId.toInt())
                }
                ACTION_REPLY -> {
                    RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY_TEXT)?.let {
                        messageActionListener.onReply(chatId, it.toString())
                    }
                }
                ACTION_DISMISS -> messageStyles.remove(chatId)
            }
        }
    }

    fun register(listener: MessageActionListener) {
        messageActionListener = listener

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

    private fun makeRoundBitmap(nodeId: Long, src: Bitmap) = roundBitmapCache.getOrPut(nodeId) {
        val output = Bitmap.createBitmap(src.width, src.height, src.config)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            isAntiAlias = true
            color = 0xff424242.toInt()
        }
        val rect = Rect(0, 0, src.width, src.height)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(src.width / 2F, src.height / 2F, src.width / 2F, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, rect, rect, paint)

        output
    }

    private suspend fun addToStyle(msg: Message): NotificationCompat.MessagingStyle {
        val chat = chatService.getChat(msg.chatId)!!

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
                    val bitmap = makeRoundBitmap(sender.id, it)
                    setIcon(IconCompat.createWithBitmap(bitmap))
                }
            }
        } else {
            Person.Builder().apply {
                setName(profileProvider.profile.username)
                profileProvider.profile.photo?.let {
                    val bitmap = makeRoundBitmap(profileProvider.profile.nodeId, it)
                    setIcon(IconCompat.createWithBitmap(bitmap))
                }
            }
        }

        style.addMessage(msg.text, msg.timestamp.time, person.build())
        return style
    }
    
    private fun createChatContentIntent(chatId: Long) = PendingIntent.getActivity(
        context,
        chatId.toInt(),
        Intent(context, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(ChatActivity.EXTRA_CHAT_ID, chatId)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun cancelChatNotifications(chatId: Long) {
        messageStyles.remove(chatId)
        notificationManager.cancel(chatId.toInt())
        notificationManager.cancel(chatId.toInt() + INVITATION_ID_OFFSET)
        notificationManager.cancel(chatId.toInt() + KICK_ID_OFFSET)
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    suspend fun notify(msg: Message) {
        val style = addToStyle(msg)
        
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
            .setContentIntent(createChatContentIntent(msg.chatId))
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
    
    fun notifyInvitation(chat: Chat) {
        val notif = NotificationCompat.Builder(context, NotificationSender.MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_person_add)
            .setContentTitle(chat.name)
            .setContentText(context.getString(R.string.chat_invitation_text))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(createChatContentIntent(chat.id))
            .build()

        notificationManager.notify(chat.id.toInt() + INVITATION_ID_OFFSET, notif)
    }

    fun notifyKick(chat: Chat) {
        val notif = NotificationCompat.Builder(context, NotificationSender.MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_leave)
            .setContentTitle(chat.name)
            .setContentText(context.getString(R.string.kicked_from_chat))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(createChatContentIntent(chat.id))
            .build()

        notificationManager.notify(chat.id.toInt() + KICK_ID_OFFSET, notif)
    }
}