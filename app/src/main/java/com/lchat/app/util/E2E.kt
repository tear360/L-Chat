package com.lchat.app.util

import android.content.Context
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object E2E {

    private const val PREFS = "lchat_e2e_keys"
    private const val KEY_PRIVATE = "private_key"
    private const val KEY_PUBLIC = "public_key"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    fun ensureKeys(context: Context, uid: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_PRIVATE, null) == null) {
            val generator = KeyPairGenerator.getInstance("EC")
            generator.initialize(ECGenParameterSpec("secp256r1"))
            val pair = generator.generateKeyPair()
            prefs.edit()
                .putString(KEY_PRIVATE, Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP))
                .putString(KEY_PUBLIC, Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP))
                .apply()
        }
        publishPublicKey(uid)
    }

    private fun publishPublicKey(uid: String) {
        val prefs = contextPrefs
        val pub = prefs?.getString(KEY_PUBLIC, null) ?: return
        Db.ref("users").child(uid).child("publicKey").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.value as? String != pub) {
                    Db.ref("users").child(uid).child("publicKey").setValue(pub)
                }
            }
    }

    private var contextPrefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        contextPrefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun privateKeyBase64(): String? = contextPrefs?.getString(KEY_PRIVATE, null)

    fun publicKeyBase64(): String? = contextPrefs?.getString(KEY_PUBLIC, null)

    fun sharedKey(myPrivateKeyB64: String, otherPublicKeyB64: String): SecretKey? = try {
        val factory = KeyFactory.getInstance("EC")
        val privateKey = factory.generatePrivate(
            PKCS8EncodedKeySpec(Base64.decode(myPrivateKeyB64, Base64.NO_WRAP))
        )
        val publicKey = factory.generatePublic(
            X509EncodedKeySpec(Base64.decode(otherPublicKeyB64, Base64.NO_WRAP))
        )
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        val digest = MessageDigest.getInstance("SHA-256").digest(agreement.generateSecret())
        SecretKeySpec(digest, "AES")
    } catch (e: Exception) {
        null
    }

    fun keyWithOther(otherUid: String, onReady: (SecretKey?) -> Unit) {
        val priv = privateKeyBase64() ?: run { onReady(null); return }
        Db.ref("users").child(otherUid).child("publicKey").get()
            .addOnSuccessListener { snapshot ->
                val pub = snapshot.value as? String
                onReady(if (pub.isNullOrEmpty()) null else sharedKey(priv, pub))
            }
            .addOnFailureListener { onReady(null) }
    }

    fun encrypt(text: String, key: SecretKey?): String {
        if (key == null) return "plain:$text"
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
            val payload = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, payload, 0, iv.size)
            System.arraycopy(encrypted, 0, payload, iv.size, encrypted.size)
            "aes:" + Base64.encodeToString(payload, Base64.NO_WRAP)
        } catch (e: Exception) {
            "plain:$text"
        }
    }

    fun decrypt(payload: String, key: SecretKey?): String? {
        if (payload.startsWith("plain:")) return payload.removePrefix("plain:")
        if (key == null) return null
        return try {
            val bytes = Base64.decode(payload.removePrefix("aes:"), Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_LENGTH)
            val body = bytes.copyOfRange(IV_LENGTH, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}