package com.household.app.ui.fragments

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.household.app.R
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import com.household.app.data.TransactionCategorizer
import com.household.app.data.WalletDataLoader
import com.household.app.data.WalletUserDataStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

class ImportCsvFragment : Fragment() {

    private data class ImportPreview(
        val detectedBank: String,
        val parsed: List<WalletDataLoader.WalletTransaction>,
        val skippedRows: Int
    )

    private lateinit var textImportStatus: TextView
    private lateinit var textDetectedBank: TextView
    private lateinit var buttonImportCsv: Button
    private lateinit var previewContainer: LinearLayout

    private var preview: ImportPreview? = null

    private val pickCsvLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            lifecycleScope.launch {
                processCsv(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_import_csv, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textImportStatus = view.findViewById(R.id.text_import_status)
        textDetectedBank = view.findViewById(R.id.text_detected_bank)
        buttonImportCsv = view.findViewById(R.id.button_import_csv)
        previewContainer = view.findViewById(R.id.import_preview_container)

        view.findViewById<Button>(R.id.button_pick_csv).setOnClickListener {
            pickCsvLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel"))
        }

        buttonImportCsv.setOnClickListener {
            val current = preview ?: return@setOnClickListener
            lifecycleScope.launch {
                WalletUserDataStore.appendImportedTransactions(requireContext(), current.parsed)
                textImportStatus.text = "Imported ${current.parsed.size} transactions (${current.skippedRows} skipped)."
                Toast.makeText(requireContext(), "CSV imported to Wallet", Toast.LENGTH_SHORT).show()
                buttonImportCsv.isEnabled = false
            }
        }

        renderPreview(emptyList())
    }

    private suspend fun processCsv(uri: Uri) {
        val resolver = requireContext().contentResolver
        val fileName = queryFileName(resolver, uri)
        val csvText = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (csvText.isBlank()) {
            textImportStatus.text = "File is empty or unreadable."
            buttonImportCsv.isEnabled = false
            renderPreview(emptyList())
            return
        }

        val baseTransactions = WalletUserDataStore.loadMergedTransactions(
            requireContext(),
            WalletDataLoader(requireContext()).loadTransactions()
        )
        val startingId = (baseTransactions.maxOfOrNull { it.id } ?: 0) + 1
        val parsedPreview = parseCsv(csvText, fileName, startingId)

        preview = parsedPreview
        textDetectedBank.text = "Detected bank: ${parsedPreview.detectedBank}"
        textImportStatus.text = "Ready to import ${parsedPreview.parsed.size} transactions (${parsedPreview.skippedRows} skipped)."
        buttonImportCsv.isEnabled = parsedPreview.parsed.isNotEmpty()
        renderPreview(parsedPreview.parsed)
    }

    private fun renderPreview(transactions: List<WalletDataLoader.WalletTransaction>) {
        previewContainer.removeAllViews()
        if (transactions.isEmpty()) {
            previewContainer.addView(card("No rows parsed yet"))
            return
        }

        transactions.take(8).forEach { tx ->
            val sign = if (tx.amount < 0) "-" else "+"
            val line = "${tx.date} · ${tx.category}\n${tx.title}\n$sign€${"%.2f".format(tx.amount.absoluteValue)} · ${tx.bankName}"
            previewContainer.addView(card(line))
        }
        if (transactions.size > 8) {
            previewContainer.addView(card("+ ${transactions.size - 8} more rows"))
        }
    }

    private fun card(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setBackgroundResource(R.drawable.bg_wallet_card)
            setTextColor(resources.getColor(android.R.color.white, null))
            setPadding(20, 16, 20, 16)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 8
            layoutParams = params
        }
    }

    private fun parseCsv(csvText: String, fileName: String, startingId: Int): ImportPreview {
        val lines = csvText
            .lineSequence()
            .map { it.trim('\uFEFF', ' ', '\t') }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.size < 2) {
            return ImportPreview("Unknown", emptyList(), 0)
        }

        val delimiter = detectDelimiter(lines.first())
        val headers = splitCsvLine(lines.first(), delimiter)
        val headerMap = headers.mapIndexed { index, raw ->
            normalizeHeader(raw) to index
        }.toMap()

        val idxDate = pickIndex(headerMap, listOf("date", "bookingdate", "value date", "buchung", "valuta"))
        val idxTitle = pickIndex(headerMap, listOf("title", "description", "purpose", "merchant", "name", "verwendungszweck", "details"))
        val idxAmount = pickIndex(headerMap, listOf("amount", "betrag", "umsatz", "value", "sum"))
        val idxCategory = pickIndex(headerMap, listOf("category", "budgetcategory"))
        val idxBank = pickIndex(headerMap, listOf("bank", "account", "konto"))

        val detectedBank = detectBank(lines, fileName, headers)
        val categorizer = TransactionCategorizer()

        var nextId = startingId
        var skipped = 0
        val parsed = mutableListOf<WalletDataLoader.WalletTransaction>()

        lines.drop(1).forEach { line ->
            val cols = splitCsvLine(line, delimiter)
            val title = cols.getOrNull(idxTitle)?.trim().orEmpty()
            val amount = parseAmount(cols.getOrNull(idxAmount).orEmpty())
            val date = parseDate(cols.getOrNull(idxDate).orEmpty())

            if (title.isBlank() || amount == null || date == null) {
                skipped += 1
                return@forEach
            }

            val bank = cols.getOrNull(idxBank)?.trim().orEmpty().ifBlank { detectedBank }
            val sourceCategory = cols.getOrNull(idxCategory)?.trim().orEmpty().ifBlank { "Other" }
            val classifiedCategory = categorizer.classifyCategory(title, sourceCategory)

            parsed.add(
                WalletDataLoader.WalletTransaction(
                    id = nextId++,
                    title = title,
                    category = classifiedCategory,
                    amount = amount,
                    date = date,
                    paymentType = "Bank",
                    trip = null,
                    note = "Imported from CSV",
                    bankName = bank,
                    excluded = false
                )
            )
        }

        return ImportPreview(
            detectedBank = detectedBank,
            parsed = parsed,
            skippedRows = skipped
        )
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
            headerMap.entries.firstOrNull { it.key.contains(candidate) }?.let { return it.value }
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
            signal.contains("revolut") -> "Revolut"
            signal.contains("paypal") -> "PayPal"
            else -> "Unknown"
        }
    }

    private fun queryFileName(resolver: ContentResolver, uri: Uri): String {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return "import.csv"
    }
}
