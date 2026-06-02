package com.jugaad.core.documentai.model

data class ExtractionResult(
    val documentType: DocumentType,
    val fields: Map<String, String>,
    val confidence: Float,
    val rawLlmOutput: String,
    val extractedByLlm: Boolean
) {
    companion object {
        val EMPTY = ExtractionResult(
            documentType = DocumentType.UNKNOWN,
            fields = emptyMap(),
            confidence = 0f,
            rawLlmOutput = "",
            extractedByLlm = false
        )
    }
}
