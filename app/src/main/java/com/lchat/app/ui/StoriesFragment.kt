package com.lchat.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.lchat.app.R
import com.lchat.app.adapter.StoryListAdapter
import com.lchat.app.adapter.StoryRow
import com.lchat.app.databinding.FragmentStoriesBinding
import com.lchat.app.model.StoryGroup
import com.lchat.app.model.StoryItem
import com.lchat.app.snap.CreateSnapActivity
import com.lchat.app.story.ViewStoryActivity
import java.io.File

class StoriesFragment : Fragment() {

    private var _binding: FragmentStoriesBinding? = null
    private val binding get() = _binding!!

    private val stories = ArrayList<StoryItem>()
    private var storiesListener: ChildEventListener? = null
    private var myName = ""

    private lateinit var adapter: StoryListAdapter

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val file = copyToCache(uri)
                if (file != null) {
                    val intent = Intent(requireContext(), CreateSnapActivity::class.java)
                    intent.putExtra(CreateSnapActivity.EXTRA_IMAGE_PATH, file.absolutePath)
                    startActivity(intent)
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        adapter = StoryListAdapter(
            myName = "",
            myUid = myUid,
            onMyStoryClick = {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onGroupClick = { group -> openStory(group) }
        )
        binding.storiesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.storiesRecycler.adapter = adapter

        loadMyName()
        listenStories()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        storiesListener?.let {
            FirebaseDatabase.getInstance().getReference("stories").removeEventListener(it)
        }
        storiesListener = null
        _binding = null
    }

    private fun loadMyName() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().getReference("users").child(uid).child("username").get()
            .addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                myName = it.value as? String ?: ""
                adapter = StoryListAdapter(
                    myName = myName,
                    myUid = uid,
                    onMyStoryClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onGroupClick = { group -> openStory(group) }
                )
                binding.storiesRecycler.adapter = adapter
                refresh()
            }
    }

    private fun listenStories() {
        val ref = FirebaseDatabase.getInstance().getReference("stories")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                addStory(snapshot)
                refresh()
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                stories.removeAll { it.id == snapshot.key }
                addStory(snapshot)
                refresh()
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                stories.removeAll { it.id == snapshot.key }
                refresh()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        storiesListener = listener
        ref.addChildEventListener(listener)
    }

    private fun addStory(snapshot: DataSnapshot) {
        val item = StoryItem(
            id = snapshot.key ?: return,
            fromUid = snapshot.child("fromUid").value as? String ?: "",
            fromName = snapshot.child("fromName").value as? String ?: "",
            imageData = snapshot.child("imageData").value as? String ?: "",
            caption = snapshot.child("caption").value as? String ?: "",
            createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
        )
        if (item.imageData.isNotEmpty()) {
            stories.add(item)
        }
    }

    private fun cleanupExpiredOwnStories() {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        val ref = FirebaseDatabase.getInstance().getReference("stories")
        for (story in stories.filter { it.fromUid == myUid && it.createdAt < cutoff }) {
            ref.child(story.id).removeValue()
        }
    }

    private fun refresh() {
        if (_binding == null) return

        cleanupExpiredOwnStories()

        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        val seen = seenIds()
        val active = stories.filter { it.createdAt >= cutoff }

        val groups = active
            .groupBy { it.fromUid }
            .map { (uid, items) ->
                val sorted = items.sortedBy { it.createdAt }
                StoryGroup(
                    fromUid = uid,
                    fromName = sorted.first().fromName,
                    stories = ArrayList(sorted),
                    viewed = sorted.all { seen.contains(it.id) }
                )
            }
            .sortedByDescending { it.stories.maxOfOrNull { s -> s.createdAt } ?: 0L }

        val rows = ArrayList<StoryRow>()
        rows.add(StoryRow.Mine)
        for (group in groups) rows.add(StoryRow.Group(group))

        adapter.setRows(rows)
        binding.emptyView.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
        binding.storiesRecycler.visibility = if (groups.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun seenIds(): Set<String> =
        requireContext()
            .getSharedPreferences("lchat_prefs", Context.MODE_PRIVATE)
            .getStringSet("seen_stories", emptySet())
            ?: emptySet()

    private fun openStory(group: StoryGroup) {
        val intent = Intent(requireContext(), ViewStoryActivity::class.java)
        intent.putExtra(ViewStoryActivity.EXTRA_STORIES, group.stories)
        intent.putExtra(ViewStoryActivity.EXTRA_FROM_NAME, group.fromName)
        intent.putExtra(ViewStoryActivity.EXTRA_FROM_UID, group.fromUid)
        startActivity(intent)
    }

    private fun copyToCache(uri: android.net.Uri): File? = try {
        val file = File(requireContext().cacheDir, "pick_${System.currentTimeMillis()}.jpg")
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        file
    } catch (e: Exception) {
        null
    }
}
