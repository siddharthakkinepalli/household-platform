package com.household.app.sync

import java.util.UUID

data class PairingToken(
    val ip: String,
    val port: Int,
    val token: String,
    val expiresAt: Long
) {
    fun isExpired() = System.currentTimeMillis() > expiresAt

    fun toQrPayload(): String =
        "jugaad://pair?ip=$ip&port=$port&token=$token&exp=$expiresAt"

    companion object {
        private const val TTL_MS = 5 * 60 * 1000L // 5 minutes

        fun generate(ip: String, port: Int) = PairingToken(
            ip = ip,
            port = port,
            token = UUID.randomUUID().toString().replace("-", "").take(16),
            expiresAt = System.currentTimeMillis() + TTL_MS
        )

        fun fromQrPayload(payload: String): PairingToken? {
            return try {
                val uri = android.net.Uri.parse(payload)
                PairingToken(
                    ip = uri.getQueryParameter("ip") ?: return null,
                    port = uri.getQueryParameter("port")?.toInt() ?: return null,
                    token = uri.getQueryParameter("token") ?: return null,
                    expiresAt = uri.getQueryParameter("exp")?.toLong() ?: return null
                )
            } catch (_: Exception) { null }
        }
    }
}
