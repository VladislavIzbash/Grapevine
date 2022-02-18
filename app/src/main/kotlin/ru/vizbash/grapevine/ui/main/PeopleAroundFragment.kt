package ru.vizbash.grapevine.ui.main

import android.graphics.Bitmap
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
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.binding.AbstractBindingItem
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil
import com.mikepenz.fastadapter.listeners.ClickEventHook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.FragmentPeopleAroundBinding
import ru.vizbash.grapevine.databinding.ItemNodeBinding
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.SourceType

class PeopleAroundFragment : Fragment() {
    private var _ui: FragmentPeopleAroundBinding? = null
    private val ui get() = _ui!!

    private val model: MainViewModel by activityViewModels()

    private data class NodeItem(
        val node: Node,
        val isContact: Boolean,
        val photo: Bitmap? = null
    ) : AbstractBindingItem<ItemNodeBinding>() {

        override val type = R.id.node_item_id

        override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemNodeBinding {
            return ItemNodeBinding.inflate(inflater, parent, false)
        }

        override fun bindView(binding: ItemNodeBinding, payloads: List<Any>) {
            if (photo != null) {
                binding.ivPhoto.setImageBitmap(photo)
            } else {
                binding.ivPhoto.setImageResource(R.drawable.avatar_placeholder)
            }
            binding.tvUsername.text = node.username
            binding.ivSource.setImageResource(when (node.primarySource!!) {
                SourceType.BLUETOOTH -> R.drawable.ic_bluetooth
                SourceType.WIFI -> R.drawable.ic_wifi
            })

            binding.buttonAddContact.visibility = if (isContact) View.INVISIBLE else View.VISIBLE
        }
    }

    private inner class NodeItemClickHook : ClickEventHook<NodeItem>() {
        override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
            return if (viewHolder is FastAdapter.ViewHolder<*>) {
                viewHolder.itemView.findViewById(R.id.buttonAddContact)
            } else {
                null
            }
        }

        override fun onClick(
            v: View,
            position: Int,
            fastAdapter: FastAdapter<NodeItem>,
            item: NodeItem
        ) {
            model.addToContacts(item.node)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _ui = FragmentPeopleAroundBinding.inflate(inflater, container, false)

        val nodeAdapter = ItemAdapter<NodeItem>()
        val fastNodeAdapter = FastAdapter.with(nodeAdapter)

        ui.rvNodes.layoutManager = LinearLayoutManager(activity)
        ui.rvNodes.adapter = fastNodeAdapter

        fastNodeAdapter.addEventHook(object : ClickEventHook<NodeItem>() {

            override fun onClick(
                v: View,
                position: Int,
                fastAdapter: FastAdapter<NodeItem>,
                item: NodeItem,
            ) {

            }
        })

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.networkError.collect {
                        it.ifPresent {
                            Snackbar.make(ui.root, R.string.error_sending_request, Snackbar.LENGTH_LONG).apply {
                                setTextColor(requireActivity().getColor(R.color.error))
                                show()
                            }
                        }
                    }
                }
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

    private suspend fun collectNodes(adapter: ItemAdapter<NodeItem>) {
        var fetchScope: CoroutineScope? = null

        model.availableNodes.collect { nodes ->
            ui.tvNoNodes.visibility = if (nodes.isEmpty()) View.VISIBLE else View.INVISIBLE
            ui.rvNodes.visibility = if (nodes.isEmpty()) View.INVISIBLE else View.VISIBLE

            val contacts = model.contacts.first()

            val items = nodes.map { node ->
                NodeItem(
                    node,
                    contacts.any { it.nodeId == node.id },
                )
            }
            FastAdapterDiffUtil.set(adapter, items) // TODO

            fetchScope?.coroutineContext?.cancelChildren()
            fetchScope = coroutineScope {
                for (node in nodes) {
                    launch {
                        val pos = adapter.adapterItems.indexOfFirst { it.node == node }
                        val item = adapter.itemList[pos]!!

                        adapter.set(pos, NodeItem(
                            item.node,
                            item.isContact,
                            model.fetchPhoto(node),
                        ))
                    }
                }
                this
            }
        }
    }

    private suspend fun collectContacts(adapter: ItemAdapter<NodeItem>) {
        model.contacts.collect { contacts ->
            val items = adapter.adapterItems.map { item ->
                NodeItem(
                    item.node,
                    contacts.any { it.nodeId == item.node.id },
                    item.photo
                )
            }
            FastAdapterDiffUtil.set(adapter, items)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }
}