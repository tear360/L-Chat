package com.lchat.app.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lchat.app.R
import com.lchat.app.databinding.ItemStoryBinding
import com.lchat.app.model.StoryGroup
import com.lchat.app.util.AppUtils

sealed class StoryRow {
    object Mine : StoryRow()
    data class Group(val group: StoryGroup) : StoryRow()
}

class StoryListAdapter(
    private val myName: String,
    private val myUid: String,
    private val onMyStoryClick: () -> Unit,
    private val onGroupClick: (StoryGroup) -> Unit
) : RecyclerView.Adapter<StoryListAdapter.VH>() {

    private val rows = ArrayList<StoryRow>()

    fun setRows(newRows: List<StoryRow>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is StoryRow.Mine -> 0
        is StoryRow.Group -> 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemStoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(rows[position])

    inner class VH(private val binding: ItemStoryBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: StoryRow) {
            when (row) {
                is StoryRow.Mine -> bindMine()
                is StoryRow.Group -> bindGroup(row.group)
            }
        }

        private fun bindMine() {
            val color = AppUtils.avatarColor(myUid)
            binding.storyAvatarBg.background = AppUtils.circle(color)
            binding.storyInitials.text = AppUtils.initials(myName)
            binding.storyInitials.setTextColor(AppUtils.initialsTextColor(color))
            binding.storyRing.visibility = View.GONE
            binding.addBadge.visibility = View.VISIBLE
            binding.storyName.text = "My Story"
            binding.storySubtitle.text = "Tap to add to Story"
            binding.root.setOnClickListener { onMyStoryClick() }
        }

        private fun bindGroup(group: StoryGroup) {
            val color = AppUtils.avatarColor(group.fromUid)
            binding.storyAvatarBg.background = AppUtils.circle(color)
            binding.storyInitials.text = AppUtils.initials(group.fromName)
            binding.storyInitials.setTextColor(AppUtils.initialsTextColor(color))
            binding.storyRing.visibility = View.VISIBLE
            binding.storyRing.background = AppUtils.ring(
                if (group.viewed) Color.parseColor("#DDDDDD") else Color.parseColor("#FFFC00"),
                2.5f
            )
            binding.addBadge.visibility = View.GONE
            binding.storyName.text = group.fromName
            val newest = group.stories.maxOfOrNull { it.createdAt } ?: 0L
            binding.storySubtitle.text =
                "${group.stories.size} snap${if (group.stories.size > 1) "s" else ""} • ${AppUtils.formatTime(newest)}"
            binding.root.setOnClickListener { onGroupClick(group) }
        }
    }
}
