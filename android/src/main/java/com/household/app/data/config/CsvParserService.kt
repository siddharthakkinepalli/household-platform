package com.household.app.data.config

import com.household.app.data.TransactionCategorizer
import com.household.app.domain.utils.MerchantNameCleaner
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedTransactionCandidate(
    val id: Int,
    val title: String,
    val category: String,
    val amount: Double,
    val date: LocalDate,
    val bankName: String,
    val note: String = "Imported from CSV"
)

data class ImportSummary(
    val fileName: String,
    val fileHash: String,
    val detectedBank: String,
    val delimiter: Char,
    val rowCount: Int,
    val parsedCount: Int,
    val skippedCount: Int,
    val warningCount: Int,
    val transactions: List<ParsedTransactionCandidate>
)

sealed class ImportErrorType {
    object EmptyFile : ImportErrorType()
    object MissingColumns : ImportErrorType()
    data class ParseFailure(val reason: String) : ImportErrorType()
}

sealed class ImportParseResult {
    data class Success(val summary: ImportSummary) : ImportParseResult()
    data class Error(val error: ImportErrorType) : ImportParseResult()
}

class CsvParserService(
    private val categorizer: TransactionCategorizer = TransactionCategorizer()
) {
    fun parse(csvText: String, fileName: String, fileHash: String, startingId: Int): ImportParseResult {
        val lines = csvText
            .lineSequence()
            .map { it.trim('\uFEFF', ' ', '\t') }
            .filter { it.isNotBlank() }
            .toList()

        if (lines.size < 2) {
            return ImportParseResult.Error(ImportErrorType.EmptyFile)
        }

        return try {
            val delimiter = detectDelimiter(lines.first())
            val headers = splitCsvLine(lines.first(), delimiter)
            val headerMap = headers.mapIndexed { index, raw -> normalizeHeader(raw) to index }.toMap()

            val idxDate = pickIndex(headerMap, listOf("date", "datum", "buchungsdatum", "booking", "valuta", "wertstellung"))
            val idxTitle = pickIndex(headerMap, listOf(
                "title", "description", "purpose", "merchant", "verwendungszweck",
                "auftraggeber", "beguenstigter", "empfänger", "name", "details", "partner", "text", "booking"
            ))
            val idxAmount = pickIndex(headerMap, listOf("amount", "betrag eur", "betrag", "umsatz", "value", "sum", "wert"))
            val idxCategory = pickIndex(headerMap, listOf("category", "budgetcategory", "kategorie"))
            val idxBank = pickIndex(headerMap, listOf("bank", "account", "konto", "iban", "glaeubiger"))

            if (idxDate < 0 || idxTitle < 0 || idxAmount < 0) {
                return ImportParseResult.Error(ImportErrorType.MissingColumns)
            }

            val detectedBank = detectBank(lines, fileName, headers)
            var nextId = startingId
            var skipped = 0
            var warningCount = 0
            val parsed = mutableListOf<ParsedTransactionCandidate>()

            lines.drop(1).forEach { line ->
                val cols = splitCsvLine(line, delimiter)
                val rawTitle = cols.getOrNull(idxTitle)?.trim().orEmpty()
                val title = MerchantNameCleaner.clean(rawTitle)
                val amount = parseAmount(cols.getOrNull(idxAmount).orEmpty())
                val date = parseDate(cols.getOrNull(idxDate).orEmpty())

                if (title.isBlank() || amount == null || date == null) {
                    skipped += 1
                    return@forEach
                }

                val bank = cols.getOrNull(idxBank)?.trim().orEmpty().ifBlank { detectedBank }
                val sourceCategory = cols.getOrNull(idxCategory)?.trim().orEmpty().ifBlank { "Other" }

                val rawDescription = cols.getOrNull(idxTitle)?.trim().orEmpty()
                val classifiedCategory = when {
                    categorizer.shouldExclude(title) || categorizer.shouldExclude(rawDescription) -> "Excluded"
                    categorizer.isIncome(rawDescription) || categorizer.isIncome(title) -> "Income"
                    amount > 0 -> {
                        val raw = categorizer.classifyCategory(title, sourceCategory)
                        if (raw == "Transfers") "Excluded" else "Income"
                    }
                    else -> {
                        val raw = categorizer.classifyCategory(title, sourceCategory)
                        if (raw == "Transfers") "Excluded" else raw
                    }
                }
                if (classifiedCategory == "Uncategorized") {
                    warningCount += 1
                }

                parsed.add(
                    ParsedTransactionCandidate(
                        id = nextId++,
                        title = title,
                        category = classifiedCategory,
                        amount = amount,
                        date = date,
                        bankName = bank
                    )
                )
            }

            ImportParseResult.Success(
                ImportSummary(
                    fileName = fileName,
                    fileHash = fileHash,
                    detectedBank = detectedBank,
                    delimiter = delimiter,
                    rowCount = lines.size - 1,
                    parsedCount = parsed.size,
                    skippedCount = skipped,
                    warningCount = warningCount,
                    transactions = parsed
                )
            )
        } catch (error: Exception) {
            ImportParseResult.Error(ImportErrorType.ParseFailure(error.message ?: "Unknown parser failure"))
        }
    }

    private fun detectDelimiter(headerLine: String): Char {
        val comma = headerLine.count { it == ',' }
        val semicolon = headerLine.count { it == ';' }
        val tab = headerLine.count { it == '\t' }
        return when {
            semicolon >= comma && semicolon >= tab -> ';'
            tab >= comma -> '\t'
            else -> ','
        }
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        line.forEach { ch ->
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    out.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        out.add(current.toString().trim())
        return out
    }

    private fun normalizeHeader(raw: String): String {
        return raw
            .lowercase(Locale.getDefault())
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun pickIndex(headerMap: Map<String, Int>, candidates: List<String>): Int {
        candidates.forEach { candidate ->
            headerMap.entries.firstOrNull { (key, _) ->
                key.contains(candidate) || 
                candidate.split(" ").any { word -> key.contains(word) }
            }?.let { return it.value }
        }
        return -1
    }

    private fun parseAmount(raw: String): Double? {
        if (raw.isBlank()) return null
        val cleaned = raw
            .replace("€", "")
            .replace("EUR", "", ignoreCase = true)
            .replace(" ", "")
            .trim()

        val normalized = when {
            cleaned.contains(",") && cleaned.contains(".") -> cleaned.replace(".", "").replace(",", ".")
            cleaned.contains(",") -> cleaned.replace(",", ".")
            else -> cleaned
        }
        return normalized.toDoubleOrNull()
    }

    private fun parseDate(raw: String): LocalDate? {
        val value = raw.trim()
        if (value.isBlank()) return null

        val patterns = listOf(
            DateTimeFormatter.ISO_DATE,
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
        )

        patterns.forEach { formatter ->
            val parsed = runCatching { LocalDate.parse(value, formatter) }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun detectBank(lines: List<String>, fileName: String, headers: List<String>): String {
        val signal = (headers.joinToString(" ") + " " + fileName + " " + lines.take(12).joinToString(" "))
            .lowercase(Locale.getDefault())

        return when {
            signal.contains("n26") -> "N26"
            signal.contains("commerz") -> "Commerzbank"
            signal.contains("deutsche bank") -> "Deutsche Bank"
            signal.contains("dkb") -> "DKB"
            signal.contains("sparkasse") -> "Sparkasse"
            signal.contains("ing-diba") || signal.contains("ing diba") || signal.contains("ing.de")
                    || (signal.contains("buchungsdatum") && signal.contains("auftraggeber")) -> "ING"
            signal.contains("revolut") -> "Revolut"
            signal.contains("paypal") -> "PayPal"
            else -> "Unknown"
        }
    }
}