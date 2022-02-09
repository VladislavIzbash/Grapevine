package ru.vizbash.grapevine.ui.main

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
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

        ui.rvNodes.layoutManager = LinearLayoutManager(activity)
        ui.rvNodes.adapter = NodeAdapter(model.nodeEntries, viewLifecycleOwner.lifecycleScope)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.nodeEntries.collect {
                        ui.tvNoNodes.visibility = if (it.isEmpty()) View.VISIBLE else View.INVISIBLE
                        ui.rvNodes.visibility = if (it.isEmpty()) View.INVISIBLE else View.VISIBLE
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