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

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val mAuth = FirebaseAuth.getInstance()
    private var signUpMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (mAuth.currentUser != null) {
            goMain()
            return
        }

        binding.toggleAuth.setOnClickListener { toggleMode() }
        binding.authButton.setOnClickListener { submit() }
    }

    private fun toggleMode() {
        signUpMode = !signUpMode
        binding.usernameLayout.visibility = if (signUpMode) View.VISIBLE else View.GONE
        binding.authButton.text = if (signUpMode) "Sign Up" else "Log In"
        binding.toggleAuth.text =
            if (signUpMode) "Already have an account? Log in"
            else "Don't have an account? Sign up"
        hideError()
    }

    private fun submit() {
        hideError()

        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()
        val username = binding.usernameInput.text.toString().trim()

        if (email.isEmpty()) {
            showError("Enter your email.")
            return
        }
        if (password.length < 6) {
            showError("Password must be at least 6 characters.")
            return
        }
        if (signUpMode && username.isEmpty()) {
            showError("Choose a username.")
            return
        }

        hideKeyboard()
        binding.authButton.isEnabled = false
        binding.authButton.text = if (signUpMode) "Creating account..." else "Logging in..."

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
                    val uid = mAuth.currentUser!!.uid
                    ensureProfile(uid, email)
                } else {
                    resetButton()
                    showError(task.exception?.localizedMessage ?: "Login failed. Try again.")
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
                    showError(task.exception?.localizedMessage ?: "Sign up failed. Try again.")
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
        binding.authButton.text = if (signUpMode) "Sign Up" else "Log In"
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