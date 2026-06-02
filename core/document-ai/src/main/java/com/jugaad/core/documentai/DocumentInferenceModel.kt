package com.jugaad.core.documentai

import com.jugaad.core.documentai.model.DocumentType
import com.jugaad.core.documentai.model.ExtractionResult
import com.jugaad.core.llmruntime.LlamaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentInferenceModel @Inject constructor(
    private val engine: LlamaEngine
) {
    // Call once before extract() — safe to call multiple times (no-op if already loaded)
    suspend fun init(modelFile: File): Boolean {
        if (engine.isLoaded) return true
        if (!modelFile.exists()) return false
        return engine.loadModel(modelFile.absolutePath)
    }

    suspend fun extract(
        ocrText: String,
        documentType: DocumentType = DocumentType.UNKNOWN
    ): ExtractionResult = withContext(Dispatchers.Default) {
        try {
            if (!engine.isLoaded) return@withContext ExtractionResult.EMPTY

            val prompt = DocumentPromptBuilder.build(ocrText, documentType)
            val rawOutput = engine.generate(prompt)
            
            val (fields, parseConfidence) = LlmResponseParser.parse(rawOutput)
            
            if (parseConfidence == 0f) {
                return@withContext ExtractionResult(
                    documentType = documentType,
                    fields = emptyMap(),
                    confidence = 0f,
                    rawLlmOutput = rawOutput,
                    extractedByLlm = true
                )
            }

            val expectedCount = getExpectedFieldCount(documentType)
            val confidence = (fields.size.toFloat() / expectedCount).coerceAtMost(1.0f)

            ExtractionResult(
                documentType = documentType,
                fields = fields,
                confidence = confidence,
                rawLlmOutput = rawOutput,
                extractedByLlm = true
            )
        } catch (e: Exception) {
            ExtractionResult.EMPTY
        }
    }

    private fun getExpectedFieldCount(type: DocumentType): Int = when (type) {
        DocumentType.RECEIPT -> 6
        DocumentType.INVOICE -> 8
        DocumentType.PAYSLIP -> 7
        DocumentType.BANK_STATEMENT -> 4
        DocumentType.PASSPORT -> 8
        DocumentType.DRIVING_LICENCE -> 5
        DocumentType.AUFENTHALTSTITEL -> 8
        DocumentType.UTILITY_BILL -> 6
        DocumentType.RENTAL_CONTRACT -> 7
        DocumentType.TAX_DOCUMENT -> 2
        DocumentType.UNKNOWN -> 2
    }
}
