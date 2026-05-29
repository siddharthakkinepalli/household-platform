package com.household.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-page processing state for a vault document.
 * One row per PDF page (or 1 row for single-image documents).
 */
@Entity(
    tableName = "vault_document_pages",
    indices = [
        Index("vaultEntryId", name = "idx_vault_document_pages_vaultEntryId"),
        Index("pageHash", name = "idx_vault_document_pages_pageHash")
    ]
)
data class DocumentPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vaultEntryId: Long,         // FK → vault_entries.id
    val pageIndex: Int,
    val pageHash: String,           // perceptual hash for dedup
    @ColumnInfo(defaultValue = "0")
    val widthPx: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val heightPx: Int = 0,
    @ColumnInfo(defaultValue = "UNKNOWN")
    val textSource: String = "UNKNOWN",  // PDFBOX | MLKIT | PADDLEOCR | TESSERACT
    @ColumnInfo(defaultValue = "PENDING")
    val processingState: String = "PENDING",
    @ColumnInfo(defaultValue = "1")
    val ocrEngineVersion: Int = 1,
    @ColumnInfo(defaultValue = "0")
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
    @ColumnInfo(defaultValue = "0")
    val processedAt: Long = System.currentTimeMillis()
)

/**
 * Structured entity extracted from a vault document by the Parser Registry.
 * Replaces ad-hoc expiry date extraction from VaultDocumentParserWorker.
 */
@Entity(
    tableName = "vault_extracted_entities",
    indices = [
        Index("vaultEntryId", name = "idx_vault_extracted_entities_vaultEntryId"),
        Index("entityType", name = "idx_vault_extracted_entities_entityType")
    ]
)
data class VaultDocumentEntityRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vaultEntryId: Long,
    val entityType: String,         // EntityType.name
    val rawValue: String,
    val normalizedValue: String,    // ISO date, clean ID, etc.
    val confidence: Float,
    @ColumnInfo(defaultValue = "0")
    val pageIndex: Int = 0,
    @ColumnInfo(defaultValue = "")
    val sourceContext: String = "",
    @ColumnInfo(defaultValue = "0")
    val isVerified: Boolean = false, // user-confirmed
    @ColumnInfo(defaultValue = "")
    val parserId: String = "",       // e.g. "IndianPassportParser"
    @ColumnInfo(defaultValue = "0")
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
