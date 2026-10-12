package com.lchat.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lchat.app.databinding.ItemUserBinding
import com.lchat.app.model.User
import com.lchat.app.util.AppUtils

class UsersAdapter : RecyclerView.Adapter<UsersAdapter.VH>() {

    private val items = ArrayList<User>()
    val selected = LinkedHashSet<String>()
    var onSelectionChanged: ((Int) -> Unit)? = null

    fun setItems(users: List<User>) {
        items.clear()
        items.addAll(users)
        notifyDataSetChanged()
    }

    fun selectedUids(): List<String> = selected.toList()

    fun clearSelection() {
        selected.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    private fun toggle(user: User) {
        if (selected.contains(user.uid)) selected.remove(user.uid) else selected.add(user.uid)
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selected.size)
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
            binding.userCheck.isChecked = selected.contains(user.uid)
            binding.root.isActivated = binding.userCheck.isChecked

            binding.root.setOnClickListener { toggle(user) }
        }
    }
}
