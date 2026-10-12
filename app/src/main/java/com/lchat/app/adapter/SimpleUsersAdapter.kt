package com.lchat.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lchat.app.databinding.ItemUserBinding
import com.lchat.app.model.User
import com.lchat.app.util.AppUtils

class SimpleUsersAdapter(
    private val onUserClick: (User) -> Unit
) : RecyclerView.Adapter<SimpleUsersAdapter.VH>() {

    private val items = ArrayList<User>()

    fun setUsers(users: List<User>) {
        items.clear()
        items.addAll(users)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            val color = AppUtils.avatarColor(user.uid)
            binding.userAvatarCircle.background = AppUtils.circle(color)
            binding.userInitials.text = AppUtils.initials(user.username.ifEmpty { user.email })
            binding.userInitials.setTextColor(AppUtils.initialsTextColor(color))
            binding.userName.text = user.username
            binding.userEmail.text = user.email
            com.lchat.app.friends.FriendsActivity.showAvatar(
                binding.avatarImage, binding.userInitials, user.photoData
            )
            binding.userCheck.visibility = View.GONE

            binding.root.setOnClickListener { onUserClick(user) }
        }
    }
}