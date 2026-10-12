package com.lchat.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.lchat.app.auth.AuthActivity
import com.lchat.app.databinding.ActivityMainBinding
import com.lchat.app.ui.CameraFragment
import com.lchat.app.ui.ChatFragment
import com.lchat.app.ui.ProfileFragment
import com.lchat.app.ui.StoriesFragment
import com.lchat.app.util.E2E
import com.lchat.app.util.Prefs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Prefs.applyTheme(this)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        E2E.init(this)
        E2E.ensureKeys(this, currentUser.uid)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_chat -> ChatFragment()
                R.id.nav_camera -> CameraFragment()
                R.id.nav_stories -> StoriesFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
            true
        }

        binding.bottomNav.selectedItemId = R.id.nav_camera
    }
}
