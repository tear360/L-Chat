package com.lchat.app.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.lchat.app.MainActivity
import com.lchat.app.databinding.ActivityAuthBinding
import com.lchat.app.util.Db
import com.lchat.app.util.E2E
import com.lchat.app.util.Prefs

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val mAuth = FirebaseAuth.getInstance()
    private var signUpMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.applyTheme(this)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val addAccountMode = intent.getBooleanExtra("addAccount", false)
        if (mAuth.currentUser != null && !addAccountMode) {
            goMain()
            return
        }

        // Pré-remplir le dernier compte utilisé pour faciliter la connexion
        Prefs.lastEmail(this)?.let {
            binding.emailInput.setText(it)
            Prefs.lastPassword(this)?.let { p -> binding.passwordInput.setText(p) }
        }

        binding.toggleAuth.setOnClickListener { toggleMode() }
        binding.authButton.setOnClickListener { submit() }
        binding.forgotPassword.setOnClickListener { sendResetEmail() }
    }

    private fun sendResetEmail() {
        val email = binding.emailInput.text.toString().trim()
        if (email.isEmpty()) {
            showError("Entre ton email d'abord.")
            return
        }
        mAuth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                showError("Email de réinitialisation envoyé à $email")
            }
            .addOnFailureListener {
                showError("Échec : ${it.localizedMessage}")
            }
    }

    private fun toggleMode() {
        signUpMode = !signUpMode
        binding.usernameLayout.visibility = if (signUpMode) View.VISIBLE else View.GONE
        binding.authButton.text = if (signUpMode) "S'inscrire" else "Se connecter"
        binding.toggleAuth.text =
            if (signUpMode) "Deja un compte ? Connecte-toi"
            else "Pas de compte ? Inscris-toi"
        hideError()
    }

    private fun submit() {
        hideError()

        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()
        val username = binding.usernameInput.text.toString().trim()

        if (email.isEmpty()) {
            showError("Entre ton email.")
            return
        }
        if (password.length < 6) {
            showError("6 caractères minimum pour le mot de passe.")
            return
        }
        if (signUpMode && username.isEmpty()) {
            showError("Choisis un pseudo.")
            return
        }

        hideKeyboard()
        binding.authButton.isEnabled = false
        binding.authButton.text = if (signUpMode) "Création du compte..." else "Connexion..."

        if (signUpMode) {
            createAccount(email, password, username)
        } else {
            login(email, password)
        }
    }

    private fun login(email: String, password: String) {
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Prefs.saveAccount(this, email, password)
                    val uid = mAuth.currentUser!!.uid
                    ensureProfile(uid, email)
                } else {
                    resetButton()
                    showError(task.exception?.localizedMessage ?: "Connexion échouée. Réessaie.")
                }
            }
    }

    private fun createAccount(email: String, password: String, username: String) {
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val uid = mAuth.currentUser!!.uid
                    val profile = mapOf(
                        "username" to username,
                        "email" to email,
                        "createdAt" to System.currentTimeMillis()
                    )
                    Db.ref("users").child(uid)
                        .setValue(profile)
                        .addOnCompleteListener {
                            prepareKeys(uid)
                            goMain()
                        }
                } else {
                    resetButton()
                    showError(task.exception?.localizedMessage ?: "Inscription échouée. Réessaie.")
                }
            }
    }

    private fun ensureProfile(uid: String, email: String) {
        Db.ref("users").child(uid).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    prepareKeys(uid)
                    goMain()
                } else {
                    val profile = mapOf(
                        "username" to email.substringBefore("@"),
                        "email" to email,
                        "createdAt" to System.currentTimeMillis()
                    )
                    Db.ref("users").child(uid)
                        .setValue(profile)
                        .addOnCompleteListener {
                            prepareKeys(uid)
                            goMain()
                        }
                }
            }
            .addOnFailureListener {
                prepareKeys(uid)
                goMain()
            }
    }

    private fun prepareKeys(uid: String) {
        E2E.init(this)
        E2E.ensureKeys(this, uid)
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun resetButton() {
        binding.authButton.isEnabled = true
        binding.authButton.text = if (signUpMode) "S'inscrire" else "Se connecter"
    }

    private fun showError(message: String) {
        binding.authError.text = message
        binding.authError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.authError.visibility = View.GONE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}