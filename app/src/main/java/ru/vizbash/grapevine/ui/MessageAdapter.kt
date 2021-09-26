package ru.vizbash.grapevine.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.view.setMargins
import androidx.recyclerview.widget.RecyclerView
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ItemMessageBinding
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(private val items: List<MessageItem>) :
    RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_IN = 0
        private const val VIEW_TYPE_OUT = 1
    }

    open class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateFormat = SimpleDateFormat("H:mm", Locale.getDefault())
        protected var ui = ItemMessageBinding.bind(view)

        fun bind(message: MessageItem) {
            ui.messageText.text = message.text
            ui.messageTime.text = dateFormat.format(message.date)
        }
    }

    class ViewHolderOut(view: View) : ViewHolder(view) {
        init {
            ui.messageLayout.layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)

        return when (viewType) {
            VIEW_TYPE_IN -> ViewHolder(view)
            VIEW_TYPE_OUT -> ViewHolderOut(view)
            else -> throw IllegalArgumentException("Invalid viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = when (items[position].isOut) {
        true -> VIEW_TYPE_OUT
        false -> VIEW_TYPE_IN
    }
}