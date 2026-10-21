package com.jimmyyu.qvgallery.data

import android.os.SystemClock
import java.security.MessageDigest
import java.security.SecureRandom

/** Password hashing and in-memory unlock state shared across activities. */
object LockManager {
    @Volatile private var unlockedUntilBackground = false
    @Volatile private var backgroundAt = 0L

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()

    fun hash(secret: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        var bytes = (salt + secret).toByteArray()
        repeat(10_000) { bytes = md.digest(bytes) }
        return bytes.toHex()
    }

    fun verify(secret: String, settings: AppSettings): Boolean =
        settings.lockHash.isNotEmpty() && hash(secret, settings.lockSalt) == settings.lockHash

    fun markUnlocked() {
        unlockedUntilBackground = true
        backgroundAt = 0L
    }

    fun onAppBackground() {
        backgroundAt = SystemClock.elapsedRealtime()
    }

    /** Re-locks if the app stayed in the background longer than the configured delay. */
    fun onAppForeground(relockSeconds: Int) {
        val since = backgroundAt
        if (since != 0L && SystemClock.elapsedRealtime() - since > relockSeconds * 1000L) {
            unlockedUntilBackground = false
        }
        backgroundAt = 0L
    }

    fun isUnlocked(settings: AppSettings): Boolean =
        settings.lockType == LockType.NONE || settings.lockHash.isEmpty() || unlockedUntilBackground

    fun lock() {
        unlockedUntilBackground = false
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
