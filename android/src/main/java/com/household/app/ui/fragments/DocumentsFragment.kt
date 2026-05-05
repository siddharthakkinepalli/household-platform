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
import androidx.fragment.app.Fragment
import com.household.app.R
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

        val parseResult = when {
            mimeType.contains("pdf") || fileName.endsWith(".pdf", ignoreCase = true) -> {
                NewPdfBridge.extractDates(resolver, uri, fileName)
            }
            mimeType.startsWith("image/") -> {
                ParsedDates(
                    dates = emptyList(),
                    note = "Image selected. OCR hook is ready; integrate on-device OCR model next."
                )
            }
            else -> {
                ParsedDates(
                    dates = emptyList(),
                    note = "Unsupported format for parser. Upload PDF or image."
                )
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

private object NewPdfBridge {

    private val dateRegex = Regex("(20\\d{2})[-/.](0?[1-9]|1[0-2])[-/.](0?[1-9]|[12]\\d|3[01])")

    fun extractDates(resolver: ContentResolver, uri: Uri, fileName: String): ParsedDates {
        val reflectedDates = extractWithNewPdfLibrary(uri)
        if (reflectedDates.isNotEmpty()) {
            return ParsedDates(
                dates = reflectedDates,
                note = "Parsed with NewPDF library"
            )
        }

        val datesFromName = dateRegex.findAll(fileName)
            .mapNotNull { match ->
                val y = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val m = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val d = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                runCatching { LocalDate.of(y, m, d) }.getOrNull()
            }
            .toList()

        val note = if (datesFromName.isEmpty()) {
            "PDF uploaded. NewPDF bridge active; no explicit date string found yet."
        } else {
            "PDF uploaded. Date candidates extracted from filename."
        }

        resolver.openInputStream(uri)?.close()
        return ParsedDates(dates = datesFromName, note = note)
    }

    private fun extractWithNewPdfLibrary(uri: Uri): List<LocalDate> {
        return try {
            val parserClass = Class.forName("com.newpdf.Parser")
            val parser = parserClass.getDeclaredConstructor().newInstance()
            val method = parserClass.getMethod("extractDateStrings", String::class.java)
            val output = method.invoke(parser, uri.toString()) as? List<*> ?: return emptyList()
            output.mapNotNull { item ->
                val text = item?.toString().orEmpty()
                runCatching { LocalDate.parse(text) }.getOrNull()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
