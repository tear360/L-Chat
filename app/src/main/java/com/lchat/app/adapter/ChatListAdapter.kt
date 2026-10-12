package com.lchat.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lchat.app.databinding.ItemChatListBinding
import com.lchat.app.model.Conversation
import com.lchat.app.model.GroupItem
import com.lchat.app.model.SnapItem
import com.lchat.app.util.AppUtils

sealed class ChatRow {
    data class Snap(val snap: SnapItem) : ChatRow()
    data class Chat(val conversation: Conversation) : ChatRow()
    data class Group(val group: GroupItem) : ChatRow()
}

class ChatListAdapter(
    private val onSnapClick: (SnapItem) -> Unit,
    private val onChatClick: (Conversation) -> Unit,
    private val onGroupClick: (GroupItem) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.VH>() {

    private val rows = ArrayList<ChatRow>()

    fun setRows(newRows: List<ChatRow>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is ChatRow.Snap -> 0
        is ChatRow.Chat -> 1
        is ChatRow.Group -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChatListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(rows[position])

    inner class VH(private val binding: ItemChatListBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: ChatRow) {
            when (row) {
                is ChatRow.Snap -> bindSnap(row.snap)
                is ChatRow.Chat -> bindChat(row.conversation)
                is ChatRow.Group -> bindGroup(row.group)
            }
        }

        private fun bindSnap(snap: SnapItem) {
            binding.avatarCircle.visibility = android.view.View.GONE
            binding.avatarInitials.visibility = android.view.View.GONE
            binding.snapBadge.visibility = android.view.View.VISIBLE
            binding.snapBadgeIcon.visibility = android.view.View.VISIBLE

            binding.itemName.text = snap.fromName
            binding.itemSubtitle.text = "New Snap"
            binding.itemSubtitle.setTextColor(
                binding.root.context.getColor(com.lchat.app.R.color.red)
            )
            binding.itemSubtitle.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.itemName.setTypeface(null, android.graphics.Typeface.NORMAL)
            binding.itemTime.text = AppUtils.formatTime(snap.createdAt)
            binding.root.setOnClickListener { onSnapClick(snap) }
        }

        private fun bindChat(conversation: Conversation) {
            binding.avatarCircle.visibility = android.view.View.VISIBLE
            binding.avatarInitials.visibility = android.view.View.VISIBLE
            binding.snapBadge.visibility = android.view.View.GONE
            binding.snapBadgeIcon.visibility = android.view.View.GONE

            val color = AppUtils.avatarColor(conversation.otherUid)
            binding.avatarCircle.background = AppUtils.circle(color)
            binding.avatarInitials.text = AppUtils.initials(conversation.name)
            binding.avatarInitials.setTextColor(AppUtils.initialsTextColor(color))

            binding.itemName.text = conversation.name
            if (conversation.unread) {
                binding.itemSubtitle.text = "New Chat"
                binding.itemSubtitle.setTextColor(
                    binding.root.context.getColor(com.lchat.app.R.color.blue)
                )
                binding.itemSubtitle.setTypeface(null, android.graphics.Typeface.BOLD)
                binding.itemName.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                binding.itemSubtitle.text = conversation.lastMessage
                binding.itemSubtitle.setTextColor(
                    binding.root.context.getColor(com.lchat.app.R.color.grayText)
                )
                binding.itemSubtitle.setTypeface(null, android.graphics.Typeface.NORMAL)
                binding.itemName.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
            binding.itemTime.text = AppUtils.formatTime(conversation.lastMessageAt)
            binding.root.setOnClickListener { onChatClick(conversation) }
        }

        private fun bindGroup(group: GroupItem) {
            binding.avatarCircle.visibility = android.view.View.VISIBLE
            binding.avatarInitials.visibility = android.view.View.VISIBLE
            binding.snapBadge.visibility = android.view.View.GONE
            binding.snapBadgeIcon.visibility = android.view.View.GONE

            val color = AppUtils.avatarColor(group.groupId)
            binding.avatarCircle.background = AppUtils.circle(color)
            binding.avatarInitials.text = AppUtils.initials(group.name)
            binding.avatarInitials.setTextColor(AppUtils.initialsTextColor(color))

            binding.itemName.text = group.name
            if (group.lastMessage.isNotEmpty()) {
                binding.itemSubtitle.text = group.lastMessage
            } else {
                binding.itemSubtitle.text = "Group"
            }
            binding.itemSubtitle.setTextColor(
                binding.root.context.getColor(com.lchat.app.R.color.grayText)
            )
            binding.itemSubtitle.setTypeface(null, android.graphics.Typeface.NORMAL)
            binding.itemName.setTypeface(null, android.graphics.Typeface.NORMAL)
            binding.itemTime.text = if (group.lastMessageAt > 0) AppUtils.formatTime(group.lastMessageAt) else ""
            binding.root.setOnClickListener { onGroupClick(group) }
        }
    }
}
