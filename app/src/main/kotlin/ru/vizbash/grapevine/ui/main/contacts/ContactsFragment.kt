package ru.vizbash.grapevine.ui.main.contacts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.databinding.FragmentContactsBinding
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import ru.vizbash.grapevine.ui.main.MainViewModel

class ContactsFragment : Fragment() {
    private lateinit var ui: FragmentContactsBinding
    private val model: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ui = FragmentContactsBinding.inflate(inflater, container, false)

        val contactAdapter = ContactAdapter(object : ContactAdapter.ContactListener {
            override fun onSelected(contact: ContactEntity) {
                TODO("Not yet implemented")
            }

            override fun onAccepted(contact: ContactEntity) {
                model.answerContactInvitation(contact, true)
            }

            override fun onRejected(contact: ContactEntity) {
                model.answerContactInvitation(contact, false)
            }

            override fun onCanceled(contact: ContactEntity) {
                model.cancelContactInvitation(contact)
            }
        })

        ui.rvContacts.layoutManager = LinearLayoutManager(activity)
        ui.rvContacts.adapter = contactAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            model.contacts.flowWithLifecycle(viewLifecycleOwner.lifecycle).collect { contacts ->
                ui.tvNoContacts.visibility =
                    if (contacts.isEmpty()) View.VISIBLE else View.INVISIBLE
                ui.rvContacts.visibility =
                    if (contacts.isEmpty()) View.INVISIBLE else View.VISIBLE

                val nodes = model.availableNodes.value

                contactAdapter.items = contacts.map { contact ->
                    ContactAdapter.ContactItem(
                        contact,
                        nodes.any { it.id == contact.nodeId },
                    )
                }
            }
        }

        return ui.root
    }
}