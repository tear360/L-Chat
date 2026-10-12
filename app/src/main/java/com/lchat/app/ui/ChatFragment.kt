package com.lchat.app.ui

import com.lchat.app.util.Db

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.lchat.app.adapter.ChatListAdapter
import com.lchat.app.adapter.ChatRow
import com.lchat.app.chat.ChatActivity
import com.lchat.app.chat.NewChatActivity
import com.lchat.app.databinding.FragmentChatBinding
import com.lchat.app.group.CreateGroupActivity
import com.lchat.app.model.Conversation
import com.lchat.app.model.GroupItem
import com.lchat.app.model.SnapItem
import com.lchat.app.snap.ViewSnapActivity
import com.lchat.app.util.E2E

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val snaps = LinkedHashMap<String, SnapItem>()
    private val conversations = LinkedHashMap<String, Conversation>()
    private val groups = LinkedHashMap<String, GroupItem>()
    private val previewKeys = HashMap<String, javax.crypto.SecretKey>()
    private val listeners =
        ArrayList<Pair<com.google.firebase.database.DatabaseReference, ChildEventListener>>()

    private lateinit var adapter: ChatListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ChatListAdapter(
            onSnapClick = { snap -> openSnap(snap) },
            onChatClick = { conversation -> openChat(conversation) },
            onGroupClick = { group -> openGroup(group) }
        )
        binding.chatRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.chatRecycler.adapter = adapter

        binding.newChatFab.setOnClickListener {
            startActivity(Intent(requireContext(), NewChatActivity::class.java))
        }
        binding.newGroupFab.setOnClickListener {
            startActivity(Intent(requireContext(), CreateGroupActivity::class.java))
        }

        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val root = Db.ref("users").child(myUid)

        listenSnaps(root.child("snaps"))
        listenConversations(root.child("conversations"))
        listenGroups(myUid)
    }

    private fun listenGroups(myUid: String) {
        val ref = Db.ref("groups")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (snapshot.child("members").child(myUid).exists()) {
                    groups[snapshot.key ?: return] = groupFrom(snapshot)
                    refresh()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                if (snapshot.child("members").child(myUid).exists()) {
                    groups[snapshot.key ?: return] = groupFrom(snapshot)
                    refresh()
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                groups.remove(snapshot.key)
                refresh()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        listeners.add(ref to listener)
        ref.addChildEventListener(listener)
    }

    private fun groupFrom(snapshot: DataSnapshot) = GroupItem(
        groupId = snapshot.key ?: "",
        name = snapshot.child("name").value as? String ?: "",
        owner = snapshot.child("owner").value as? String ?: "",
        lastMessage = snapshot.child("lastMessage").value as? String ?: "",
        lastMessageAt = snapshot.child("lastMessageAt").getValue(Long::class.java) ?: 0L
    )

    private fun openGroup(group: GroupItem) {
        val intent = Intent(requireContext(), ChatActivity::class.java)
        intent.putExtra("otherUid", "group_${group.groupId}")
        intent.putExtra("otherName", group.name)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        for ((ref, listener) in listeners) {
            ref.removeEventListener(listener)
        }
        listeners.clear()
        _binding = null
    }

    private fun listenSnaps(ref: com.google.firebase.database.DatabaseReference) {
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val key = snapshot.key ?: return
                snaps[key] = snapFrom(snapshot, key)
                refresh()
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val key = snapshot.key ?: return
                snaps[key] = snapFrom(snapshot, key)
                refresh()
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                snaps.remove(snapshot.key)
                refresh()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        listeners.add(ref to listener)
        ref.addChildEventListener(listener)
    }

    private fun listenConversations(ref: com.google.firebase.database.DatabaseReference) {
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val key = snapshot.key ?: return
                conversations[key] = conversationFrom(snapshot, key)
                refresh()
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val key = snapshot.key ?: return
                conversations[key] = conversationFrom(snapshot, key)
                refresh()
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                conversations.remove(snapshot.key)
                refresh()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        listeners.add(ref to listener)
        ref.addChildEventListener(listener)
    }

    private fun snapFrom(snapshot: DataSnapshot, key: String) = SnapItem(
        key = key,
        fromUid = snapshot.child("fromUid").value as? String ?: "",
        fromName = snapshot.child("fromName").value as? String ?: "",
        imageData = snapshot.child("imageData").value as? String ?: "",
        caption = snapshot.child("caption").value as? String ?: "",
        createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
    )

    private fun conversationFrom(snapshot: DataSnapshot, key: String): Conversation {
        val conversation = Conversation(
            otherUid = key,
            name = snapshot.child("name").value as? String ?: "",
            lastMessage = "",
            lastMessageAt = snapshot.child("lastMessageAt").getValue(Long::class.java) ?: 0L,
            unread = snapshot.child("unread").value as? Boolean ?: false
        )
        decryptPreview(snapshot.child("lastMessage").value as? String ?: "", conversation)
        return conversation
    }

    private fun decryptPreview(raw: String, conversation: Conversation) {
        if (raw.isEmpty()) {
            conversation.lastMessage = ""
            refresh()
            return
        }
        if (raw.startsWith("plain:")) {
            conversation.lastMessage = raw.removePrefix("plain:")
            refresh()
            return
        }
        val cached = previewKeys[conversation.otherUid]
        if (cached != null) {
            conversation.lastMessage = E2E.decrypt(raw, cached) ?: "[Chiffré]"
            refresh()
            return
        }
        E2E.keyWithOther(conversation.otherUid) { key ->
            if (key != null) previewKeys[conversation.otherUid] = key
            activity?.runOnUiThread {
                conversation.lastMessage = E2E.decrypt(raw, key) ?: "[Chiffré]"
                refresh()
            }
        }
    }

    private fun refresh() {
        if (_binding == null) return

        val rows = ArrayList<ChatRow>()
        for (snap in snaps.values) rows.add(ChatRow.Snap(snap))
        for (conversation in conversations.values) rows.add(ChatRow.Chat(conversation))
        for (group in groups.values) rows.add(ChatRow.Group(group))

        rows.sortByDescending {
            when (it) {
                is ChatRow.Snap -> it.snap.createdAt
                is ChatRow.Chat -> it.conversation.lastMessageAt
                is ChatRow.Group -> it.group.lastMessageAt
            }
        }

        adapter.setRows(rows)
        binding.emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        binding.chatRecycler.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openSnap(snap: SnapItem) {
        val intent = Intent(requireContext(), ViewSnapActivity::class.java)
        intent.putExtra("snapKey", snap.key)
        intent.putExtra("imageData", snap.imageData)
        intent.putExtra("caption", snap.caption)
        intent.putExtra("fromName", snap.fromName)
        startActivity(intent)
    }

    private fun openChat(conversation: Conversation) {
        val intent = Intent(requireContext(), ChatActivity::class.java)
        intent.putExtra("otherUid", conversation.otherUid)
        intent.putExtra("otherName", conversation.name)
        startActivity(intent)
    }
}
