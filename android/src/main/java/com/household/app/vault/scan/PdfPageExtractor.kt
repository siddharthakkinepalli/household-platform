package com.household.app.vault.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfPageExtractor {

    private const val MIN_DIGITAL_TEXT_LENGTH = 50
    private const val RASTER_SCALE = 3f  // 3x ≈ 216 DPI minimum; needed for reliable MRZ OCR

    // CamScanner and similar apps embed watermark-only text layers in image-based PDFs.
    // Strip these before checking length so we fall through to raster+OCR instead of returning noise.
    private val SCANNER_WATERMARKS = listOf(
        "scanned by camscanner",
        "adobe scan",
        "microsoft lens",
        "scanbot"
    )

    private fun stripWatermarks(text: String): String {
        var s = text.lowercase()
        for (wm in SCANNER_WATERMARKS) s = s.replace(wm, "")
        return s.trim()
    }

    fun isWatermarkOnly(text: String): Boolean =
        text.isNotBlank() && stripWatermarks(text).length < MIN_DIGITAL_TEXT_LENGTH

    suspend fun extractText(
        @Suppress("UNUSED_PARAMETER") context: Context,
        pdfFile: File,
        cacheManager: OcrCacheManager? = null
    ): String =
        withContext(Dispatchers.IO) {
            val nativeText = tryNativeExtract(pdfFile)
            if (stripWatermarks(nativeText).length >= MIN_DIGITAL_TEXT_LENGTH) return@withContext nativeText
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
                        Log.d("PdfPageExtractor", "page $i: pdf_w=${page.width} pdf_h=${page.height} bitmap=${w}x${h}")
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, Matrix().apply { setScale(RASTER_SCALE, RASTER_SCALE) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val centerPixel = bitmap.getPixel(w / 2, h / 2)
                        Log.d("PdfPageExtractor", "page $i: center pixel R=${Color.red(centerPixel)} G=${Color.green(centerPixel)} B=${Color.blue(centerPixel)} A=${Color.alpha(centerPixel)}")
                        val pageText = try {
                            val hash = cacheManager?.computeHash(bitmap)
                            val cached = if (hash != null) cacheManager.getCached(hash) else null
                            if (cached != null) {
                                cached
                            } else {
                                var ocrResult = OcrRouter.recognizeText(bitmap)
                                // If preprocessed OCR returned noise, retry ML Kit on raw bitmap.
                                // Amber/warm-tinted scans (e.g. CamScanner) often survive without OpenCV binarisation.
                                if (ocrResult.length < 10) {
                                    val raw = OcrRouter.recognizeRaw(bitmap)
                                    if (raw.length > ocrResult.length) {
                                        Log.d("PdfPageExtractor", "page $i raw fallback used: preprocessed=${ocrResult.length} raw=${raw.length}")
                                        ocrResult = raw
                                    }
                                }
                                Log.d("PdfPageExtractor", "page $i OCR result len=${ocrResult.length} preview=${ocrResult.take(100).replace('\n',' ')}")
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
