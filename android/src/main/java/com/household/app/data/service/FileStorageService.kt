package com.household.app.data.service

import android.content.Context
import android.net.Uri
import java.io.File

class FileStorageService(private val context: Context) {
    fun saveReceiptImage(tempUri: Uri): String {
        val fileName = "receipt_${System.currentTimeMillis()}.jpg"
        val directory = File(context.filesDir, "vault_receipts")
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val destinationFile = File(directory, fileName)

        context.contentResolver.openInputStream(tempUri)?.use { input ->
            destinationFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return destinationFile.absolutePath
    }
}
