package ru.vizbash.grapevine.ui.main

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
import kotlinx.coroutines.*
import ru.vizbash.grapevine.databinding.FragmentPeopleAroundBinding

class PeopleAroundFragment : Fragment() {
    private var _ui: FragmentPeopleAroundBinding? = null
    private val ui get() = _ui!!

    private val model: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _ui = FragmentPeopleAroundBinding.inflate(inflater, container, false)

        val nodeAdapter = NodeAdapter(model.nodes.value)

        ui.rvNodes.layoutManager = LinearLayoutManager(activity)
        ui.rvNodes.adapter = nodeAdapter

        var fetchScope: CoroutineScope? = null

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.nodes.collect { nodes ->
                        ui.tvNoNodes.visibility = if (nodes.isEmpty()) View.VISIBLE else View.INVISIBLE
                        ui.rvNodes.visibility = if (nodes.isEmpty()) View.INVISIBLE else View.VISIBLE

                        nodeAdapter.update(nodes)

                        fetchScope?.coroutineContext?.cancelChildren()
                        fetchScope = coroutineScope {
                            for (node in nodes) {
                                launch {
                                   nodeAdapter.setPhoto(node, model.fetchPhoto(node))
                                }
                            }
                            this
                        }
                    }
                }
            }
        }

        return ui.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }
}