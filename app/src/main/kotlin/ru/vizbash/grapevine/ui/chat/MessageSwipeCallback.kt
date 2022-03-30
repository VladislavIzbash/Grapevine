package ru.vizbash.grapevine.ui.chat

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import ru.vizbash.grapevine.storage.message.Message

class MessageSwipeCallback(
    private val listener: (Message) -> Unit,
) : ItemTouchHelper.Callback() {

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ) = makeMovementFlags(0, ItemTouchHelper.END)

    override fun isLongPressDragEnabled() = false

    override fun isItemViewSwipeEnabled() = true

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        viewHolder.bindingAdapter?.notifyItemChanged(position)

        val item = (viewHolder as MessageAdapter.ViewHolder).boundItem ?: return
        listener(item)
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.2F

    override fun getSwipeEscapeVelocity(defaultValue: Float) = defaultValue / 2

    override fun getAnimationDuration(
        recyclerView: RecyclerView,
        animationType: Int,
        animateDx: Float,
        animateDy: Float,
    ) = 50L
}