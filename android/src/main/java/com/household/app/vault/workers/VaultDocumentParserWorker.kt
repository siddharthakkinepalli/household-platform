package com.household.app.vault.workers

import android.content.Context
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.household.app.data.AppDatabase
import com.household.app.data.entities.AlertType
import com.household.app.data.entities.DocumentAlertEntity
import com.household.app.data.entities.DocumentEntity
import com.household.app.data.entities.DocumentType
import com.household.app.data.entities.PantryEntity
import com.household.app.data.service.VaultDocumentParser
import com.household.app.domain.models.vault.VaultCategory
import com.household.app.vault.parser.LocalReceiptScanner
import com.household.app.vault.parser.ReceiptTextParser
import com.household.app.vault.scan.MlKitOcrEngine
import com.household.app.vault.scan.PdfPageExtractor
import android.util.Log
import com.household.app.data.entities.VaultDocumentEntityRecord
import com.household.app.vault.classification.DocumentDocType
import com.household.app.vault.classification.ParserRegistry
import com.household.app.vault.extraction.EntityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class VaultDocumentParserWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val vaultId = inputData.getLong("vault_id", -1L)
        if (vaultId == -1L) return@withContext Result.failure()

        val db = AppDatabase.getInstance(applicationContext)
        val entry = db.vaultDao().getEntryById(vaultId) ?: return@withContext Result.failure()

        // Step 1: resolve OCR text
        var ocrText = entry.rawOcrContent
        var spatialScanned: LocalReceiptScanner.ScannedReceipt? = null

        if (ocrText.isBlank()) {
            val file = File(entry.imagePath)
            if (file.exists()) {
                val engine = MlKitOcrEngine()
                try {
                    when {
                        entry.mimeType == "application/pdf" -> {
                            ocrText = PdfPageExtractor.extractText(applicationContext, file, engine)
                        }
                        entry.mimeType.startsWith("image/") -> {
                            val payload = engine.recognize(applicationContext, file.toUri())
                            ocrText = payload.fullText
                            // Run spatial row reconstruction on the bounding-box-rich payload
                            spatialScanned = runCatching {
                                LocalReceiptScanner.processMlKitPayload(payload)
                            }.getOrNull()
                        }
                    }
                    if (ocrText.isNotBlank()) db.vaultDao().updateRawOcr(vaultId, ocrText)
                } catch (_: Exception) {
                    // extraction failed — proceed with title-only classification
                } finally {
                    engine.close()
                }
            }
        }

        // Use document title as classification hint when OCR is empty (e.g. scanned PDFs)
        val textForParsing = ocrText.ifBlank { entry.documentTitle ?: "" }

        // Resolve existing category early — used to gate receipt-only processing below
        val existingCategory = runCatching { VaultCategory.valueOf(entry.category) }
            .getOrDefault(VaultCategory.OTHER)

        // Step 1.5: structured receipt extraction — ONLY for receipt/unclassified documents.
        // Identity, insurance, contract, medical docs should never go through receipt parsing.
        val isReceiptLike = existingCategory == VaultCategory.RECEIPT ||
                            existingCategory == VaultCategory.OTHER
        if (isReceiptLike && textForParsing.isNotBlank()) {
            val receipt = spatialScanned?.let {
                ReceiptTextParser.ParsedReceipt(
                    merchant = it.merchant,
                    date = it.date,
                    totalAmount = it.totalAmount,
                    category = it.category,
                    isInvoice = it.isInvoice,
                    lineItems = ReceiptTextParser.extractLineItems(textForParsing)
                )
            } ?: ReceiptTextParser.parse(textForParsing)

            val merchant = receipt.merchant.takeIf { it != "UNBEKANNT" } ?: entry.merchantName
            val amount = receipt.totalAmount.takeIf { it > 0.0 } ?: entry.totalAmount

            val parsedDateEpoch: Long? = receipt.date?.let { dateStr ->
                runCatching {
                    val full = if (dateStr.length == 8) dateStr.substring(0, 6) + "20" + dateStr.substring(6)
                               else dateStr
                    LocalDate.parse(full, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                }.getOrNull()
            }

            if (merchant != null || amount != null || parsedDateEpoch != null) {
                db.vaultDao().updateReceiptMeta(
                    id        = vaultId,
                    merchant  = merchant ?: entry.merchantName ?: "UNBEKANNT",
                    amount    = amount ?: entry.totalAmount ?: 0.0,
                    dateEpoch = parsedDateEpoch ?: entry.dateEpoch
                )
            }

            val pantryEligible = receipt.category in setOf("Groceries", "Drugstore & Personal Care")
            if (!receipt.isInvoice && pantryEligible && receipt.lineItems.isNotEmpty()) {
                val pantryItems = receipt.lineItems.map { item ->
                    PantryEntity(
                        name        = item.name,
                        category    = receipt.category,
                        quantity    = item.quantity.toFloat(),
                        vaultId     = vaultId,
                        isConfirmed = false
                    )
                }
                db.pantryDao().insertItemsIgnore(pantryItems)
            }
        }

        Log.d("VaultParser", "OCR text length=${ocrText.length}, textForParsing preview=${textForParsing.take(200).replace('\n',' ')}")

        // Step 2: run Parser Registry — structured extraction with per-document-type parsers
        val (winningParser, extractionResult) = if (textForParsing.isNotBlank()) {
            runCatching { ParserRegistry.process(textForParsing) }.getOrNull()
                ?: (null to null)
        } else null to null

        Log.d("VaultParser", "ParserRegistry winner=${winningParser?.docType} entities=${extractionResult?.entities?.size} confidence=${extractionResult?.overallConfidence}")

        // Persist structured entities to vault_extracted_entities table
        if (extractionResult != null && extractionResult.entities.isNotEmpty()) {
            val records = extractionResult.entities.map { entity ->
                VaultDocumentEntityRecord(
                    vaultEntryId    = vaultId,
                    entityType      = entity.type.name,
                    rawValue        = entity.rawValue,
                    normalizedValue = entity.normalizedValue,
                    confidence      = entity.confidence,
                    pageIndex       = entity.pageIndex,
                    sourceContext   = entity.sourceContext,
                    parserId        = winningParser?.docType?.name ?: "UNKNOWN"
                )
            }
            db.documentEntityDao().deleteForDocument(vaultId)
            db.documentEntityDao().insertAll(records)
        }

        // If registry extracted an expiry date with high confidence, use it for DocumentEntity creation
        val registryExpiryDate: LocalDate? = extractionResult?.entities
            ?.filter { it.type == EntityType.EXPIRY_DATE && it.confidence >= 0.6f }
            ?.mapNotNull { runCatching { LocalDate.parse(it.normalizedValue) }.getOrNull() }
            ?.maxOrNull()

        // Step 2b: classify and extract metadata (existing VaultDocumentParser — keeps category/subfolder logic)
        val meta = VaultDocumentParser.parse(textForParsing, existingCategory)
        Log.d("VaultParser", "Parsed: category=${meta.category} subFolder=${meta.subFolder} expiry=${meta.expiryDate ?: registryExpiryDate} title=${meta.suggestedTitle}")

        // Step 3: persist updated category, subfolder and title.
        //
        // IMPORTANT: preserve the user's explicit subfolder selection.
        // When the user uploads a file directly into e.g. Siddharth → Identity → Passport,
        // the entry is saved with subFolder="passport". If OCR later fails (e.g. JPEG2000
        // embedded in a scanned PDF) the parser returns subFolder=UNFILED, which would move
        // the document out of the folder the user chose. Only let the parser override the
        // subfolder if it was never set (UNFILED) or is the catch-all (OTHER).
        val parserMayOverrideSubFolder = entry.subFolder == "unfiled" || entry.subFolder == "other"
        val effectiveSubFolder = if (parserMayOverrideSubFolder) meta.subFolder.id else entry.subFolder

        db.vaultDao().updateParsedMeta(
            id        = vaultId,
            category  = meta.category.name,
            subFolder = effectiveSubFolder,
            title     = meta.suggestedTitle ?: entry.documentTitle
        )

        // Step 4: if expiry date found, create a DocumentEntity + alert so the
        //         existing upcoming-alerts UI picks it up automatically.
        //         Registry expiry takes priority (higher fidelity, parser-specific logic).
        val effectiveExpiry = registryExpiryDate ?: meta.expiryDate
        effectiveExpiry?.let { expiry ->
            val expiryMs   = expiry.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val daysUntil  = ChronoUnit.DAYS.between(LocalDate.now(), expiry).toInt()
            val docTitle   = meta.suggestedTitle ?: entry.documentTitle ?: "Imported Document"

            val docType = when (meta.category) {
                VaultCategory.IDENTITY -> DocumentType.ID
                VaultCategory.MEDICAL  -> DocumentType.MEDICAL
                VaultCategory.PROPERTY -> DocumentType.PROPERTY
                else                   -> DocumentType.CONTRACT
            }
            val noticeDays = when (meta.category) {
                VaultCategory.IDENTITY              -> 90
                VaultCategory.INSURANCE,
                VaultCategory.PROPERTY              -> 60
                else                               -> 30
            }

            val docId = db.documentDao().insertDocument(
                DocumentEntity(
                    title          = docTitle,
                    type           = docType,
                    expiryDate     = expiryMs,
                    noticePeriodDays = noticeDays,
                    monthlyCost    = meta.monthlyCost,
                    localUri       = entry.imagePath
                )
            )

            db.documentAlertDao().insertAlerts(
                listOf(
                    DocumentAlertEntity(
                        documentId = docId,
                        alertType  = AlertType.EXPIRY_WARNING,
                        message    = "$docTitle expires on $expiry",
                        daysUntil  = daysUntil
                    )
                )
            )
        }

        Result.success()
    }
}
