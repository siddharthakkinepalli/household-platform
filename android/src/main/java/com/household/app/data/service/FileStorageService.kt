package com.household.app.data.service

import android.content.Context
import android.net.Uri
import java.io.File

class FileStorageService(private val context: Context) {
    fun saveReceiptImage(tempUri: Uri): String = saveDocument(tempUri, "image/jpeg")

    fun saveDocument(uri: Uri, mimeType: String): String {
        val ext = extensionForMime(mimeType)
        val fileName = "vault_${System.currentTimeMillis()}.$ext"
        val directory = File(context.filesDir, "vault_receipts")
        if (!directory.exists()) directory.mkdirs()
        val dest = File(directory, fileName)
        context.contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
        return dest.absolutePath
    }

    private fun extensionForMime(mimeType: String): String = when {
        mimeType.contains("pdf")  -> "pdf"
        mimeType.contains("png")  -> "png"
        mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
        else -> "bin"
    }
}
