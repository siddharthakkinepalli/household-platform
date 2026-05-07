package com.household.app.domain.models.vault

data class VisionTextPayload(
    val blocks: List<TextBlockPayload>,
    val fullText: String
) {
    fun allLines(): List<TextLinePayload> = blocks.flatMap { it.lines }
}

data class TextBlockPayload(
    val lines: List<TextLinePayload>
)

data class TextLinePayload(
    val text: String,
    val confidence: Float,
    val boundingBoxTop: Float,
    val boundingBoxBottom: Float
)
