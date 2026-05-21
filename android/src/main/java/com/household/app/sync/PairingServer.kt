package com.household.app.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

sealed class ServerState {
    object Waiting : ServerState()
    object Transferred : ServerState()
    data class Error(val message: String) : ServerState()
}

class PairingServer(
    private val token: PairingToken,
    private val backupJson: String,
    private val apkPath: String,
    private val onStateChange: (ServerState) -> Unit
) {
    private var serverSocket: ServerSocket? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket(token.port).apply {
                soTimeout = 5 * 60 * 1000
            }
            Log.d("PairingServer", "Listening on ${token.ip}:${token.port}")
            onStateChange(ServerState.Waiting)

            while (true) {
                val client: Socket = try {
                    serverSocket!!.accept()
                } catch (e: SocketTimeoutException) {
                    onStateChange(ServerState.Error("Timed out — no partner connected within 5 minutes"))
                    return@withContext
                }

                val done = handleClient(client)
                if (done) return@withContext
            }
        } catch (e: Exception) {
            if (serverSocket?.isClosed == true) return@withContext // normal shutdown
            Log.e("PairingServer", "Server error", e)
            onStateChange(ServerState.Error(e.message ?: "Unknown error"))
        } finally {
            stop()
        }
    }

    // Returns true when the server should stop (backup delivered).
    private fun handleClient(socket: Socket): Boolean {
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
            val requestLine = reader.readLine() ?: return false
            // Drain remaining headers — browsers send full HTTP request before reading response
            while (true) { if ((reader.readLine() ?: "").isEmpty()) break }
            Log.d("PairingServer", "Request: $requestLine")

            return when {
                requestLine.startsWith("GET /install") -> {
                    serveApk(it)
                    false // keep server alive for retries
                }
                requestLine.startsWith("GET /backup") -> {
                    val valid = requestLine.contains("token=${token.token}") && !token.isExpired()
                    if (valid) {
                        serveJson(it)
                        onStateChange(ServerState.Transferred)
                        true // backup done — shut down
                    } else {
                        send403(it)
                        false
                    }
                }
                else -> { send404(it); false }
            }
        }
    }

    private fun serveJson(socket: Socket) {
        val body = backupJson.toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream()
        PrintWriter(out, false).apply {
            println("HTTP/1.1 200 OK")
            println("Content-Type: application/json")
            println("Content-Length: ${body.size}")
            println("Connection: close")
            println()
            flush()
        }
        out.write(body)
        out.flush()
    }

    private fun serveApk(socket: Socket) {
        try {
            val apkFile = java.io.File(apkPath)
            val out = socket.getOutputStream()
            PrintWriter(out, false).apply {
                println("HTTP/1.1 200 OK")
                println("Content-Type: application/vnd.android.package-archive")
                println("Content-Disposition: attachment; filename=\"jugaad.apk\"")
                println("Content-Length: ${apkFile.length()}")
                println("Connection: close")
                println()
                flush()
            }
            apkFile.inputStream().use { it.copyTo(out) }
            out.flush()
            Log.d("PairingServer", "APK served (${apkFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e("PairingServer", "APK serve error", e)
        }
    }

    private fun send403(socket: Socket) {
        PrintWriter(socket.getOutputStream(), true).apply {
            println("HTTP/1.1 403 Forbidden\r\nConnection: close\r\n")
        }
    }

    private fun send404(socket: Socket) {
        PrintWriter(socket.getOutputStream(), true).apply {
            println("HTTP/1.1 404 Not Found\r\nConnection: close\r\n")
        }
    }

    fun stop() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    companion object {
        fun findFreePort(): Int = ServerSocket(0).use { it.localPort }

        fun getApkPath(context: Context): String =
            context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir
    }
}
