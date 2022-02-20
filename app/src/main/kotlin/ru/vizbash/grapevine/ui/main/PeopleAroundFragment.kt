package ru.vizbash.grapevine.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageButton
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
import com.mikepenz.fastadapter.binding.BindingViewHolder
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil
import com.mikepenz.fastadapter.listeners.ClickEventHook
import jp.wasabeef.recyclerview.animators.LandingAnimator
import jp.wasabeef.recyclerview.animators.SlideInUpAnimator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.FragmentPeopleAroundBinding
import ru.vizbash.grapevine.databinding.ItemNodeBinding
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.SourceType

class PeopleAroundFragment : Fragment() {
    private lateinit var ui: FragmentPeopleAroundBinding
    private val model: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        ui = FragmentPeopleAroundBinding.inflate(inflater, container, false)

        val nodeAdapter = ItemAdapter<NodeItem>()
        val fastNodeAdapter = FastAdapter.with(nodeAdapter).apply {
            setHasStableIds(true)
        }

        fastNodeAdapter.addEventHook(NodeItemClickHook())

        ui.rvNodes.layoutManager = LinearLayoutManager(activity)
        ui.rvNodes.adapter = fastNodeAdapter
        ui.rvNodes.itemAnimator = SlideInUpAnimator()

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.networkError.filterNotNull().collect {
                        Snackbar.make(ui.root, R.string.error_sending_request, Snackbar.LENGTH_LONG).apply {
                            setTextColor(requireActivity().getColor(R.color.error))
                            show()
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
            FastAdapterDiffUtil.set(adapter, items)
        }
    }

    private suspend fun collectContacts(adapter: ItemAdapter<NodeItem>) {
        model.contacts.collect { contacts ->
            val items = model.availableNodes.value.map { node ->
                NodeItem(
                    node,
                    contacts.any { it.nodeId == node.id },
                    false,
                )
            }
            FastAdapterDiffUtil.set(adapter, items)
        }
    }

    private inner class NodeItem(
        val node: Node,
        val isContact: Boolean,
        private val animate: Boolean = true,
    ) : AbstractBindingItem<ItemNodeBinding>() {
        var fetchJob: Job? = null

        override val type = R.id.node_item

        override var identifier: Long
            get() = node.id
            set(_) {  }

        override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemNodeBinding {
            return ItemNodeBinding.inflate(inflater, parent, false)
        }

        override fun bindView(binding: ItemNodeBinding, payloads: List<Any>) {
            fetchJob = lifecycleScope.launch {
                val photo = model.fetchPhoto(node)

                binding.ivPhoto.post {
                    if (photo != null) {
                        binding.ivPhoto.setImageBitmap(photo)
                    } else {
                        binding.ivPhoto.setImageResource(R.drawable.avatar_placeholder)
                    }
                }
            }

            binding.tvUsername.text = node.username
            binding.ivSource.setImageResource(when (node.primarySource!!) {
                SourceType.BLUETOOTH -> R.drawable.ic_bluetooth
                SourceType.WIFI -> R.drawable.ic_wifi
            })

            binding.buttonAddContact.visibility = if (isContact) View.INVISIBLE else View.VISIBLE

//            if (animate) {
//                binding.root.animation = AnimationUtils.loadAnimation(
//                    binding.root.context,
//                    R.anim.node_item,
//                )
//            }
        }

        override fun unbindView(binding: ItemNodeBinding) {
            fetchJob?.cancel()
        }

        override fun equals(other: Any?) = other is NodeItem
                && other.node == node
                && other.isContact == isContact

        override fun hashCode() = super.hashCode() + node.hashCode() + isContact.hashCode()

    }

    private inner class NodeItemClickHook : ClickEventHook<NodeItem>() {
        override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
            return if (viewHolder is BindingViewHolder<*>) {
                viewHolder.itemView.findViewById<ImageButton>(R.id.buttonAddContact)
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
}