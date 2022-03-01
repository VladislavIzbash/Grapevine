package ru.vizbash.grapevine

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.network.GrapevineNetwork
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import ru.vizbash.grapevine.storage.messages.MessageEntity
import javax.inject.Inject

@AndroidEntryPoint
class GrapevineService : Service() {
    @Inject lateinit var profileService: ProfileService
    @Inject lateinit var grapevineNetwork: GrapevineNetwork

    private var running = false
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) {
            return START_STICKY
        }

        running = true

        Log.i(TAG, "Started Grapevine service")

        coroutineScope.run {
            launch { receiveInvitations() }
            launch { receiveInvitationAnswers() }
            launch { receiveTextMessages() }
            launch { receiveReadConfirmations() }
        }

        return START_STICKY
    }

//    private suspend fun CoroutineScope.shareMessageFiles() {
//        val myId = profileService.profile.entity.nodeId
//        profileService.getAllMessages().collect { messages ->
//            for (msg in messages.filter { it.senderId == myId && it.file != null }) {
//                launch {
//                    grapevineNetwork.startFileSharing(msg.id, msg.file!!) { chunkSize ->
//                        contentResolver.openFile(msg.file.)
//                    }
//                }
//            }
//        }
//    }

    private suspend fun receiveInvitations() {
        grapevineNetwork.contactInvitations.collect { node ->
            val photo = try {
                grapevineNetwork.fetchNodePhoto(node)
            } catch (e: GVException) {
                e.printStackTrace()
                null
            }

            profileService.addContact(node, photo, ContactEntity.State.INGOING)
        }
    }

    private suspend fun receiveInvitationAnswers() {
        grapevineNetwork.contactInvitationAnswers.collect { (node, accepted) ->
            profileService.getContact(node.id)?.let {
                if (accepted) {
                    profileService.setContactState(it, ContactEntity.State.ACCEPTED)
                } else {
                    profileService.deleteContact(it) // TODO: notify user
                }
            }
        }
    }

    private suspend fun receiveTextMessages() {
        grapevineNetwork.textMessages.collect { (msg, node) ->
            profileService.getContact(node.id)?.let {
                profileService.addReceivedMessage(it, msg)
            }
        } // TODO: show notification
    }

    private suspend fun receiveReadConfirmations() {
        grapevineNetwork.readConfirmations.collect { msgId ->
            profileService.setMessageState(msgId, MessageEntity.State.READ)
        }
    }

    override fun onDestroy() {
        running = false
        coroutineScope.coroutineContext.cancelChildren()
        Log.i(TAG, "Stopped Grapevine service")
    }

    override fun onBind(intent: Intent): IBinder? = null
}