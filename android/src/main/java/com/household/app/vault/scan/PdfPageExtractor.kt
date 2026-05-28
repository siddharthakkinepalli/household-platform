package com.household.app.vault.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfPageExtractor {

    private const val MIN_DIGITAL_TEXT_LENGTH = 50
    private const val RASTER_SCALE = 2f

    suspend fun extractText(
        @Suppress("UNUSED_PARAMETER") context: Context,
        pdfFile: File,
        cacheManager: OcrCacheManager? = null
    ): String =
        withContext(Dispatchers.IO) {
            val nativeText = tryNativeExtract(pdfFile)
            if (nativeText.length >= MIN_DIGITAL_TEXT_LENGTH) return@withContext nativeText
            rasterAndOcr(pdfFile, cacheManager)
        }

    private fun tryNativeExtract(pdfFile: File): String = try {
        PDDocument.load(pdfFile).use { doc ->
            PDFTextStripper().getText(doc).trim()
        }
    } catch (_: Exception) {
        ""
    }

    private suspend fun rasterAndOcr(
        pdfFile: File,
        cacheManager: OcrCacheManager? = null
    ): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        fd.use {
            PdfRenderer(fd).use { renderer ->
                val pageCount = minOf(renderer.pageCount, 5)
                for (i in 0 until pageCount) {
                    renderer.openPage(i).use { page ->
                        val w = (page.width * RASTER_SCALE).toInt()
                        val h = (page.height * RASTER_SCALE).toInt()
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, Matrix().apply { setScale(RASTER_SCALE, RASTER_SCALE) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val pageText = try {
                            val hash = cacheManager?.computeHash(bitmap)
                            val cached = if (hash != null) cacheManager.getCached(hash) else null
                            if (cached != null) {
                                cached
                            } else {
                                val ocrResult = OcrRouter.recognizeText(bitmap)
                                if (hash != null) cacheManager.store(hash, ocrResult)
                                ocrResult
                            }
                        } finally {
                            bitmap.recycle()
                        }
                        if (pageText.isNotBlank()) sb.append(pageText).append('\n')
                    }
                }
            }
        }
        sb.toString().trim()
    }
}
