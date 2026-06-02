package com.jugaad.core.documentai

import com.jugaad.core.documentai.model.DocumentType
import com.jugaad.core.documentai.model.ExtractionResult
import com.jugaad.core.llmruntime.LlamaEngine
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentInferenceModelTest {

    private val engine = mockk<LlamaEngine>()
    private val model = DocumentInferenceModel(engine)

    @Test
    fun returnsEmptyWhenEngineNotLoaded() = runTest {
        every { engine.isLoaded } returns false
        
        val result = model.extract("ocr text", DocumentType.RECEIPT)
        
        assertEquals(ExtractionResult.EMPTY, result)
    }

    @Test
    fun extractReturnsSuccessfulResult() = runTest {
        every { engine.isLoaded } returns true
        coEvery { engine.generate(any(), any()) } returns """{"merchant":"REWE"}"""
        
        val result = model.extract("ocr text", DocumentType.RECEIPT)
        
        assertTrue(result.extractedByLlm)
        assertEquals("REWE", result.fields["merchant"])
        assertEquals(DocumentType.RECEIPT, result.documentType)
    }

    @Test
    fun extractHandlesParserFailure() = runTest {
        every { engine.isLoaded } returns true
        coEvery { engine.generate(any(), any()) } returns "invalid json"
        
        val result = model.extract("ocr text", DocumentType.RECEIPT)
        
        assertTrue(result.extractedByLlm)
        assertTrue(result.fields.isEmpty())
        assertEquals(0f, result.confidence)
    }

    @Test
    fun computesConfidenceCorrectly() = runTest {
        every { engine.isLoaded } returns true
        // Receipt has 6 expected fields
        coEvery { engine.generate(any(), any()) } returns """{"merchant":"REWE", "date":"today", "total_amount":"1.0"}"""
        
        val result = model.extract("ocr text", DocumentType.RECEIPT)
        
        // 3 fields out of 6 -> 0.5 confidence
        assertEquals(0.5f, result.confidence, 0.01f)
    }

    @Test
    fun extractHandlesEngineException() = runTest {
        every { engine.isLoaded } returns true
        coEvery { engine.generate(any(), any()) } throws RuntimeException("Engine crash")
        
        val result = model.extract("ocr text", DocumentType.RECEIPT)
        
        assertEquals(ExtractionResult.EMPTY, result)
    }
}
