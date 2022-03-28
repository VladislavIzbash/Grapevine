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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import jp.wasabeef.recyclerview.animators.SlideInUpAnimator
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.databinding.FragmentNodeListBinding
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.storage.chat.Chat
import ru.vizbash.grapevine.ui.main.MainViewModel

class NodeListFragment : Fragment() {
    private lateinit var ui: FragmentNodeListBinding
    private val activityModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ui = FragmentNodeListBinding.inflate(inflater, container, false)

        val nodeAdapter = NodeAdapter(viewLifecycleOwner.lifecycleScope, {}, ::openChat)

        val decoration = DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)

        ui.nodeList.layoutManager = LinearLayoutManager(requireContext())
        ui.nodeList.itemAnimator = SlideInUpAnimator()
        ui.nodeList.addItemDecoration(decoration)
        ui.nodeList.adapter = nodeAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                activityModel.nodeList.collect { updateNodeList(it, nodeAdapter) }
            }
        }

        return ui.root
    }

    private fun updateNodeList(nodes: List<Node>, adapter: NodeAdapter) {
        ui.noNodesText.visibility = if (nodes.isEmpty()) View.VISIBLE else View.INVISIBLE
        ui.nodeList.visibility = if (nodes.isEmpty()) View.INVISIBLE else View.VISIBLE

        val items = nodes.map { NodeAdapter.NodeItem(it, activityModel.fetchPhotoAsync(it)) }
        adapter.submitList(items)
    }

    private fun openChat(node: Node) {
        activityModel.createDialogChat(node)
    }
}