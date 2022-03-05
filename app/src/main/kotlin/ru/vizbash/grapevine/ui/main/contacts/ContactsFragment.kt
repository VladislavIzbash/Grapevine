package ru.vizbash.grapevine.ui.main.contacts

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.databinding.FragmentContactsBinding
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import ru.vizbash.grapevine.ui.chat.ChatActivity
import ru.vizbash.grapevine.ui.main.MainViewModel

class ContactsFragment : Fragment() {
    private lateinit var ui: FragmentContactsBinding
    private val model: MainViewModel by activityViewModels()

    private val contactListener = object : ContactAdapter.ContactListener {
        override fun onSelected(contact: ContactEntity) {
            val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CHAT_ID, contact.nodeId)
            }
            startActivity(intent)
        }

        override fun onAccepted(contact: ContactEntity) {
            model.acceptContact(contact)
        }

        override fun onRejected(contact: ContactEntity) {
            model.rejectContact(contact)
        }

        override fun onCanceled(contact: ContactEntity) {
            model.cancelContactInvitation(contact)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ui = FragmentContactsBinding.inflate(inflater, container, false)

        val contactAdapter = ContactAdapter(
            viewLifecycleOwner.lifecycleScope,
            model.service.currentProfile.nodeId,
            contactListener,
        )

        ui.rvContacts.layoutManager = LinearLayoutManager(activity)
        ui.rvContacts.adapter = contactAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                collectContacts(contactAdapter)
            }
        }

        return ui.root
    }

    private suspend fun collectContacts(contactAdapter: ContactAdapter) {
        model.service.contactList.collect { contacts ->
            ui.tvNoContacts.visibility =
                if (contacts.isEmpty()) View.VISIBLE else View.INVISIBLE
            ui.rvContacts.visibility =
                if (contacts.isEmpty()) View.INVISIBLE else View.VISIBLE

            val contactItems = contacts.map { contact ->
                val onlineFlow = model.service.availableNodes.map { nodes ->
                    nodes.any { it.id == contact.nodeId }
                }

                ContactAdapter.ContactItem(
                    contact,
                    onlineFlow,
                    model.service.getLastMessage(contact),
                )
            }
            contactAdapter.submitList(contactItems.toMutableList())
        }
    }
}