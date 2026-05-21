package com.household.app.ui.fragments

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.household.app.R
import com.household.app.vault.scan.DocumentScanner
import com.household.app.vault.scan.OcrEngineProvider
import com.household.app.vault.scan.PdfTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

class DocumentsFragment : Fragment() {

    private data class ManagedDocument(
        val name: String,
        val uri: Uri,
        val mimeType: String,
        val extractedDates: List<LocalDate>,
        val parserNote: String
    )

    private lateinit var textParseStatus: TextView
    private lateinit var documentsContainer: LinearLayout
    private lateinit var renewalsContainer: LinearLayout

    private val managedDocuments = mutableListOf<ManagedDocument>()

    private val pickDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            processDocument(uri)
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_documents, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textParseStatus = view.findViewById(R.id.text_parse_status)
        documentsContainer = view.findViewById(R.id.documents_container)
        renewalsContainer = view.findViewById(R.id.renewals_container)

        view.findViewById<View>(R.id.button_pick_document).setOnClickListener {
            pickDocumentLauncher.launch(arrayOf("application/pdf", "image/*"))
        }

        renderDocuments()
        renderRenewals()
    }

    private fun processDocument(uri: Uri) {
        val resolver = requireContext().contentResolver
        val mimeType = resolver.getType(uri).orEmpty()
        val fileName = queryFileName(resolver, uri)

        textParseStatus.text = "Processing…"

        viewLifecycleOwner.lifecycleScope.launch {
            val parseResult = withContext(Dispatchers.IO) {
                when {
                    mimeType.contains("pdf") || fileName.endsWith(".pdf", ignoreCase = true) -> {
                        val result = PdfTextExtractor.extract(requireContext(), resolver, uri)
                        ParsedDates(dates = result.dates, note = result.note)
                    }
                    mimeType.startsWith("image/") -> extractDatesFromImage(resolver, uri)
                    else -> ParsedDates(emptyList(), "Unsupported format. Upload PDF or image.")
                }
            }

            managedDocuments.add(
                ManagedDocument(
                    name = fileName,
                    uri = uri,
                    mimeType = mimeType.ifBlank { "unknown" },
                    extractedDates = parseResult.dates,
                    parserNote = parseResult.note
                )
            )
            textParseStatus.text = parseResult.note
            renderDocuments()
            renderRenewals()
        }
    }

    private suspend fun extractDatesFromImage(resolver: ContentResolver, uri: Uri): ParsedDates {
        return runCatching {
            // Copy to temp file so DocumentScanner can read it
            val tmpFile = File(requireContext().cacheDir, "doc_scan_tmp.jpg")
            resolver.openInputStream(uri)?.use { FileOutputStream(tmpFile).use { out -> it.copyTo(out) } }

            val enhanced = DocumentScanner.process(tmpFile)
            val engine = OcrEngineProvider.engine
            val payload = engine.recognize(requireContext(), enhanced.toUri())
            val text = payload.fullText
            val dates = parseDatesFromText(text)
            ParsedDates(
                dates = dates,
                note = "${engine.id}: ${text.length} chars, ${dates.size} date(s) found"
            )
        }.getOrElse { e ->
            ParsedDates(emptyList(), "Image OCR failed: ${e.message}")
        }
    }

    private fun parseDatesFromText(text: String): List<LocalDate> {
        val found = mutableSetOf<LocalDate>()
        val isoPattern = Regex("""(20\d{2})[-/.](0?[1-9]|1[0-2])[-/.](0?[1-9]|[12]\d|3[01])""")
        val euPattern  = Regex("""\b(0?[1-9]|[12]\d|3[01])[./-](0?[1-9]|1[0-2])[./-](20\d{2})\b""")
        isoPattern.findAll(text).forEach { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
                .getOrNull()?.let { found.add(it) }
        }
        euPattern.findAll(text).forEach { m ->
            runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }
                .getOrNull()?.let { found.add(it) }
        }
        return found.sortedDescending()
    }

    private fun renderDocuments() {
        documentsContainer.removeAllViews()
        if (managedDocuments.isEmpty()) {
            documentsContainer.addView(card("No documents uploaded yet"))
            return
        }

        managedDocuments.forEach { doc ->
            val dates = if (doc.extractedDates.isEmpty()) "No dates extracted" else doc.extractedDates.joinToString(", ")
            val line = "${doc.name}\n${doc.mimeType}\n$dates"
            documentsContainer.addView(card(line))
        }
    }

    private fun renderRenewals() {
        renewalsContainer.removeAllViews()
        val upcoming = managedDocuments
            .flatMap { doc -> doc.extractedDates.map { date -> doc.name to date } }
            .sortedBy { it.second }

        if (upcoming.isEmpty()) {
            renewalsContainer.addView(card("No renewal dates found yet"))
            return
        }

        upcoming.forEach { (name, date) ->
            val days = ChronoUnit.DAYS.between(LocalDate.now(), date)
            val status = if (days >= 0) "$days days left" else "overdue by ${days.absoluteValue} days"
            renewalsContainer.addView(card("$name\nRenewal: $date ($status)"))
        }
    }

    private fun card(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setBackgroundResource(R.drawable.bg_card)
            setTextColor(resources.getColor(R.color.text_primary, null))
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
        return "document"
    }
}

private data class ParsedDates(
    val dates: List<LocalDate>,
    val note: String
)
