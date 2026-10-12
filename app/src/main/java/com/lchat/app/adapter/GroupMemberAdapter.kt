package com.lchat.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lchat.app.databinding.ItemGroupMemberBinding
import com.lchat.app.model.User
import com.lchat.app.util.AppUtils

class GroupMemberAdapter(
    private val onRemove: (User) -> Unit,
    private val canManage: Boolean
) : RecyclerView.Adapter<GroupMemberAdapter.VH>() {

    private val items = ArrayList<User>()
    private var ownerUid = ""

    fun setMembers(users: List<User>, ownerUid: String) {
        this.ownerUid = ownerUid
        items.clear()
        items.addAll(users)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGroupMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val binding: ItemGroupMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            val color = AppUtils.avatarColor(user.uid)
            binding.avatarCircle.background = AppUtils.circle(color)
            binding.initials.text = AppUtils.initials(user.username)
            binding.initials.setTextColor(AppUtils.initialsTextColor(color))
            binding.name.text = user.username

            val isOwner = user.uid == ownerUid
            binding.adminBadge.visibility = if (isOwner) View.VISIBLE else View.GONE
            binding.removeButton.visibility =
                if (canManage && !isOwner) View.VISIBLE else View.GONE
            binding.removeButton.setOnClickListener { onRemove(user) }
        }
    }
}