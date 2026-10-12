package com.lchat.app.friends

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.lchat.app.databinding.ActivityFriendsBinding
import com.lchat.app.databinding.ItemFriendBinding
import com.lchat.app.databinding.ItemFriendRequestBinding
import com.lchat.app.model.User
import com.lchat.app.util.AppUtils
import com.lchat.app.util.Db
import com.lchat.app.util.Prefs

class FriendsActivity : AppCompatActivity() {

    companion object {
        fun showAvatar(
            imageView: android.widget.ImageView,
            initialsView: android.widget.TextView,
            photoData: String
        ) {
            if (photoData.isNotEmpty()) {
                val bitmap = com.lchat.app.util.ImageStore.decode(photoData)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = View.VISIBLE
                    initialsView.visibility = View.GONE
                    return
                }
            }
            imageView.visibility = View.GONE
            initialsView.visibility = View.VISIBLE
        }
    }

    private lateinit var binding: ActivityFriendsBinding
    private lateinit var requestAdapter: RequestsAdapter
    private lateinit var friendAdapter: FriendsAdapter
    private lateinit var searchAdapter: FriendsAdapter

    private val requests = ArrayList<User>()
    private val friends = ArrayList<User>()
    private val searchResults = ArrayList<User>()
    private val sentRequests = HashSet<String>()
    private val friendUids = HashSet<String>()
    private var incomingListener: ChildEventListener? = null
    private var sentListener: ChildEventListener? = null
    private var friendsListener: ChildEventListener? = null
    private var usersListener: ChildEventListener? = null
    private var myUid = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.applyTheme(this)
        binding = ActivityFriendsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        myUid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }

        binding.backButton.setOnClickListener { finish() }

        requestAdapter = RequestsAdapter(
            onAccept = { acceptRequest(it) },
            onDecline = { declineRequest(it) }
        )
        binding.requestsRecycler.layoutManager = LinearLayoutManager(this)
        binding.requestsRecycler.adapter = requestAdapter

        friendAdapter = FriendsAdapter({ uid -> removeFriend(uid) })
        binding.friendsRecycler.layoutManager = LinearLayoutManager(this)
        binding.friendsRecycler.adapter = friendAdapter

        searchAdapter = FriendsAdapter(null)
        binding.searchRecycler.layoutManager = LinearLayoutManager(this)
        binding.searchRecycler.adapter = searchAdapter

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { applySearch() }
        })

        listenIncomingRequests()
        listenSentRequests()
        listenFriends()
        listenAllUsers()
    }

    override fun onDestroy() {
        super.onDestroy()
        incomingListener?.let { myRef("incomingRequests").removeEventListener(it) }
        sentListener?.let { myRef("sentRequests").removeEventListener(it) }
        friendsListener?.let { myRef("friends").removeEventListener(it) }
        usersListener?.let { Db.ref("users").removeEventListener(it) }
    }

    private fun myRef(node: String) = Db.ref("users").child(myUid).child(node)

    private fun fetchUser(uid: String, onReady: (User) -> Unit) {
        Db.ref("users").child(uid).get().addOnSuccessListener { snap ->
            if (!snap.exists()) return@addOnSuccessListener
            onReady(User(
                uid = uid,
                username = snap.child("username").value as? String ?: "",
                email = snap.child("email").value as? String ?: "",
                photoData = snap.child("photoData").value as? String ?: ""
            ))
        }
    }

    private fun listenIncomingRequests() {
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val uid = snapshot.key ?: return
                fetchUser(uid) { user ->
                    requests.removeAll { it.uid == user.uid }
                    requests.add(user)
                    refreshRequests()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {
                requests.removeAll { it.uid == snapshot.key }
                refreshRequests()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        incomingListener = listener
        myRef("incomingRequests").addChildEventListener(listener)
    }

    private fun listenSentRequests() {
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                sentRequests.add(snapshot.key ?: return)
                applySearch()
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {
                sentRequests.remove(snapshot.key)
                applySearch()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        sentListener = listener
        myRef("sentRequests").addChildEventListener(listener)
    }

    private fun listenFriends() {
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val uid = snapshot.key ?: return
                friendUids.add(uid)
                fetchUser(uid) { user ->
                    friends.removeAll { it.uid == user.uid }
                    friends.add(user)
                    friends.sortBy { it.username.lowercase() }
                    refreshFriends()
                    applySearch()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {
                friendUids.remove(snapshot.key)
                friends.removeAll { it.uid == snapshot.key }
                refreshFriends()
                applySearch()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        friendsListener = listener
        myRef("friends").addChildEventListener(listener)
    }

    private fun listenAllUsers() {
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
                searchResults.removeAll { it.uid == uid }
                searchResults.add(user)
                applySearch()
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        usersListener = listener
        Db.ref("users").addChildEventListener(listener)
    }

    private fun refreshRequests() {
        requestAdapter.setItems(requests)
        binding.requestsTitle.visibility = if (requests.isEmpty()) View.GONE else View.VISIBLE
        binding.requestsRecycler.visibility = if (requests.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun refreshFriends() {
        friendAdapter.setItems(friends)
        binding.friendsTitle.visibility = if (friends.isEmpty()) View.GONE else View.VISIBLE
        binding.friendsRecycler.visibility = if (friends.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyFriends.visibility = if (friends.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun applySearch() {
        val query = binding.searchInput.text.toString().trim().lowercase()
        val filtered = if (query.isEmpty()) {
            searchResults
        } else {
            searchResults.filter {
                it.username.lowercase().contains(query) || it.email.lowercase().contains(query)
            }
        }.sortedBy { it.username.lowercase() }

        searchAdapter.setItems(filtered, friendUids, sentRequests) { user ->
            sendRequest(user)
        }
        binding.searchRecycler.visibility =
            if (query.isEmpty() || filtered.isEmpty()) View.GONE else View.VISIBLE
        binding.noResult.visibility =
            if (query.isNotEmpty() && filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun sendRequest(user: User) {
        myRef("sentRequests").child(user.uid).setValue(true)
        Db.ref("users").child(user.uid).child("incomingRequests").child(myUid).setValue(true)
        sentRequests.add(user.uid)
        applySearch()
        Toast.makeText(this, "Demande envoyée à ${user.username}", Toast.LENGTH_SHORT).show()
    }

    private fun acceptRequest(user: User) {
        val db = Db.ref("users")
        db.child(myUid).child("friends").child(user.uid).setValue(true)
        db.child(user.uid).child("friends").child(myUid).setValue(true)
        db.child(myUid).child("incomingRequests").child(user.uid).removeValue()
        db.child(user.uid).child("incomingRequests").child(myUid).removeValue()
        Toast.makeText(this, "${user.username} est maintenant ton ami", Toast.LENGTH_SHORT).show()
    }

    private fun declineRequest(user: User) {
        val db = Db.ref("users")
        db.child(myUid).child("incomingRequests").child(user.uid).removeValue()
        db.child(user.uid).child("incomingRequests").child(myUid).removeValue()
    }

    private fun removeFriend(uid: String) {
        val db = Db.ref("users")
        db.child(myUid).child("friends").child(uid).removeValue()
        db.child(uid).child("friends").child(myUid).removeValue()
    }

    class RequestsAdapter(
        private val onAccept: (User) -> Unit,
        private val onDecline: (User) -> Unit
    ) : RecyclerView.Adapter<RequestsAdapter.VH>() {

        private val items = ArrayList<User>()

        fun setItems(users: List<User>) {
            items.clear()
            items.addAll(users)
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemFriendRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        inner class VH(private val binding: ItemFriendRequestBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(user: User) {
                val color = AppUtils.avatarColor(user.uid)
                binding.avatarCircle.background = AppUtils.circle(color)
                binding.initials.text = AppUtils.initials(user.username)
                binding.initials.setTextColor(AppUtils.initialsTextColor(color))
                binding.name.text = user.username
                showAvatar(binding.avatarImage, binding.initials, user.photoData)
                binding.acceptButton.setOnClickListener { onAccept(user) }
                binding.declineButton.setOnClickListener { onDecline(user) }
            }
        }
    }

    class FriendsAdapter(
        private val onRemove: ((String) -> Unit)?,
        private val onAdd: ((User) -> Unit)? = null
    ) : RecyclerView.Adapter<FriendsAdapter.VH>() {

        private val items = ArrayList<User>()
        private var friendUids: Set<String> = emptySet()
        private var sentRequests: Set<String> = emptySet()
        private var addAction: ((User) -> Unit)? = null

        fun setItems(
            users: List<User>,
            friends: Set<String> = emptySet(),
            sent: Set<String> = emptySet(),
            onAdd: ((User) -> Unit)? = null
        ) {
            items.clear()
            items.addAll(users)
            friendUids = friends
            sentRequests = sent
            addAction = onAdd
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemFriendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        inner class VH(private val binding: ItemFriendBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(user: User) {
                val color = AppUtils.avatarColor(user.uid)
                binding.avatarCircle.background = AppUtils.circle(color)
                binding.initials.text = AppUtils.initials(user.username)
                binding.initials.setTextColor(AppUtils.initialsTextColor(color))
                binding.name.text = user.username
                showAvatar(binding.avatarImage, binding.initials, user.photoData)

                val isFriend = friendUids.contains(user.uid)
                val isPending = sentRequests.contains(user.uid)

                binding.actionButton.visibility = View.VISIBLE
                binding.actionButton.text = when {
                    onRemove != null -> "Retirer"
                    isFriend -> "Ami"
                    isPending -> "Envoyée"
                    else -> "Ajouter"
                }
                binding.actionButton.isEnabled = when {
                    onRemove != null -> true
                    isFriend || isPending -> false
                    else -> true
                }
                binding.actionButton.setOnClickListener {
                    when {
                        onRemove != null -> onRemove.invoke(user.uid)
                        isFriend || isPending -> Unit
                        else -> addAction?.invoke(user)
                    }
                }
            }
        }
    }
}