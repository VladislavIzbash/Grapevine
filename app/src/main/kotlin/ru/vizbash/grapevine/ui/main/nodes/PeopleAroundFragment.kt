package ru.vizbash.grapevine.ui.main.nodes

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
import jp.wasabeef.recyclerview.animators.SlideInUpAnimator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.databinding.FragmentPeopleAroundBinding
import ru.vizbash.grapevine.ui.main.MainViewModel

class PeopleAroundFragment : Fragment() {
    private lateinit var ui: FragmentPeopleAroundBinding
    private val model: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        ui = FragmentPeopleAroundBinding.inflate(inflater, container, false)

        val nodeAdapter = NodeAdapter(lifecycleScope, model::addToContacts)

        ui.rvNodes.layoutManager = LinearLayoutManager(activity)
        ui.rvNodes.adapter = nodeAdapter
        ui.rvNodes.itemAnimator = SlideInUpAnimator()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    collectNodes(nodeAdapter)
                }
                launch {
                    collectContacts(nodeAdapter)
                }
            }
        }

        return ui.root
    }

    private suspend fun collectNodes(adapter: NodeAdapter) {
        model.availableNodes.collect { nodes ->
            ui.tvNoNodes.visibility = if (nodes.isEmpty()) View.VISIBLE else View.INVISIBLE
            ui.rvNodes.visibility = if (nodes.isEmpty()) View.INVISIBLE else View.VISIBLE

            val contacts = model.contacts.first()

            adapter.items = nodes.map { node ->
                NodeAdapter.NodeItem(
                    node,
                    model.fetchPhotoAsync(node),
                    contacts.any { it.nodeId == node.id },
                )
            }
        }
    }

    private suspend fun collectContacts(adapter: NodeAdapter) {
        model.contacts.collect { contacts ->
            adapter.items = model.availableNodes.value.map { node ->
                NodeAdapter.NodeItem(
                    node,
                    model.fetchPhotoAsync(node),
                    contacts.any { it.nodeId == node.id },
                )
            }
        }
    }
}