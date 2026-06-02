package com.jugaad.core.documentai

import com.jugaad.core.documentai.model.DocumentType
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPromptBuilderTest {

    @Test
    fun eachDocumentTypeProducesNonEmptyPrompt() {
        DocumentType.values().forEach { type ->
            val result = DocumentPromptBuilder.build("sample ocr text", type)
            assertTrue("Prompt for $type should not be blank", result.isNotBlank())
        }
    }

    @Test
    fun promptContainsOcrText() {
        val ocrText = "REWE 15.05.2024 Total: €42.50"
        val result = DocumentPromptBuilder.build(ocrText, DocumentType.RECEIPT)
        assertTrue("Prompt should contain original OCR text", result.contains(ocrText))
    }

    @Test
    fun promptContainsSystemInstruction() {
        val result = DocumentPromptBuilder.build("text", DocumentType.UNKNOWN)
        assertTrue("Prompt should contain JSON instruction", result.contains("Output valid JSON only"))
    }

    @Test
    fun promptContainsNullOmitInstruction() {
        val result = DocumentPromptBuilder.build("text", DocumentType.UNKNOWN)
        assertTrue("Prompt should contain omit instruction", result.contains("omit it from the JSON entirely"))
    }

    @Test
    fun longOcrTextIsTruncated() {
        val longText = "A".repeat(5000)
        val result = DocumentPromptBuilder.build(longText, DocumentType.RECEIPT)
        assertTrue("Long prompt should be truncated", result.length < 6000)
    }

    @Test
    fun schemaContainsExpectedReceiptFields() {
        val result = DocumentPromptBuilder.build("text", DocumentType.RECEIPT)
        val expected = listOf("merchant", "date", "total_amount", "currency", "line_items")
        expected.forEach { field ->
            assertTrue("Receipt schema should contain $field", result.contains(field))
        }
    }

    @Test
    fun schemaContainsExpectedPassportFields() {
        val result = DocumentPromptBuilder.build("text", DocumentType.PASSPORT)
        val expected = listOf("surname", "given_names", "expiry_date", "document_number", "mrz_line1", "mrz_line2")
        expected.forEach { field ->
            assertTrue("Passport schema should contain $field", result.contains(field))
        }
    }

    @Test
    fun ocrTextIsWrappedInXmlTags() {
        val result = DocumentPromptBuilder.build("text", DocumentType.UNKNOWN)
        assertTrue("OCR text should be wrapped in <document_text>", result.contains("<document_text>"))
        assertTrue("OCR text should be wrapped in </document_text>", result.contains("</document_text>"))
    }
}
