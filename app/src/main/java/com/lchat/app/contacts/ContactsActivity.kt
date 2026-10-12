package com.lchat.app.contacts

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.lchat.app.R
import com.lchat.app.databinding.ActivityContactsBinding
import com.lchat.app.databinding.ItemContactBinding
import com.lchat.app.model.User
import com.lchat.app.util.AppUtils
import com.lchat.app.util.Db
import com.lchat.app.util.Prefs

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private val adapter = ContactsAdapter(
        onRemove = { user -> confirmRemove(user) },
        onAdd = { user -> addContact(user) }
    )
    private val contacts = LinkedHashMap<String, User>()
    private val suggestions = ArrayList<User>()
    private var contactsListener: ChildEventListener? = null
    private var usersListener: ChildEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.applyTheme(this)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        binding.addButton.setOnClickListener { showAddDialog() }

        binding.contactsRecycler.layoutManager = LinearLayoutManager(this)
        binding.contactsRecycler.adapter = adapter

        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        listenContacts(myUid)
        listenSuggestions(myUid)
    }

    override fun onDestroy() {
        super.onDestroy()
        contactsListener?.let { Db.ref("users").removeEventListener(it) }
        usersListener?.let { Db.ref("users").removeEventListener(it) }
    }

    private fun myContactsRef() = Db.ref("users")
        .child(FirebaseAuth.getInstance().currentUser!!.uid)
        .child("contacts")

    private fun listenContacts(myUid: String) {
        val ref = myContactsRef()
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val uid = snapshot.key ?: return
                Db.ref("users").child(uid).get().addOnSuccessListener { userSnap ->
                    if (userSnap.exists()) {
                        contacts[uid] = User(
                            uid = uid,
                            username = userSnap.child("username").value as? String ?: "",
                            email = userSnap.child("email").value as? String ?: "",
                            photoData = userSnap.child("photoData").value as? String ?: ""
                        )
                        refresh()
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {
                contacts.remove(snapshot.key)
                refresh()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        contactsListener = listener
        ref.addChildEventListener(listener)
    }

    private fun listenSuggestions(myUid: String) {
        val ref = Db.ref("users")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val uid = snapshot.key ?: return
                if (uid == myUid || contacts.containsKey(uid)) return
                suggestions.add(User(
                    uid = uid,
                    username = snapshot.child("username").value as? String ?: "",
                    email = snapshot.child("email").value as? String ?: "",
                    photoData = snapshot.child("photoData").value as? String ?: ""
                ))
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        usersListener = listener
        ref.addChildEventListener(listener)
    }

    private fun refresh() {
        adapter.setContacts(contacts.values.toList())
        binding.emptyView.visibility =
            if (contacts.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Username or email"
        val padding = (20 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding / 2, padding, padding / 2)

        AlertDialog.Builder(this)
            .setTitle("Add contact")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val query = input.text.toString().trim().lowercase()
                if (query.isEmpty()) return@setPositiveButton
                val match = suggestions.firstOrNull {
                    it.username.lowercase() == query || it.email.lowercase() == query
                } ?: suggestions.firstOrNull {
                    it.username.lowercase().contains(query) || it.email.lowercase().contains(query)
                }
                if (match != null) {
                    addContact(match)
                } else {
                    Toast.makeText(this, "No user found", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addContact(user: User) {
        myContactsRef().child(user.uid).setValue(true)
            .addOnSuccessListener {
                suggestions.removeAll { it.uid == user.uid }
                Toast.makeText(this, "${user.username} added", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Could not add contact", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmRemove(user: User) {
        AlertDialog.Builder(this)
            .setTitle("Remove contact")
            .setMessage("Remove ${user.username} from your contacts?")
            .setPositiveButton("Remove") { _, _ ->
                myContactsRef().child(user.uid).removeValue()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    class ContactsAdapter(
        private val onRemove: (User) -> Unit,
        private val onAdd: (User) -> Unit
    ) : RecyclerView.Adapter<ContactsAdapter.VH>() {

        private val items = ArrayList<User>()
        private var suggestions: List<User> = emptyList()
        private var showSuggestions = false

        fun setContacts(users: List<User>) {
            items.clear()
            items.addAll(users)
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        inner class VH(private val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(user: User) {
                val color = AppUtils.avatarColor(user.uid)
                binding.avatarCircle.background = AppUtils.circle(color)
                binding.initials.text = AppUtils.initials(user.username.ifEmpty { user.email })
                binding.initials.setTextColor(AppUtils.initialsTextColor(color))
                binding.name.text = user.username
                binding.email.text = user.email
                binding.removeButton.setOnClickListener { onRemove(user) }
            }
        }
    }
}