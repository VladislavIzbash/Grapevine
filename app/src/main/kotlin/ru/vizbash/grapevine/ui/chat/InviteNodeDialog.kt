package ru.vizbash.grapevine.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.databinding.DialogInviteBinding
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.ui.main.MainViewModel
import ru.vizbash.grapevine.ui.main.nodes.NodeAdapter

@AndroidEntryPoint
class InviteNodeDialog : BottomSheetDialogFragment() {
    companion object {
        const val KEY_NODE_ID = "node_id"
    }

    private val mainModel: MainViewModel by viewModels()
    private val activityModel: ChatMembersViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val nodeAdapter = NodeAdapter(
            viewLifecycleOwner.lifecycleScope,
            ::onNodeClicked,
            false,
            {},
        )

        val ui = DialogInviteBinding.inflate(inflater, container, false)

        ui.nodeList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = nodeAdapter
            addItemDecoration(DividerItemDecoration(
                requireContext(),
                DividerItemDecoration.VERTICAL),
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainModel.nodeList.collect {
                    val members = activityModel.chatMembers.first()

                    val items = it.filter { node ->
                        members.find { it.id == node.id } == null
                    }.map {
                        NodeAdapter.NodeItem(it, mainModel.fetchPhotoAsync(it))
                    }

                    ui.noPeopleAround.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    ui.nodeList.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE

                    nodeAdapter.submitList(items)
                }
            }
        }

        return ui.root
    }

    private fun onNodeClicked(node: Node) {
        setFragmentResult(KEY_NODE_ID, bundleOf(KEY_NODE_ID to node.id))
        dismiss()
    }
}