package com.lchat.app.call

import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.lchat.app.databinding.ActivityCallBinding
import com.lchat.app.util.AppUtils
import com.lchat.app.util.Db
import com.lchat.app.util.Prefs

class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private var otherUid = ""
    private var otherName = ""
    private var myUid = ""
    private var myName = ""
    private var callKey = ""
    private var isCaller = false
    private var connected = false
    private var callListener: ChildEventListener? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var seconds = 0
    private val tick = object : Runnable {
        override fun run() {
            seconds++
            binding.timer.text = String.format("%02d:%02d", seconds / 60, seconds % 60)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.applyTheme(this)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        myUid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        otherUid = intent.getStringExtra("otherUid") ?: run { finish(); return }
        otherName = intent.getStringExtra("otherName") ?: ""
        callKey = AppUtils.pairKey(myUid, otherUid)

        isCaller = intent.getBooleanExtra("isCaller", true)

        Db.ref("users").child(myUid).child("username").get()
            .addOnSuccessListener { myName = it.value as? String ?: "" }

        binding.otherName.text = if (isCaller) otherName else "L Chat"
        binding.statusText.text = if (isCaller) "Appel en cours..." else "Appel entrant..."

        binding.hangupButton.setOnClickListener { endCall() }
        binding.answerButton.setOnClickListener { answerCall() }
        binding.answerButton.visibility = if (isCaller) android.view.View.GONE else android.view.View.VISIBLE

        if (isCaller) {
            startRinging()
            startCallTimeout()
            writeCallSignal("ringing")
        }

        listenCall()
    }

    private fun writeCallSignal(state: String) {
        Db.ref("calls").child(callKey).setValue(mapOf(
            "state" to state,
            "fromUid" to myUid,
            "fromName" to myName,
            "toUid" to otherUid,
            "toName" to otherName,
            "startedAt" to System.currentTimeMillis()
        ))
    }

    private fun listenCall() {
        val ref = Db.ref("calls").child(callKey)
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) = handleSignal(snapshot)
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = handleSignal(snapshot)
            override fun onChildRemoved(snapshot: DataSnapshot) {
                if (!connected) {
                    runOnUiThread {
                        Toast.makeText(this@CallActivity, "Appel terminé", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        callListener = listener
        ref.addChildEventListener(listener)
    }

    private fun handleSignal(snapshot: DataSnapshot) {
        val state = snapshot.child("state").value as? String ?: return
        val fromUid = snapshot.child("fromUid").value as? String ?: return
        if (fromUid == myUid) return

        runOnUiThread {
            when (state) {
                "ringing" -> {
                    if (!isCaller) {
                        startRinging()
                        startCallTimeout()
                        binding.otherName.text = snapshot.child("fromName").value as? String ?: otherName
                    }
                }
                "connected" -> {
                    if (!connected) {
                        connected = true
                        stopRinging()
                        binding.statusText.text = "En ligne"
                        binding.timer.visibility = android.view.View.VISIBLE
                        binding.answerButton.visibility = android.view.View.GONE
                        binding.hangupButton.setBackgroundResource(com.lchat.app.R.drawable.ic_call_end_active)
                        handler.post(tick)
                    }
                }
                "declined", "ended", "missed" -> {
                    stopRinging()
                    if (!connected) {
                        Toast.makeText(this@CallActivity, "Appel non décroché", Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }
            }
        }
    }

    private fun answerCall() {
        connected = true
        stopRinging()
        handler.removeCallbacks(tick)
        binding.statusText.text = "En ligne"
        binding.timer.visibility = android.view.View.VISIBLE
        binding.answerButton.visibility = android.view.View.GONE
        handler.post(tick)
        writeCallSignal("connected")
    }

    private fun endCall() {
        handler.removeCallbacks(tick)
        stopRinging()
        writeCallSignal(if (connected) "ended" else if (isCaller) "ended" else "declined")
        Db.ref("calls").child(callKey).removeValue()
        finish()
    }

    private fun startCallTimeout() {
        handler.postDelayed({
            if (!connected) {
                stopRinging()
                writeCallSignal("missed")
                Db.ref("calls").child(callKey).removeValue()
                Toast.makeText(this, "Pas de réponse", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, 30000)
    }

    private fun startRinging() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)
            if (ringtone?.isPlaying == false) ringtone?.play()
        } catch (e: Exception) {}
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                it.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    private fun stopRinging() {
        ringtone?.let { if (it.isPlaying) it.stop() }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tick)
        stopRinging()
        callListener?.let { Db.ref("calls").child(callKey).removeEventListener(it) }
    }
}