package com.dubey.timelimiter.utils

import java.security.MessageDigest
import java.security.SecureRandom

object SecurityUtils {
    fun hashPin(pin: String, salt: ByteArray = generateSalt()): Pair<String, ByteArray> {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hash = digest.digest(pin.toByteArray())
        return Pair(hash.joinToString("") { "%02x".format(it) }, salt)
    }

    fun verifyPin(pin: String, hashedPin: String, salt: ByteArray): Boolean {
        val (newHash, _) = hashPin(pin, salt)
        return newHash == hashedPin
    }

    private fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt
    }
}
