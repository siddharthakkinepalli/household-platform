package com.household.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-page processing state for a vault document.
 * One row per PDF page (or 1 row for single-image documents).
 */
@Entity(tableName = "vault_document_pages")
data class DocumentPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vaultEntryId: Long,         // FK → vault_entries.id
    val pageIndex: Int,
    val pageHash: String,           // perceptual hash for dedup
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val textSource: String = "UNKNOWN",  // PDFBOX | MLKIT | PADDLEOCR | TESSERACT
    val processingState: String = "PENDING",
    val ocrEngineVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * OCR result cache keyed by (pageHash, engineVersion).
 * Shared across all documents — same physical page in different files reuses OCR.
 */
@Entity(tableName = "ocr_cache", primaryKeys = ["pageHash", "engineVersion"])
data class OcrCacheEntity(
    val pageHash: String,
    val engineVersion: Int,
    val ocrText: String,
    val confidence: Float,
    val processedAt: Long = System.currentTimeMillis()
)

/**
 * Structured entity extracted from a vault document by the Parser Registry.
 * Replaces ad-hoc expiry date extraction from VaultDocumentParserWorker.
 */
@Entity(tableName = "vault_extracted_entities")
data class VaultDocumentEntityRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vaultEntryId: Long,
    val entityType: String,         // EntityType.name
    val rawValue: String,
    val normalizedValue: String,    // ISO date, clean ID, etc.
    val confidence: Float,
    val pageIndex: Int = 0,
    val sourceContext: String = "",
    val isVerified: Boolean = false, // user-confirmed
    val parserId: String = "",       // e.g. "IndianPassportParser"
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * FTS4 mirror of vault_extracted_entities for full-text search over extracted values.
 * Populated by VaultDocumentParserWorker after entity extraction.
 */
@androidx.room.Fts4(contentEntity = VaultDocumentEntityRecord::class)
@Entity(tableName = "vault_entities_fts")
data class VaultEntityFts(
    val rawValue: String,
    val normalizedValue: String
)
