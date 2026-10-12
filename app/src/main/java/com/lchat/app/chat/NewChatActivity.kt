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
import com.lchat.app.group.CreateGroupActivity
import com.lchat.app.model.User
import com.lchat.app.util.Db
import com.lchat.app.util.Prefs

class NewChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewChatBinding
    private val adapter = SimpleUsersAdapter { user -> openChat(user) }
    private val allUsers = ArrayList<User>()
    private val contactUids = HashSet<String>()
    private var usersListener: ChildEventListener? = null
    private var contactsListener: ChildEventListener? = null
    private var showContactsOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.applyTheme(this)
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

        binding.tabContacts.setOnClickListener { setMode(true) }
        binding.tabSearch.setOnClickListener { setMode(false) }
        binding.tabGroup.setOnClickListener {
            startActivity(Intent(this, CreateGroupActivity::class.java))
        }

        setMode(true)
        listenContacts()
        listenUsers()
    }

    private fun setMode(contactsOnly: Boolean) {
        showContactsOnly = contactsOnly
        binding.tabContacts.isChecked = contactsOnly
        binding.tabSearch.isChecked = !contactsOnly
        binding.searchInput.hint =
            if (contactsOnly) "Search your contacts" else "Search any username"
        applyFilter()
    }

    override fun onDestroy() {
        super.onDestroy()
        usersListener?.let { Db.ref("users").removeEventListener(it) }
        contactsListener?.let { myContactsRef().removeEventListener(it) }
    }

    private fun myContactsRef() = Db.ref("users")
        .child(FirebaseAuth.getInstance().currentUser!!.uid)
        .child("contacts")

    private fun listenContacts() {
        val ref = myContactsRef()
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                contactUids.add(snapshot.key ?: return)
                applyFilter()
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {
                contactUids.remove(snapshot.key)
                applyFilter()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        contactsListener = listener
        ref.addChildEventListener(listener)
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

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val uid = snapshot.key ?: return
                allUsers.indexOfFirst { it.uid == uid }.takeIf { it >= 0 }?.let { index ->
                    allUsers[index].username =
                        snapshot.child("username").value as? String ?: ""
                }
                applyFilter()
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        usersListener = listener
        ref.addChildEventListener(listener)
    }

    private fun applyFilter() {
        val query = binding.searchInput.text.toString().trim().lowercase()
        val base = if (showContactsOnly) allUsers.filter { contactUids.contains(it.uid) } else allUsers
        val filtered = if (query.isEmpty()) {
            base.sortedBy { it.username.lowercase() }
        } else {
            base.filter {
                it.username.lowercase().contains(query) || it.email.lowercase().contains(query)
            }
        }
        adapter.setUsers(filtered)
        binding.emptyView.visibility =
            if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyView.text = if (showContactsOnly)
            "No contacts yet.\nAdd contacts from the Me tab, or search any username."
        else
            "No user found with that name."
    }

    private fun openChat(user: User) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("otherUid", user.uid)
        intent.putExtra("otherName", user.username)
        startActivity(intent)
        finish()
    }
}