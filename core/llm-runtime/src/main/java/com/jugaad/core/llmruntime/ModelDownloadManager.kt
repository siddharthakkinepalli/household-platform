package com.jugaad.core.llmruntime

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val MODEL_FILENAME = "gemma4_e2b_q4km.gguf"
        const val MODEL_SUBDIR = "models"
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/majentik/gemma-4-E2B-it-RotorQuant-GGUF-Q4_K_M/resolve/main/gemma-4-E2B-it-RotorQuant-Q4_K_M.gguf"
        const val MODEL_SIZE_BYTES = 3_427_861_536L
    }

    fun getModelFile(): File {
        val dir = File(context.filesDir, MODEL_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MODEL_FILENAME)
    }

    fun isModelDownloaded(): Boolean {
        val file = getModelFile()
        return file.exists() && file.length() > MODEL_SIZE_BYTES / 2
    }

    suspend fun downloadModel(
        url: String,
        onProgress: (Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val targetFile = getModelFile()
            val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")
            
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode}")
            }

            val totalBytes = connection.contentLengthLong  // contentLength is Int — overflows for >2GB files
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(512 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        onProgress(downloadedBytes, totalBytes)
                    }
                }
            }

            if (tempFile.renameTo(targetFile)) {
                targetFile
            } else {
                throw Exception("Failed to rename temp file to target file")
            }
        }
    }
}
