package com.household.app.vault.scan

import android.content.ContentResolver
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.time.LocalDate

data class PdfExtractResult(
    val text: String,
    val dates: List<LocalDate>,
    val note: String
)

object PdfTextExtractor {

    private val datePatterns = listOf(
        // ISO: 2024-03-15
        Regex("""(20\d{2})[-/.](0?[1-9]|1[0-2])[-/.](0?[1-9]|[12]\d|3[01])"""),
        // EU: 15.03.2024 or 15/03/2024
        Regex("""\b(0?[1-9]|[12]\d|3[01])[./-](0?[1-9]|1[0-2])[./-](20\d{2})\b""")
    )

    fun extract(@Suppress("UNUSED_PARAMETER") context: android.content.Context, resolver: ContentResolver, uri: Uri): PdfExtractResult {
        return runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { doc ->
                    val stripper = PDFTextStripper()
                    val text = stripper.getText(doc).orEmpty()
                    val dates = parseDates(text)
                    PdfExtractResult(
                        text = text,
                        dates = dates,
                        note = "PdfBox extracted ${text.length} chars, ${dates.size} date(s) found"
                    )
                }
            } ?: PdfExtractResult("", emptyList(), "Could not open PDF stream")
        }.getOrElse { e ->
            PdfExtractResult("", emptyList(), "PDF extraction failed: ${e.message}")
        }
    }

    private fun parseDates(text: String): List<LocalDate> {
        val found = mutableSetOf<LocalDate>()
        for (pattern in datePatterns) {
            pattern.findAll(text).forEach { match ->
                val g = match.groupValues
                runCatching {
                    // First pattern: YYYY-MM-DD; second: DD-MM-YYYY
                    if (g[1].length == 4) {
                        LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
                    } else {
                        LocalDate.of(g[3].toInt(), g[2].toInt(), g[1].toInt())
                    }
                }.getOrNull()?.let { found.add(it) }
            }
        }
        return found.sortedDescending()
    }
}
