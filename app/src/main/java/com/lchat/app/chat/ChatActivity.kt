package com.lchat.app.chat

import com.lchat.app.util.Db
import com.lchat.app.util.E2E

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.lchat.app.adapter.MessagesAdapter
import com.lchat.app.databinding.ActivityChatBinding
import com.lchat.app.model.ChatMessage
import com.lchat.app.util.AppUtils
import javax.crypto.SecretKey

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessagesAdapter

    private val mAuth = FirebaseAuth.getInstance()
    private var myUid = ""
    private var otherUid = ""
    private var otherName = ""
    private var myName = ""
    private var pairKey = ""
    private var sharedKey: SecretKey? = null
    private var messagesListener: ChildEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        myUid = mAuth.currentUser?.uid ?: run { finish(); return }
        otherUid = intent.getStringExtra("otherUid") ?: run { finish(); return }
        otherName = intent.getStringExtra("otherName") ?: ""
        pairKey = AppUtils.pairKey(myUid, otherUid)

        binding.chatTitle.text = otherName
        val color = AppUtils.avatarColor(otherUid)
        binding.avatarBg.background = AppUtils.circle(color)
        binding.avatarInitials.text = AppUtils.initials(otherName)
        binding.avatarInitials.setTextColor(AppUtils.initialsTextColor(color))

        adapter = MessagesAdapter(myUid)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding.messagesRecycler.layoutManager = layoutManager
        binding.messagesRecycler.adapter = adapter

        binding.backButton.setOnClickListener { finish() }
        binding.sendButton.setOnClickListener { sendMessage() }

        E2E.init(this)
        E2E.ensureKeys(this, myUid)
        E2E.keyWithOther(otherUid) { key ->
            runOnUiThread {
                sharedKey = key
                adapter.notifyDataSetChanged()
            }
        }

        loadMyName()
        markConversationRead()
        listenMessages()
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.let {
            Db.ref("conversations")
                .child(pairKey)
                .child("messages")
                .removeEventListener(it)
        }
    }

    private fun loadMyName() {
        Db.ref("users").child(myUid).child("username").get()
            .addOnSuccessListener { myName = it.value as? String ?: "" }
    }

    private fun markConversationRead() {
        val ref = Db.ref("users")
            .child(myUid).child("conversations").child(otherUid)
        ref.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                ref.child("unread").setValue(false)
            }
        }
    }

    private fun listenMessages() {
        val ref = Db.ref("conversations")
            .child(pairKey)
            .child("messages")

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val raw = snapshot.child("text").value as? String ?: return
                val decrypted = decryptText(raw)
                val message = ChatMessage(
                    fromUid = snapshot.child("fromUid").value as? String ?: "",
                    text = decrypted,
                    createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
                )
                if (message.text.isNotEmpty()) {
                    adapter.addMessage(message)
                    binding.messagesRecycler.scrollToPosition(adapter.itemCount - 1)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        messagesListener = listener
        ref.addChildEventListener(listener)
    }

    private fun decryptText(raw: String): String =
        E2E.decrypt(raw, sharedKey) ?: "[Message illisible]"

    private fun sendMessage() {
        val text = binding.messageInput.text.toString().trim()
        if (text.isEmpty()) return

        val now = System.currentTimeMillis()
        val encryptedText = E2E.encrypt(text, sharedKey)
        val message = ChatMessage(fromUid = myUid, text = encryptedText, createdAt = now)

        Db.ref("conversations")
            .child(pairKey)
            .child("messages")
            .push()
            .setValue(message)

        val preview = E2E.encrypt(text, sharedKey)
        if (myName.isEmpty()) {
            Db.ref("users").child(myUid)
                .child("username").get()
                .addOnSuccessListener {
                    myName = it.value as? String ?: ""
                    updateConversationMeta(preview, now)
                }
                .addOnFailureListener { updateConversationMeta(preview, now) }
        } else {
            updateConversationMeta(preview, now)
        }

        binding.messageInput.text?.clear()

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.messageInput.windowToken, 0)
    }

    private fun updateConversationMeta(lastMessage: String, timestamp: Long) {
        val db = Db.ref("users")

        db.child(myUid).child("conversations").child(otherUid).setValue(
            mapOf(
                "name" to otherName,
                "lastMessage" to lastMessage,
                "lastMessageAt" to timestamp,
                "unread" to false
            )
        )

        db.child(otherUid).child("conversations").child(myUid).setValue(
            mapOf(
                "name" to myName,
                "lastMessage" to lastMessage,
                "lastMessageAt" to timestamp,
                "unread" to true
            )
        )
    }
}
