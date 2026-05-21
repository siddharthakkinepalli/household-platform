package com.household.app.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed class FetchResult {
    data class Success(val json: String) : FetchResult()
    data class Failure(val message: String) : FetchResult()
}

object PairingClient {
    suspend fun fetchBackup(token: PairingToken): FetchResult = withContext(Dispatchers.IO) {
        if (token.isExpired()) return@withContext FetchResult.Failure("QR code has expired")
        try {
            val url = URL("http://${token.ip}:${token.port}/backup?token=${token.token}")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.requestMethod = "GET"

            val code = conn.responseCode
            if (code == 200) {
                val json = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                FetchResult.Success(json)
            } else {
                FetchResult.Failure("Server returned $code")
            }
        } catch (e: Exception) {
            FetchResult.Failure(e.message ?: "Connection failed")
        }
    }
}
