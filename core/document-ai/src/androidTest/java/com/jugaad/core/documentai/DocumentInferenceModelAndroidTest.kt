package com.jugaad.core.documentai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jugaad.core.documentai.model.DocumentType
import com.jugaad.core.documentai.model.ExtractionResult
import com.jugaad.core.llmruntime.LlamaEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentInferenceModelAndroidTest {

    @Test
    fun jniLibraryLoadsOnDevice() {
        try {
            System.loadLibrary("llama_jni")
        } catch (e: UnsatisfiedLinkError) {
            fail("Native library llama_jni failed to load on device: ${e.message}")
        }
    }

    @Test
    fun extractReturnsEmptyWhenEngineNotLoaded() {
        val mockEngine = mockk<LlamaEngine>()
        every { mockEngine.isLoaded } returns false
        val model = DocumentInferenceModel(mockEngine)
        
        val result = runBlocking { 
            model.extract("some OCR text", DocumentType.RECEIPT) 
        }
        
        assertEquals(ExtractionResult.EMPTY, result)
        assertEquals(false, result.extractedByLlm)
        assertTrue(result.fields.isEmpty())
        coVerify(exactly = 0) { mockEngine.generate(any(), any()) }
    }

    @Test
    fun extractReturnsFieldsWhenEngineReturnsValidJson() {
        val mockEngine = mockk<LlamaEngine>()
        every { mockEngine.isLoaded } returns true
        coEvery { mockEngine.generate(any(), any()) } returns
            """{"merchant":"Lidl","date":"2024-06-01","total_amount":"18.75","currency":"EUR"}"""
        
        val model = DocumentInferenceModel(mockEngine)
        
        val result = runBlocking { 
            model.extract("LIDL SAGT DANKE", DocumentType.RECEIPT) 
        }
        
        assertEquals(true, result.extractedByLlm)
        assertEquals("Lidl", result.fields["merchant"])
        assertEquals("18.75", result.fields["total_amount"])
        assertTrue(result.confidence > 0f)
    }

    @Test
    fun extractNeverPropagatesEngineException() {
        val mockEngine = mockk<LlamaEngine>()
        every { mockEngine.isLoaded } returns true
        coEvery { mockEngine.generate(any(), any()) } throws RuntimeException("simulated NDK crash")
        
        val model = DocumentInferenceModel(mockEngine)
        
        val result = runBlocking { 
            model.extract("text", DocumentType.RECEIPT) 
        }
        
        assertEquals(ExtractionResult.EMPTY, result)
    }

    @Test
    fun extractReturnsEmptyConfidenceOnGarbageOutput() {
        val mockEngine = mockk<LlamaEngine>()
        every { mockEngine.isLoaded } returns true
        coEvery { mockEngine.generate(any(), any()) } returns "I cannot parse this document."
        
        val model = DocumentInferenceModel(mockEngine)
        
        val result = runBlocking { 
            model.extract("text", DocumentType.RECEIPT) 
        }
        
        assertTrue(result.fields.isEmpty())
        assertEquals(0f, result.confidence)
        assertEquals(true, result.extractedByLlm)
    }
}
