package ru.vizbash.grapevine.ui.main.nodes

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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import jp.wasabeef.recyclerview.animators.SlideInUpAnimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.databinding.FragmentNodeListBinding
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.ui.chat.ChatActivity
import ru.vizbash.grapevine.ui.main.MainViewModel

class NodeListFragment : Fragment() {
    private var _ui: FragmentNodeListBinding? = null
    private val ui get() = _ui!!

    private val activityModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _ui = FragmentNodeListBinding.inflate(inflater, container, false)

        val nodeAdapter = NodeAdapter(viewLifecycleOwner.lifecycleScope, {}, true, ::openChat)

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

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }

    private fun updateNodeList(nodes: List<Node>, adapter: NodeAdapter) {
        val showNoNodes = nodes.isEmpty() && activityModel.searchQuery.value.isEmpty()
        ui.noNodesText.visibility = if (showNoNodes) View.VISIBLE else View.INVISIBLE
        ui.nodeList.visibility = if (nodes.isEmpty()) View.INVISIBLE else View.VISIBLE

        val items = nodes.map { NodeAdapter.NodeItem(it, activityModel.fetchPhotoAsync(it)) }
        adapter.submitList(items)
    }

    private fun openChat(node: Node) {
        lifecycleScope.launch(Dispatchers.Default) {
            activityModel.createDialogChat(node)

            val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CHAT_ID, node.id)
            }
            startActivity(intent)
        }
    }
}