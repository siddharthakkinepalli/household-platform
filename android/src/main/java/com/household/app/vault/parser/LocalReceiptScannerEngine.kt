package com.household.app.vault.parser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.household.app.domain.models.vault.TextLinePayload
import com.household.app.domain.models.vault.VisionTextPayload
import java.util.regex.Pattern
import kotlin.math.abs

/**
 * Stage 1: Grayscale + contrast enhancement applied to camera bitmaps before
 * ML Kit processes them. Better input → fewer OCR substitution errors.
 */
object ImagePreProcessor {

    fun optimizeForOcr(source: Bitmap): Bitmap {
        val config = source.config ?: Bitmap.Config.ARGB_8888
        val output = Bitmap.createBitmap(source.width, source.height, config)
        val canvas = Canvas(output)
        val paint = Paint()

        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val contrastFactor = 1.7f
        val contrast = ColorMatrix(floatArrayOf(
            contrastFactor, 0f, 0f, 0f, -128f * (contrastFactor - 1f),
            0f, contrastFactor, 0f, 0f, -128f * (contrastFactor - 1f),
            0f, 0f, contrastFactor, 0f, -128f * (contrastFactor - 1f),
            0f, 0f, 0f, 1f, 0f
        ))
        val combined = ColorMatrix().apply { setConcat(contrast, grayscale) }
        paint.colorFilter = ColorMatrixColorFilter(combined)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }
}

/**
 * Stage 2: Spatial row reconstruction + amount extraction on top of VisionTextPayload.
 *
 * ML Kit splits a receipt into text blocks by visual region — "Vollmilch" (left column)
 * and "1,29" (right column) end up in separate blocks. This scanner clusters all text
 * lines by Y-center proximity so both tokens appear on the same reconstructed row,
 * enabling reliable price-per-item and total extraction.
 *
 * Works on VisionTextPayload (bounding boxes already preserved by VisionTextAdapter)
 * so MlKitOcrEngine's return type is unchanged.
 */
object LocalReceiptScanner {

    data class ScannedReceipt(
        val merchant: String,
        val date: String?,
        val totalAmount: Double,
        val category: String,
        val isInvoice: Boolean,
        val reconstructedRows: List<String>
    )

    fun processMlKitPayload(payload: VisionTextPayload): ScannedReceipt {
        val rows = reconstructAdaptiveRows(payload.allLines())
        val fullText = rows.joinToString("\n")

        // Delegate merchant/category/date/line-items to ReceiptTextParser (text-based, no bbox advantage)
        val textParsed = ReceiptTextParser.parse(fullText)
        val isInvoice = textParsed.isInvoice

        // Use spatial row reconstruction only for amount extraction — it handles left/right price
        // alignment that the flat-text parser misses
        val spatialAmount = extractAmountFromRows(rows, isInvoice)

        return ScannedReceipt(
            merchant = textParsed.merchant,
            date = textParsed.date,
            totalAmount = if (spatialAmount > 0.0) spatialAmount else textParsed.totalAmount,
            category = textParsed.category,
            isInvoice = isInvoice,
            reconstructedRows = rows
        )
    }

    /**
     * Clusters text lines that share a horizontal band (same Y-center ± 40% of line height).
     * Lines within the same band are sorted left→right and joined, reconstructing the
     * original "item  price" layout that ML Kit splits across blocks.
     */
    private fun reconstructAdaptiveRows(lines: List<TextLinePayload>): List<String> {
        if (lines.isEmpty()) return emptyList()

        val sorted = lines.sortedBy { it.boundingBoxTop }
        val groups = mutableListOf<MutableList<TextLinePayload>>()

        for (line in sorted) {
            val centerY = (line.boundingBoxTop + line.boundingBoxBottom) / 2f
            val height = line.boundingBoxBottom - line.boundingBoxTop
            val tolerance = (height * 0.40f).coerceAtLeast(12f)

            val matchingGroup = groups.find { group ->
                group.any { existing ->
                    val existingCenter = (existing.boundingBoxTop + existing.boundingBoxBottom) / 2f
                    abs(existingCenter - centerY) <= tolerance
                }
            }

            if (matchingGroup != null) matchingGroup.add(line)
            else groups.add(mutableListOf(line))
        }

        return groups.map { group ->
            group.sortedBy { it.boundingBoxLeft }
                .joinToString("  ") { it.text }
        }
    }

    private val pricePattern = Pattern.compile("(\\d{1,5}(?:\\.\\d{3})*,\\d{2})\\b")

    private val strictKeywordsRetail = listOf(
        "gesamt", "summe", "endbetrag", "zahlbetrag", "total", "zu zahlen", "zahlst."
    )
    private val strictKeywordsInvoice = listOf(
        "rechnungsbetrag", "zahlungsbetrag", "gesamtbetrag", "abschlagbetrag", "fällig"
    )
    private val taxRowKeywords = listOf(
        "mwst", "mehrwertsteuer", "umsatzsteuer", "netto", "brutto", " % "
    )

    private fun extractAmountFromRows(rows: List<String>, isInvoice: Boolean): Double {
        val priorityKeywords = if (isInvoice) strictKeywordsInvoice else strictKeywordsRetail

        // Priority 1: row that contains a total keyword (and is not a tax-breakdown row)
        for (row in rows) {
            val fixed = fixOcrCharacterGlitch(row.lowercase())
            if (priorityKeywords.any { fixed.contains(it) }) {
                if (taxRowKeywords.any { fixed.contains(it) } && !fixed.contains("gesamt")) continue
                val m = pricePattern.matcher(fixed)
                if (m.find()) m.group(1)?.let { return cleanGermanFloat(it, fixed) }
            }
        }

        // Priority 2: largest price on any non-tax row
        var max = 0.0
        for (row in rows) {
            val fixed = fixOcrCharacterGlitch(row.lowercase())
            if (taxRowKeywords.any { fixed.contains(it) } && priorityKeywords.none { fixed.contains(it) }) continue
            val m = pricePattern.matcher(fixed)
            while (m.find()) {
                val v = cleanGermanFloat(m.group(1) ?: "", fixed)
                if (abs(v) > abs(max)) max = v
            }
        }
        return max
    }

    /**
     * Corrects common OCR character substitutions inside numeric strings.
     * e.g. "14O50" → "14050", "4l50" → "4150", ",82" misread as ",B2"
     */
    internal fun fixOcrCharacterGlitch(input: String): String = input
        .replace(Regex("(\\d+)[oO](\\d{2})"), "$10$2")
        .replace(Regex("(\\d+)[iIlL](\\d{2})"), "$11$2")
        .replace(Regex(",[bB]2\\b"), ",82")
        .replace(Regex(",[sS]0\\b"), ",50")

    private fun cleanGermanFloat(value: String, rowContext: String): Double {
        return try {
            val base = value.replace(".", "").replace(",", ".").toDouble()
            val refundKeywords = listOf("guthaben", "gutschrift", "rückzahlung", "erstattung")
            if (refundKeywords.any { rowContext.contains(it) }) -abs(base) else abs(base)
        } catch (_: Exception) { 0.0 }
    }
}
