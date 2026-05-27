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

        // Use document title as classification hint when OCR is empty (e.g. PDFs)
        val textForParsing = ocrText.ifBlank { entry.documentTitle ?: "" }

        // Step 1.5: structured receipt extraction.
        // For images: use spatialScanned (bounding-box row reconstruction — more accurate amounts).
        // For PDFs: fall through to string-based ReceiptTextParser.
        if (textForParsing.isNotBlank()) {
            val receipt = spatialScanned?.let {
                // Wrap ScannedReceipt as ParsedReceipt shape for the shared update logic below
                ReceiptTextParser.ParsedReceipt(
                    merchant = it.merchant,
                    date = it.date,
                    totalAmount = it.totalAmount,
                    category = it.category,
                    isInvoice = it.isInvoice,
                    lineItems = ReceiptTextParser.extractLineItems(textForParsing)
                )
            } ?: ReceiptTextParser.parse(textForParsing)

            // Only overwrite stored fields if parser produced confident results
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

            // Push grocery/drugstore line items to pantry as unconfirmed staged items
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

        // Step 2: classify and extract metadata
        val existingCategory = runCatching { VaultCategory.valueOf(entry.category) }
            .getOrDefault(VaultCategory.OTHER)
        val meta = VaultDocumentParser.parse(textForParsing, existingCategory)

        // Step 3: persist updated category, subfolder and title
        db.vaultDao().updateParsedMeta(
            id       = vaultId,
            category = meta.category.name,
            subFolder = meta.subFolder.id,
            title    = meta.suggestedTitle ?: entry.documentTitle
        )

        // Step 4: if expiry date found, create a DocumentEntity + alert so the
        //         existing upcoming-alerts UI picks it up automatically
        meta.expiryDate?.let { expiry ->
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
