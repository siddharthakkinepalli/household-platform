package com.jugaad.core.security

import java.security.MessageDigest

object SecurityUtils {

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
