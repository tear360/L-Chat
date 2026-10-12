package com.lchat.app.group

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.lchat.app.adapter.GroupMemberAdapter
import com.lchat.app.databinding.ActivityGroupSettingsBinding
import com.lchat.app.model.User
import com.lchat.app.util.AppUtils
import com.lchat.app.util.Db
import com.lchat.app.util.ImageStore
import com.lchat.app.util.Prefs

class GroupSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupSettingsBinding
    private lateinit var adapter: GroupMemberAdapter
    private val members = LinkedHashMap<String, User>()
    private var groupId = ""
    private var ownerUid = ""
    private var name = ""
    private var description = ""
    private var iconData = ""
    private var membersCanEdit = false
    private var membersListener: ChildEventListener? = null
    private var canEdit = false

    private val pickIcon =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) uploadIcon(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.applyTheme(this)
        binding = ActivityGroupSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra("groupId") ?: run { finish(); return }
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }

        binding.backButton.setOnClickListener { finish() }
        binding.iconRow.setOnClickListener { if (canEdit) pickIcon.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        ) }
        binding.membersCanEditSwitch.setOnCheckedChangeListener { _, checked ->
            if (canEdit) {
                membersCanEdit = checked
                Db.ref("groups").child(groupId).child("membersCanEdit").setValue(checked)
            }
        }

        adapter = GroupMemberAdapter(
            onRemove = { user -> removeMember(user) },
            canManage = false
        )
        binding.membersRecycler.layoutManager = LinearLayoutManager(this)
        binding.membersRecycler.adapter = adapter

        loadGroup(myUid)
        listenMembers()
    }

    override fun onDestroy() {
        super.onDestroy()
        membersListener?.let {
            Db.ref("groups").child(groupId).child("members").removeEventListener(it)
        }
    }

    private fun loadGroup(myUid: String) {
        Db.ref("groups").child(groupId).get().addOnSuccessListener { snap ->
            if (!snap.exists()) {
                finish()
                return@addOnSuccessListener
            }
            name = snap.child("name").value as? String ?: ""
            description = snap.child("description").value as? String ?: ""
            iconData = snap.child("icon").value as? String ?: ""
            ownerUid = snap.child("owner").value as? String ?: ""
            membersCanEdit = snap.child("membersCanEdit").value as? Boolean ?: false

            val isOwner = ownerUid == myUid
            canEdit = isOwner || membersCanEdit

            binding.groupNameInput.setText(name)
            binding.groupDescInput.setText(description)
            binding.groupNameInput.isEnabled = canEdit
            binding.groupDescInput.isEnabled = canEdit
            binding.iconRow.isEnabled = canEdit
            binding.adminLabel.visibility = if (isOwner) View.VISIBLE else View.GONE
            binding.membersCanEditSwitch.isChecked = membersCanEdit
            binding.membersCanEditSwitch.isEnabled = isOwner

            binding.saveButton.visibility = if (canEdit) View.VISIBLE else View.GONE
            binding.saveButton.setOnClickListener { saveGroup() }

            val color = AppUtils.avatarColor(groupId)
            binding.groupAvatarBg.background = AppUtils.circle(color)
            if (iconData.isNotEmpty()) {
                val bitmap = ImageStore.decode(iconData)
                if (bitmap != null) {
                    binding.groupIcon.setImageBitmap(bitmap)
                    binding.groupIcon.visibility = View.VISIBLE
                    binding.groupInitials.visibility = View.GONE
                }
            }
            if (binding.groupInitials.visibility == View.VISIBLE) {
                binding.groupInitials.text = AppUtils.initials(name)
                binding.groupInitials.setTextColor(AppUtils.initialsTextColor(color))
            }
            refreshMembers(isOwner)
        }
    }

    private fun listenMembers() {
        val ref = Db.ref("groups").child(groupId).child("members")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val uid = snapshot.key ?: return
                Db.ref("users").child(uid).get().addOnSuccessListener { u ->
                    if (u.exists()) {
                        members[uid] = User(
                            uid = uid,
                            username = u.child("username").value as? String ?: "",
                            email = u.child("email").value as? String ?: ""
                        )
                        adapter.setMembers(members.values.toList(), ownerUid)
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {
                members.remove(snapshot.key)
                adapter.setMembers(members.values.toList(), ownerUid)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        membersListener = listener
        ref.addChildEventListener(listener)
    }

    private fun refreshMembers(isOwner: Boolean) {
        adapter.setMembers(members.values.toList(), ownerUid)
    }

    private fun saveGroup() {
        val newName = binding.groupNameInput.text.toString().trim()
        if (newName.isEmpty()) {
            Toast.makeText(this, "Group name required", Toast.LENGTH_SHORT).show()
            return
        }
        Db.ref("groups").child(groupId).child("name").setValue(newName)
        Db.ref("groups").child(groupId).child("description")
            .setValue(binding.groupDescInput.text.toString().trim())
        Toast.makeText(this, "Group updated", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun uploadIcon(uri: android.net.Uri) {
        Thread {
            val data = try {
                ImageStore.encodeUri(applicationContext, uri, 256, 80)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (data == null) {
                    Toast.makeText(this, "Could not load image", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                Db.ref("groups").child(groupId).child("icon").setValue(data)
                val bitmap = ImageStore.decode(data)
                if (bitmap != null) {
                    binding.groupIcon.setImageBitmap(bitmap)
                    binding.groupIcon.visibility = View.VISIBLE
                    binding.groupInitials.visibility = View.GONE
                }
                Toast.makeText(this, "Icon updated", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun removeMember(user: User) {
        Db.ref("groups").child(groupId).child("members").child(user.uid).removeValue()
        Toast.makeText(this, "${user.username} removed", Toast.LENGTH_SHORT).show()
    }
}