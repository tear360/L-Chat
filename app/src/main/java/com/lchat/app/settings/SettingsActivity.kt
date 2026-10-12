package com.lchat.app.settings

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.lchat.app.BuildConfig
import com.lchat.app.MainActivity
import com.lchat.app.auth.AuthActivity
import com.lchat.app.friends.FriendsActivity
import com.lchat.app.databinding.ActivitySettingsBinding
import com.lchat.app.util.Db
import com.lchat.app.util.Prefs
import com.lchat.app.util.Updater

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val mAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.applyTheme(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.versionValue.text = "v${BuildConfig.VERSION_NAME}"
        binding.darkModeSwitch.isChecked = Prefs.isDarkMode(this)
        binding.darkModeSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setDarkMode(this, checked)
        }

        binding.backButton.setOnClickListener { finish() }
        binding.friendsRow.setOnClickListener {
            startActivity(Intent(this, FriendsActivity::class.java))
        }
        binding.changePasswordRow.setOnClickListener { showChangePasswordDialog() }
        binding.resetPasswordRow.setOnClickListener { showResetPasswordDialog() }
        binding.switchAccountRow.setOnClickListener { showAccountSwitcher() }
        binding.updateRow.setOnClickListener { checkUpdates() }
        binding.logoutRow.setOnClickListener { confirmLogout() }

        loadContactsCount()
    }

    override fun onResume() {
        super.onResume()
        loadContactsCount()
    }

    private fun loadContactsCount() {
        val uid = mAuth.currentUser?.uid ?: return
        Db.ref("users").child(uid).child("friends").get()
            .addOnSuccessListener {
                binding.friendsCount.text = "${it.childrenCount} ami(s)"
            }
    }

    private fun field(hint: String, type: Int): EditText {
        val input = EditText(this)
        input.hint = hint
        input.inputType = type
        val padding = (20 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding / 2, padding, padding / 2)
        return input
    }

    private fun showChangePasswordDialog() {
        val oldInput = field("Mot de passe actuel", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val newInput = field("Nouveau mot de passe", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val confirmInput = field("Confirmer", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        val p = (20 * resources.displayMetrics.density).toInt()
        container.setPadding(p, p / 2, p, 0)
        container.addView(oldInput)
        container.addView(newInput)
        container.addView(confirmInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("Changer le mot de passe")
            .setView(container)
            .setPositiveButton("Valider") { _, _ ->
                val old = oldInput.text.toString()
                val new = newInput.text.toString()
                if (new.length < 6) {
                    Toast.makeText(this, "6 caractères minimum", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (new != confirmInput.text.toString()) {
                    Toast.makeText(this, "Les mots de passe ne correspondent pas", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val user = mAuth.currentUser
                if (user == null) {
                    Toast.makeText(this, "Non connecté", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                user.updatePassword(new)
                    .addOnSuccessListener {
                        Prefs.saveAccount(this, user.email ?: "", new)
                        Toast.makeText(this, "Mot de passe modifié", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(
                            this,
                            "Échec : reconnecte-toi puis réessaie (${it.localizedMessage})",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showResetPasswordDialog() {
        val input = field("Ton email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val email = mAuth.currentUser?.email
        if (email != null) input.setText(email)

        MaterialAlertDialogBuilder(this)
            .setTitle("Mot de passe oublié")
            .setMessage("Un lien de réinitialisation sera envoyé par email.")
            .setView(input)
            .setPositiveButton("Envoyer") { _, _ ->
                val target = input.text.toString().trim()
                if (target.isEmpty()) return@setPositiveButton
                mAuth.sendPasswordResetEmail(target)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Email envoyé à $target", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Échec : ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showAccountSwitcher() {
        val accounts = Prefs.accounts(this).toMutableList()
        val currentEmail = mAuth.currentUser?.email ?: ""
        val options = ArrayList<String>()
        for (a in accounts) options.add(if (a == currentEmail) "$a  (actuel)" else a)
        options.add("+ Ajouter un compte")

        MaterialAlertDialogBuilder(this)
            .setTitle("Changer de compte")
            .setItems(options.toTypedArray()) { _, which ->
                val choice = options[which]
                if (choice.startsWith("+")) {
                    mAuth.signOut()
                    val intent = Intent(this, AuthActivity::class.java)
                    intent.putExtra("addAccount", true)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                } else {
                    val email = choice.substringBefore("  (")
                    val password = Prefs.lastPassword(this) ?: ""
                    if (password.isEmpty()) {
                        Toast.makeText(this, "Aucun mot de passe enregistré", Toast.LENGTH_SHORT).show()
                        return@setItems
                    }
                    Toast.makeText(this, "Connexion à $email...", Toast.LENGTH_SHORT).show()
                    mAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val intent = Intent(this, MainActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                startActivity(intent)
                                finishAffinity()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Échec : ${task.exception?.localizedMessage}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                }
            }
            .show()
    }

    private fun checkUpdates() {
        binding.updateLabel.text = "Recherche..."
        Updater.checkForUpdate(this) { tag, downloadUrl ->
            runOnUiThread {
                binding.updateLabel.text = "Rechercher des mises à jour"
                val current = "v${BuildConfig.VERSION_NAME}"
                if (tag == null) {
                    Toast.makeText(this, "Impossible de vérifier (pas de réseau ?)", Toast.LENGTH_LONG).show()
                } else if (tag == current) {
                    Toast.makeText(this, "À jour ($current)", Toast.LENGTH_SHORT).show()
                } else if (downloadUrl == null) {
                    Toast.makeText(this, "Version $tag disponible (ignorée)", Toast.LENGTH_LONG).show()
                } else {
                    Updater.promptUpdate(this, tag, downloadUrl)
                }
            }
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Se déconnecter")
            .setMessage("Voulez-vous vraiment te déconnecter ?")
            .setPositiveButton("Oui") { _, _ ->
                mAuth.signOut()
                val intent = Intent(this, AuthActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                finishAffinity()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}