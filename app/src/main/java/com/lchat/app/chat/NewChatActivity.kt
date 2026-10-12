package com.lchat.app.chat

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.lchat.app.adapter.SimpleUsersAdapter
import com.lchat.app.databinding.ActivityNewChatBinding
import com.lchat.app.model.User
import com.lchat.app.util.Db

class NewChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewChatBinding
    private val adapter = SimpleUsersAdapter { user -> openChat(user) }
    private val allUsers = ArrayList<User>()
    private var usersListener: ChildEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }

        binding.usersRecycler.layoutManager = LinearLayoutManager(this)
        binding.usersRecycler.adapter = adapter

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilter() }
        })

        listenUsers()
    }

    override fun onDestroy() {
        super.onDestroy()
        usersListener?.let {
            Db.ref("users").removeEventListener(it)
        }
    }

    private fun listenUsers() {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = Db.ref("users")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val uid = snapshot.key ?: return
                if (uid == myUid) return
                val user = User(
                    uid = uid,
                    username = snapshot.child("username").value as? String ?: "",
                    email = snapshot.child("email").value as? String ?: "",
                    photoData = snapshot.child("photoData").value as? String ?: ""
                )
                if (!allUsers.any { it.uid == uid }) {
                    allUsers.add(user)
                    allUsers.sortBy { it.username.lowercase() }
                    applyFilter()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val uid = snapshot.key ?: return
                allUsers.removeAll { it.uid == uid }
                applyFilter()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        usersListener = listener
        ref.addChildEventListener(listener)
    }

    private fun applyFilter() {
        val query = binding.searchInput.text.toString().trim().lowercase()
        val filtered = if (query.isEmpty()) {
            allUsers
        } else {
            allUsers.filter {
                it.username.lowercase().contains(query) || it.email.lowercase().contains(query)
            }
        }
        adapter.setUsers(filtered)
        binding.emptyView.visibility =
            if (filtered.isEmpty() && allUsers.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openChat(user: User) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("otherUid", user.uid)
        intent.putExtra("otherName", user.username)
        startActivity(intent)
        finish()
    }
}