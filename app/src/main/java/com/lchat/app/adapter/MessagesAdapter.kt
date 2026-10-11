package com.lchat.app.adapter

import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lchat.app.R
import com.lchat.app.databinding.ItemMessageBinding
import com.lchat.app.model.ChatMessage

class MessagesAdapter(private val myUid: String) : RecyclerView.Adapter<MessagesAdapter.VH>() {

    private val items = ArrayList<ChatMessage>()

    fun addMessage(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.size - 1)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            val mine = message.fromUid == myUid

            binding.messageText.text = message.text
            binding.messageText.setBackgroundResource(
                if (mine) R.drawable.bg_bubble_mine else R.drawable.bg_bubble_theirs
            )
            binding.messageText.typeface = if (mine) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

            binding.root.gravity = if (mine) Gravity.END else Gravity.START
        }
    }
}
