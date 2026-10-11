package com.lchat.app.snap

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.lchat.app.databinding.ActivityCreateSnapBinding
import com.lchat.app.util.ImageStore
import java.io.File

class CreateSnapActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "imagePath"
        private const val REQUEST_SEND = 100
    }

    private lateinit var binding: ActivityCreateSnapBinding
    private val imagePath: String? by lazy { intent.getStringExtra(EXTRA_IMAGE_PATH) }
    private var uploading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateSnapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val path = imagePath
        if (path == null || !File(path).exists()) {
            finish()
            return
        }

        binding.previewImage.load(File(path))

        binding.closeButton.setOnClickListener { finish() }
        binding.storyButton.setOnClickListener { addToStory() }
        binding.sendToButton.setOnClickListener { openRecipients() }
    }

    private fun openRecipients() {
        val intent = Intent(this, ChooseRecipientsActivity::class.java)
        intent.putExtra(ChooseRecipientsActivity.EXTRA_IMAGE_PATH, imagePath)
        intent.putExtra(ChooseRecipientsActivity.EXTRA_CAPTION, binding.captionInput.text.toString())
        startActivityForResult(intent, REQUEST_SEND)
    }

    private fun addToStory() {
        if (uploading) return
        uploading = true
        setButtonsEnabled(false)
        binding.uploadProgress.visibility = View.VISIBLE

        myUsername { fromName ->
            Thread {
                val imageData = try {
                    ImageStore.encodeFile(File(imagePath!!))
                } catch (e: Exception) {
                    null
                }
                runOnUiThread {
                    if (imageData == null) {
                        saveFailed()
                        return@runOnUiThread
                    }
                    val story = mapOf(
                        "fromUid" to FirebaseAuth.getInstance().currentUser!!.uid,
                        "fromName" to fromName,
                        "imageData" to imageData,
                        "caption" to binding.captionInput.text.toString(),
                        "createdAt" to System.currentTimeMillis()
                    )
                    FirebaseDatabase.getInstance().getReference("stories")
                        .push()
                        .setValue(story)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Added to your Story", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { saveFailed() }
                }
            }.start()
        }
    }

    private fun myUsername(onResult: (String) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return finish()
        FirebaseDatabase.getInstance().getReference("users").child(uid).child("username").get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.value as? String ?: "Someone")
            }
            .addOnFailureListener { onResult("Someone") }
    }

    private fun saveFailed() {
        uploading = false
        setButtonsEnabled(true)
        binding.uploadProgress.visibility = View.GONE
        Toast.makeText(this, "Could not save the snap. Try again.", Toast.LENGTH_SHORT).show()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.storyButton.isEnabled = enabled
        binding.sendToButton.isEnabled = enabled
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SEND && resultCode == Activity.RESULT_OK) {
            finish()
        }
    }
}
