package com.lchat.app.snap

import com.lchat.app.util.Db

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.lchat.app.databinding.ActivityViewSnapBinding
import com.lchat.app.util.ImageStore

class ViewSnapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewSnapBinding
    private var animator: ValueAnimator? = null
    private var closed = false

    private val snapKey: String by lazy { intent.getStringExtra("snapKey") ?: "" }
    private val imageData: String by lazy { intent.getStringExtra("imageData") ?: "" }
    private val caption: String by lazy { intent.getStringExtra("caption") ?: "" }
    private val fromName: String by lazy { intent.getStringExtra("fromName") ?: "" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewSnapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.senderName.text = fromName

        if (caption.isNotEmpty()) {
            binding.captionText.text = caption
            binding.captionText.visibility = View.VISIBLE
        }

        val bitmap = ImageStore.decode(imageData)
        if (bitmap != null) {
            binding.snapImage.setImageBitmap(bitmap)
        }
        binding.imageProgress.visibility = View.GONE

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                closeSnap()
            }
        })

        startTimer()
    }

    override fun onPause() {
        super.onPause()
        cancelTimer()
    }

    override fun onResume() {
        super.onResume()
        if (!closed && (animator == null || animator?.isStarted != true)) {
            startTimer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTimer()
    }

    private fun cancelTimer() {
        animator?.removeAllListeners()
        animator?.cancel()
    }

    private fun startTimer() {
        cancelTimer()
        binding.timerBar.scaleX = 0f
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5000
            interpolator = LinearInterpolator()
            addUpdateListener { valueAnimator ->
                binding.timerBar.scaleX = valueAnimator.animatedValue as Float
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!closed) closeSnap()
                }
            })
        }
        animator = anim
        anim.start()
    }

    private fun closeSnap() {
        if (closed) return
        closed = true
        cancelTimer()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null && snapKey.isNotEmpty()) {
            Db.ref("users")
                .child(uid)
                .child("snaps")
                .child(snapKey)
                .removeValue()
        }
        finish()
    }
}
