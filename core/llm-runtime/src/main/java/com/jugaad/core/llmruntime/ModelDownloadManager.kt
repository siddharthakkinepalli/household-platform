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

    enum class ModelVariant { FAST, DEEP }

    companion object {
        const val MODEL_SUBDIR = "models"

        // DEEP — Gemma 4 E2B Q4_K_M (existing, ~3.4 GB)
        const val MODEL_FILENAME     = "gemma4_e2b_q4km.gguf"
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/majentik/gemma-4-E2B-it-RotorQuant-GGUF-Q4_K_M/resolve/main/gemma-4-E2B-it-RotorQuant-Q4_K_M.gguf"
        const val MODEL_SIZE_BYTES   = 3_427_861_536L

        // FAST — LFM2.5-1.2B-Instruct Q4_K_M (official LiquidAI GGUF, ~731 MB)
        const val FAST_MODEL_FILENAME     = "lfm2_5_1b_q4km.gguf"
        const val FAST_MODEL_DOWNLOAD_URL =
            "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/main/LFM2.5-1.2B-Instruct-Q4_K_M.gguf"
        const val FAST_MODEL_SIZE_BYTES   = 731_000_000L
    }

    fun getModelFile(): File = getModelFile(ModelVariant.DEEP)

    fun getModelFile(variant: ModelVariant): File {
        val dir = File(context.filesDir, MODEL_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, if (variant == ModelVariant.FAST) FAST_MODEL_FILENAME else MODEL_FILENAME)
    }

    fun isModelDownloaded(): Boolean = isModelDownloaded(ModelVariant.DEEP)

    fun isModelDownloaded(variant: ModelVariant): Boolean {
        val file = getModelFile(variant)
        val minSize = if (variant == ModelVariant.FAST) FAST_MODEL_SIZE_BYTES / 2
                      else MODEL_SIZE_BYTES / 2
        return file.exists() && file.length() > minSize
    }

    fun getDownloadUrl(variant: ModelVariant): String =
        if (variant == ModelVariant.FAST) FAST_MODEL_DOWNLOAD_URL else MODEL_DOWNLOAD_URL

    suspend fun downloadModel(
        url: String,
        onProgress: (Long, Long) -> Unit,
        variant: ModelVariant = ModelVariant.DEEP
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val targetFile = getModelFile(variant)
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
