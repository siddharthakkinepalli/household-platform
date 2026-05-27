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
import androidx.lifecycle.lifecycleScope
import com.household.app.R
import com.household.app.data.WalletDataLoader
import com.household.app.data.WalletUserDataStore
import com.household.app.data.config.CsvParserService
import com.household.app.data.config.ImportParseResult
import com.household.app.data.config.ParsedTransactionCandidate
import kotlinx.coroutines.launch
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
    private val csvParserService = CsvParserService()

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
        val fileHash = csvText.hashCode().toString()

        when (val result = csvParserService.parse(csvText, fileName, fileHash, startingId)) {
            is ImportParseResult.Success -> {
                val summary = result.summary
                val walletTransactions = summary.transactions.map { it.toWalletTransaction() }
                preview = ImportPreview(
                    detectedBank = summary.detectedBank,
                    parsed = walletTransactions,
                    skippedRows = summary.skippedCount
                )
                textDetectedBank.text = "Detected bank: ${summary.detectedBank}"
                textImportStatus.text = "Ready to import ${walletTransactions.size} transactions (${summary.skippedCount} skipped, ${summary.warningCount} uncategorized)."
                buttonImportCsv.isEnabled = walletTransactions.isNotEmpty()
                renderPreview(walletTransactions)
            }
            is ImportParseResult.Error -> {
                textImportStatus.text = "Parse error: ${result.error}"
                buttonImportCsv.isEnabled = false
                renderPreview(emptyList())
            }
        }
    }

    private fun ParsedTransactionCandidate.toWalletTransaction() = WalletDataLoader.WalletTransaction(
        id = id,
        title = title,
        category = category,
        amount = amount,
        date = date,
        paymentType = "Bank",
        trip = null,
        note = note,
        bankName = bankName,
        excluded = category == "Excluded"
    )

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
