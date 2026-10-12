package com.lchat.app.snap

import android.app.Activity
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
import com.lchat.app.R
import com.lchat.app.adapter.UsersAdapter
import com.lchat.app.friends.FriendsActivity
import com.lchat.app.databinding.ActivityChooseRecipientsBinding
import com.lchat.app.model.User
import com.lchat.app.util.Db
import com.lchat.app.util.ImageStore
import com.lchat.app.util.Prefs
import java.io.File

class ChooseRecipientsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "imagePath"
        const val EXTRA_CAPTION = "caption"
    }

    private lateinit var binding: ActivityChooseRecipientsBinding
    private val adapter = UsersAdapter()
    private val allUsers = ArrayList<User>()
    private val friendUids = HashSet<String>()
    private var myName = ""
    private var sending = false
    private var contactsOnly = true

    private var usersListener: ChildEventListener? = null
    private var contactsListener: ChildEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.applyTheme(this)
        binding = ActivityChooseRecipientsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        binding.usersRecycler.layoutManager = LinearLayoutManager(this)
        binding.usersRecycler.adapter = adapter

        adapter.onSelectionChanged = { count ->
            binding.sendFab.visibility = if (count > 0) View.VISIBLE else View.GONE
            binding.selectionCount.text = if (count > 0) "$count selected" else ""
        }

        binding.contactsOnlySwitch.setOnCheckedChangeListener { _, checked ->
            contactsOnly = checked
            applyFilter()
        }
        binding.addContactsButton.setOnClickListener {
            startActivity(Intent(this, FriendsActivity::class.java))
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilter() }
        })

        binding.sendFab.setOnClickListener { send() }

        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        Db.ref("users").child(myUid).child("username").get()
            .addOnSuccessListener { myName = it.value as? String ?: "" }

        listenContacts(myUid)
        listenUsers(myUid)
    }

    override fun onDestroy() {
        super.onDestroy()
        usersListener?.let { Db.ref("users").removeEventListener(it) }
        contactsListener?.let { myFriendsRef().removeEventListener(it) }
    }

    private fun myFriendsRef() = Db.ref("users")
        .child(FirebaseAuth.getInstance().currentUser!!.uid)
        .child("friends")

    private fun listenContacts(myUid: String) {
        val ref = myFriendsRef()
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                friendUids.add(snapshot.key ?: return)
                applyFilter()
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {
                friendUids.remove(snapshot.key)
                applyFilter()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        contactsListener = listener
        ref.addChildEventListener(listener)
    }

    private fun listenUsers(myUid: String) {
        val ref = Db.ref("users")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val uid = snapshot.key ?: return
                if (uid == myUid) return
                allUsers.removeAll { it.uid == uid }
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
                    allUsers[index].username = snapshot.child("username").value as? String ?: ""
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
        val base = if (contactsOnly) allUsers.filter { friendUids.contains(it.uid) } else allUsers
        val filtered = if (query.isEmpty()) {
            base.sortedBy { it.username.lowercase() }
        } else {
            base.filter {
                it.username.lowercase().contains(query) || it.email.lowercase().contains(query)
            }
        }
        adapter.setItems(filtered)

        val nothingToShow = filtered.isEmpty()
        binding.emptyView.visibility = if (nothingToShow) View.VISIBLE else View.GONE
        binding.emptyView.text = when {
            allUsers.isEmpty() -> "No other users yet.\nAsk a friend to sign up on L Chat!"
            contactsOnly && friendUids.isEmpty() ->
                "Tu n'as pas encore d'amis.\nAjoute des amis depuis Moi > Paramètres."
            query.isNotEmpty() -> "Nobody matches \"$query\"."
            else -> "Aucun ami à montrer. Désactive \"Amis uniquement\" pour voir tout le monde."
        }
    }

    private fun send() {
        if (sending) return

        val selected = adapter.selectedUids()
        if (selected.isEmpty()) {
            Toast.makeText(this, "Sélectionne au moins un contact", Toast.LENGTH_SHORT).show()
            return
        }

        val path = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (path == null || !File(path).exists()) {
            Toast.makeText(this, "Image introuvable", Toast.LENGTH_SHORT).show()
            return
        }

        sending = true
        binding.sendProgress.visibility = View.VISIBLE
        binding.sendFab.visibility = View.GONE

        val caption = intent.getStringExtra(EXTRA_CAPTION) ?: ""
        val fromUid = FirebaseAuth.getInstance().currentUser!!.uid

        Thread {
            val imageData = try {
                ImageStore.encodeFile(File(path))
            } catch (e: Exception) {
                null
            }

            runOnUiThread {
                if (imageData == null) {
                    sending = false
                    binding.sendProgress.visibility = View.GONE
                    Toast.makeText(this, "Impossible de traiter l'image", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                val snap = mapOf(
                    "fromUid" to fromUid,
                    "fromName" to myName,
                    "imageData" to imageData,
                    "caption" to caption,
                    "createdAt" to System.currentTimeMillis()
                )
                val db = Db.ref("users")
                for (uid in selected) {
                    db.child(uid).child("snaps").push().setValue(snap)
                }
                Toast.makeText(this, "Snap sent to $selected!", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
        }.start()
    }
}