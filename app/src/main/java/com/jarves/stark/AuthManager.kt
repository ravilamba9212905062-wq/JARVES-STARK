package com.jarves.stark

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Local fallback identity: a secret passphrase/PIN known only to the owner.
 * The clear text secret is never stored and is never written to JARVES memory.
 */
class AuthManager(context: Context) {
    private val prefs = context.getSharedPreferences("jarves_auth", Context.MODE_PRIVATE)
    private val rng = SecureRandom()

    fun hasSecret(): Boolean = prefs.getString("hash", null) != null

    fun setSecret(secret: String): Boolean {
        val clean = secret.trim()
        if (clean.length < 4) return false
        val salt = ByteArray(16).also(rng::nextBytes)
        val hash = derive(clean, salt)
        prefs.edit().putString("salt", hex(salt)).putString("hash", hex(hash)).apply()
        lock()
        return true
    }

    fun verify(secret: String): Boolean {
        val saltHex = prefs.getString("salt", null) ?: return false
        val hashHex = prefs.getString("hash", null) ?: return false
        val actual = derive(secret.trim(), unhex(saltHex))
        return MessageDigest.isEqual(actual, unhex(hashHex))
    }

    fun unlock() { prefs.edit().putBoolean("unlocked", true).apply() }
    fun lock() { prefs.edit().putBoolean("unlocked", false).apply() }
    fun isUnlocked(): Boolean = !hasSecret() || prefs.getBoolean("unlocked", false)

    private fun derive(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String): ByteArray = ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
