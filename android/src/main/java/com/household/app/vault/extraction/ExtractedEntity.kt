package com.household.app.vault.extraction

import java.time.LocalDate

data class ExtractedEntity(
    val type: EntityType,
    val rawValue: String,
    val normalizedValue: String,
    val confidence: Float,          // 0.0 – 1.0
    val pageIndex: Int = 0,
    val sourceContext: String = ""  // surrounding OCR text for debugging
)

data class ExtractionResult(
    val entities: List<ExtractedEntity>,
    val overallConfidence: Float,
    val partial: Boolean = false    // true when some expected fields were not found
) {
    fun firstOfType(type: EntityType): ExtractedEntity? =
        entities.firstOrNull { it.type == type }

    fun allOfType(type: EntityType): List<ExtractedEntity> =
        entities.filter { it.type == type }

    fun expiryDate(): LocalDate? = firstOfType(EntityType.EXPIRY_DATE)?.let { entity ->
        runCatching { LocalDate.parse(entity.normalizedValue) }.getOrNull()
    }
}
