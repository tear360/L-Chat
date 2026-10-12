package com.lchat.app.ui

import com.lchat.app.util.Db

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.lchat.app.auth.AuthActivity
import com.lchat.app.contacts.ContactsActivity
import com.lchat.app.databinding.FragmentProfileBinding
import com.lchat.app.util.AppUtils
import com.lchat.app.util.ImageStore
import com.lchat.app.util.Prefs
import com.lchat.app.util.Updater

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var myUid = ""
    private var myUsername = ""

    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) uploadProfilePhoto(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        binding.changeUsernameRow.setOnClickListener { showUsernameDialog() }
        binding.changePhotoRow.setOnClickListener {
            pickPhoto.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.logoutRow.setOnClickListener { confirmLogout() }
        binding.contactsRow.setOnClickListener {
            startActivity(Intent(requireContext(), ContactsActivity::class.java))
        }
        binding.updateRow.setOnClickListener { checkUpdates() }
        binding.switchAccountRow.setOnClickListener { showAccountSwitcher() }

        binding.darkModeSwitch.isChecked = Prefs.isDarkMode(requireContext())
        binding.darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setDarkMode(requireContext(), isChecked)
        }

        loadProfile()
        loadContactsCount()
    }

    private fun loadContactsCount() {
        Db.ref("users").child(myUid).child("contacts").get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener
                binding.contactsCount.text = snapshot.childrenCount.toString()
            }
    }

    private fun checkUpdates() {
        binding.updateLabel.text = "Checking..."
        requireContext().let { ctx ->
            Updater.checkForUpdate(ctx) { tag, downloadUrl ->
                requireActivity()?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    binding.updateLabel.text = "Check for updates"
                    val current = "v" + com.lchat.app.BuildConfig.VERSION_NAME
                    if (tag == null || tag == current) {
                        Toast.makeText(ctx, "You're up to date ($current)", Toast.LENGTH_SHORT).show()
                    } else if (downloadUrl == null) {
                        Toast.makeText(ctx, "Update $tag available - you've skipped it", Toast.LENGTH_LONG).show()
                    } else {
                        Updater.promptUpdate(ctx, tag, downloadUrl)
                    }
                }
            }
        }
    }

    private fun showAccountSwitcher() {
        val ctx = requireContext()
        val accounts = Prefs.accounts(ctx).toMutableList()
        val currentEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""
        val options = ArrayList<String>()
        for (a in accounts) {
            options.add(if (a == currentEmail) "$a  (current)" else a)
        }
        options.add("+ Add another account")
        options.add("- Log out")

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Switch account")
            .setItems(options.toTypedArray()) { _, which ->
                val choice = options[which]
                when {
                    choice.startsWith("+") -> {
                        FirebaseAuth.getInstance().signOut()
                        val intent = Intent(ctx, AuthActivity::class.java)
                        intent.putExtra("addAccount", true)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        ctx.startActivity(intent)
                        requireActivity().finishAffinity()
                    }
                    choice.startsWith("-") -> {
                        confirmLogout()
                    }
                    else -> {
                        val email = choice.substringBefore("  (")
                        val password = Prefs.lastPassword(ctx) ?: ""
                        if (password.isEmpty()) {
                            Toast.makeText(ctx, "No saved password for $email", Toast.LENGTH_SHORT).show()
                            return@setItems
                        }
                        Toast.makeText(ctx, "Switching to $email...", Toast.LENGTH_SHORT).show()
                        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val intent = Intent(ctx, com.lchat.app.MainActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    ctx.startActivity(intent)
                                    requireActivity().finishAffinity()
                                } else {
                                    Toast.makeText(ctx, "Failed: ${task.exception?.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadProfile() {
        Db.ref("users").child(myUid).get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener
                myUsername = snapshot.child("username").value as? String ?: ""
                val email = snapshot.child("email").value as? String ?: ""
                val photoData = snapshot.child("photoData").value as? String ?: ""

                binding.profileUsername.text = myUsername.ifEmpty { "L Chat user" }
                binding.profileEmail.text = email
                binding.usernameValue.text = myUsername

                val color = AppUtils.avatarColor(myUid)
                binding.profileAvatarBg.background = AppUtils.circle(color)
                binding.profileInitials.text = AppUtils.initials(myUsername.ifEmpty { email })
                binding.profileInitials.setTextColor(AppUtils.initialsTextColor(color))

                if (photoData.isNotEmpty()) {
                    val bitmap = ImageStore.decode(photoData)
                    if (bitmap != null && _binding != null) {
                        binding.profilePhoto.setImageBitmap(bitmap)
                        binding.profilePhoto.visibility = View.VISIBLE
                        binding.profileInitials.visibility = View.GONE
                    }
                }
            }
    }

    private fun showUsernameDialog() {
        val input = EditText(requireContext())
        input.setText(myUsername)
        input.hint = "Username"
        val padding = (20 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding / 2, padding, padding / 2)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change username")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) updateUsername(newName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUsername(newName: String) {
        Db.ref("users").child(myUid)
            .child("username").setValue(newName)
            .addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                myUsername = newName
                binding.profileUsername.text = newName
                binding.usernameValue.text = newName
                binding.profileInitials.text = AppUtils.initials(newName)
                Toast.makeText(requireContext(), "Username updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Update failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadProfilePhoto(uri: android.net.Uri) {
        Toast.makeText(requireContext(), "Saving photo...", Toast.LENGTH_SHORT).show()

        Thread {
            val photoData = try {
                ImageStore.encodeUri(requireContext().applicationContext, uri, 512, 80)
            } catch (e: Exception) {
                null
            }

            if (photoData == null) {
                activity?.runOnUiThread {
                    if (_binding != null) {
                        Toast.makeText(requireContext(), "Photo failed", Toast.LENGTH_SHORT).show()
                    }
                }
                return@Thread
            }

            Db.ref("users").child(myUid)
                .child("photoData").setValue(photoData)
                .addOnSuccessListener {
                    if (_binding == null) return@addOnSuccessListener
                    val bitmap = ImageStore.decode(photoData)
                    if (bitmap != null) {
                        binding.profilePhoto.setImageBitmap(bitmap)
                        binding.profilePhoto.visibility = View.VISIBLE
                        binding.profileInitials.visibility = View.GONE
                    }
                    Toast.makeText(requireContext(), "Photo updated", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    if (_binding == null) return@addOnFailureListener
                    Toast.makeText(
                        requireContext(),
                        "Photo failed: ${it.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }.start()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Log out")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Log out") { _, _ ->
                FirebaseAuth.getInstance().signOut()
                val intent = Intent(requireContext(), AuthActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                requireActivity().finishAffinity()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
