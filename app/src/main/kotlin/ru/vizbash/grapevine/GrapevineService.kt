package ru.vizbash.grapevine

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.network.GrapevineNetwork
import ru.vizbash.grapevine.storage.contacts.ContactEntity
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

        coroutineScope.launch {
            grapevineNetwork.contactInvitations.collect { node ->
                try {
                    profileService.addContact(
                        node,
                        grapevineNetwork.fetchNodePhoto(node),
                        ContactEntity.State.INGOING,
                    )
                } catch (e: GVException) {
                    e.printStackTrace()
                }
            }
        }

        coroutineScope.launch {
            grapevineNetwork.contactInvitationAnswers.collect { (node, accepted) ->
                profileService.contactList.first().find { it.nodeId == node.id }?.let {
                    if (accepted) {
                        profileService.setContactState(it, ContactEntity.State.ACCEPTED)
                    } else {
                        profileService.deleteContact(it) // TODO: notify user
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        coroutineScope.coroutineContext.cancelChildren()
    }

    override fun onBind(intent: Intent): IBinder? = null
}