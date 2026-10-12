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
import com.google.firebase.database.FirebaseDatabase
import com.lchat.app.adapter.UsersAdapter
import com.lchat.app.model.User
import com.lchat.app.util.ImageStore
import java.io.File
import java.util.*

class ChooseRecipientsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "imagePath"
        const val EXTRA_CAPTION = "caption"
    }

    private lateinit var binding: com.lchat.app.databinding.ActivityChooseRecipientsBinding
    private val adapter = UsersAdapter()
    private val allUsers = ArrayList<User>()
    private var myName = ""
    private var sending = false

    private var usersListener: ChildEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = com.lchat.app.databinding.ActivityChooseRecipientsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }

        binding.usersRecycler.layoutManager = LinearLayoutManager(this)
        binding.usersRecycler.adapter = adapter

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilter() }
        })

        binding.sendFab.setOnClickListener { send() }

        loadMyName()
        listenUsers()
    }

    override fun onDestroy() {
        super.onDestroy()
        usersListener?.let {
            FirebaseDatabase.getInstance().getReference("users").removeEventListener(it)
        }
    }

    private fun loadMyName() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().getReference("users").child(uid).child("username").get()
            .addOnSuccessListener { myName = it.value as? String ?: "" }
    }

    private fun listenUsers() {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().getReference("users")
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
                    adapter.setItems(allUsers)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val uid = snapshot.key ?: return
                allUsers.forEach { if (it.uid == uid) {
                    it.username = snapshot.child("username").value as? String ?: ""
                    it.photoData = snapshot.child("photoData").value as? String ?: ""
                }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val uid = snapshot.key ?: return
                allUsers.removeAll { it.uid == uid }
                adapter.setItems(allUsers)
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
        adapter.setItems(filtered)
        binding.emptyView.visibility = if (filtered.isEmpty() && allUsers.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun send() {
        val selected = adapter.selectedUids()
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select at least one friend", Toast.LENGTH_SHORT).show()
            return
        }

        val path = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (path == null || !File(path).exists()) {
            Toast.makeText(this, "Image not found", Toast.LENGTH_SHORT).show()
            return
        }

        val caption = intent.getStringExtra(EXTRA_CAPTION) ?: ""
        val fromUid = FirebaseAuth.getInstance().currentUser!!.uid

        sending = true
        binding.sendProgress.visibility = View.VISIBLE
        binding.sendFab.visibility = View.GONE

        Thread {
            val imageData = try { ImageStore.encodeFile(File(path)) } catch (e: Exception) { null }

            runOnUiThread {
                if (imageData == null) {
                    sending = false
                    binding.sendProgress.visibility = View.GONE
                    binding.sendFab.visibility = View.VISIBLE
                    Toast.makeText(this, "Could not process the image", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                val snap = mapOf(
                    "fromUid" to fromUid,
                    "fromName" to myName,
                    "imageData" to imageData,
                    "caption" to caption,
                    "createdAt" to System.currentTimeMillis()
                )
                val db = FirebaseDatabase.getInstance().getReference("users")
                for (uid in selected) {
                    db.child(uid).child("snaps").push().setValue(snap)
                        .addOnSuccessListener { /* snap sent to $uid */ }
                        .addOnFailureListener { /* failed to send to $uid */ }
                }
                sending = false
                binding.sendProgress.visibility = View.GONE
                binding.sendFab.visibility = View.VISIBLE
                Toast.makeText(this, "Snap sent!", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
        }.start()
    }
}