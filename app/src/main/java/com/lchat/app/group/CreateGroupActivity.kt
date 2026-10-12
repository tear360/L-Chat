package com.lchat.app.group

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.lchat.app.adapter.UsersAdapter
import com.lchat.app.chat.ChatActivity
import com.lchat.app.databinding.ActivityCreateGroupBinding
import com.lchat.app.model.User
import com.lchat.app.util.Db
import com.lchat.app.util.Prefs

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateGroupBinding
    private val adapter = UsersAdapter()
    private val allUsers = ArrayList<User>()
    private var usersListener: ChildEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.applyTheme(this)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        binding.createButton.setOnClickListener { createGroup() }

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
        usersListener?.let { Db.ref("users").removeEventListener(it) }
    }

    private fun listenUsers() {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = Db.ref("users")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val uid = snapshot.key ?: return
                if (uid == myUid) return
                allUsers.add(User(
                    uid = uid,
                    username = snapshot.child("username").value as? String ?: "",
                    email = snapshot.child("email").value as? String ?: "",
                    photoData = snapshot.child("photoData").value as? String ?: ""
                ))
                applyFilter()
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        usersListener = listener
        ref.addChildEventListener(listener)
    }

    private fun applyFilter() {
        val query = binding.searchInput.text.toString().trim().lowercase()
        adapter.setItems(
            if (query.isEmpty()) allUsers else allUsers.filter {
                it.username.lowercase().contains(query) || it.email.lowercase().contains(query)
            }
        )
    }

    private fun createGroup() {
        val name = binding.groupNameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Donne un nom au groupe", Toast.LENGTH_SHORT).show()
            return
        }
        val members = adapter.selectedUids()
        if (members.size < 2) {
            Toast.makeText(
                this,
                "Un groupe doit avoir au moins 2 autres membres",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val myUid = FirebaseAuth.getInstance().currentUser!!.uid
        val groupRef = Db.ref("groups").push()
        val groupId = groupRef.key ?: return

        val memberMap = HashMap<String, Boolean>()
        memberMap[myUid] = true
        for (m in members) memberMap[m] = true

        groupRef.setValue(mapOf(
            "name" to name,
            "owner" to myUid,
            "members" to memberMap,
            "createdAt" to System.currentTimeMillis()
        )).addOnSuccessListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("otherUid", "group_$groupId")
            intent.putExtra("otherName", name)
            startActivity(intent)
            finish()
        }
    }
}