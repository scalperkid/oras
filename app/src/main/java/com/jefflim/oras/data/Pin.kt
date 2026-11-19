package com.jefflim.oras.data

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Access codes are never stored in the clear. PBKDF2-HMAC-SHA256 with a per-code salt,
 * so reading the app's data file tells you nothing useful and one child cannot learn
 * another's code by poking around.
 */
object Pin {
    // A 4 to 6 digit code has a small keyspace no matter what, so the iteration count is
    // not what protects it: the freeze-after-wrong-tries rule is. Kept low enough that
    // checking every child's code on one unlock stays imperceptible on a mid-range phone.
    private const val ITERATIONS = 12_000
    private const val KEY_BITS = 256
    private val rng = SecureRandom()

    fun hash(code: String): String {
        val salt = ByteArray(16).also(rng::nextBytes)
        return enc(salt) + ":" + enc(derive(code, salt))
    }

    fun verify(code: String, stored: String): Boolean {
        if (code.isEmpty() || stored.isEmpty()) return false
        val parts = stored.split(":")
        if (parts.size != 2) return false
        return try {
            val salt = dec(parts[0])
            constantTimeEquals(derive(code, salt), dec(parts[1]))
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun derive(code: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(code.toCharArray(), salt, ITERATIONS, KEY_BITS))
            .encoded

    /** Compares every byte regardless of mismatch, so timing leaks nothing. */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    // The JDK encoder, not android.util.Base64, so this class stays plain Java and the
    // hashing can be tested without a device.
    private fun enc(b: ByteArray): String = Base64.getEncoder().withoutPadding().encodeToString(b)
    private fun dec(s: String): ByteArray = Base64.getDecoder().decode(s)
}
