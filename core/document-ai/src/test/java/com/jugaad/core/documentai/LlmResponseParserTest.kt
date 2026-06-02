package com.jugaad.core.documentai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmResponseParserTest {

    @Test
    fun parsesCleanJson() {
        val input = """{"merchant":"REWE","date":"2024-05-15","total_amount":"42.50"}"""
        val (fields, confidence) = LlmResponseParser.parse(input)
        assertEquals("REWE", fields["merchant"])
        assertEquals("2024-05-15", fields["date"])
        assertEquals(1.0f, confidence)
    }

    @Test
    fun extractsJsonFromSurroundingText() {
        val input = "Here is the JSON:\n{\"merchant\":\"ALDI\"}\nDone."
        val (fields, confidence) = LlmResponseParser.parse(input)
        assertEquals("ALDI", fields["merchant"])
        assertEquals(1.0f, confidence)
    }

    @Test
    fun returnsEmptyOnInvalidJson() {
        val input = "This is not JSON at all."
        val (fields, confidence) = LlmResponseParser.parse(input)
        assertTrue(fields.isEmpty())
        assertEquals(0f, confidence)
    }

    @Test
    fun returnsEmptyOnEmptyString() {
        val (fields, confidence) = LlmResponseParser.parse("")
        assertTrue(fields.isEmpty())
        assertEquals(0f, confidence)
    }

    @Test
    fun skipsNullValues() {
        val input = """{"merchant":"REWE","date":null,"total_amount":"10.00"}"""
        val (fields, _) = LlmResponseParser.parse(input)
        assertFalse(fields.containsKey("date"))
        assertEquals("REWE", fields["merchant"])
    }

    @Test
    fun flattensNestedObjects() {
        val input = """{"address":{"street":"Musterstr","city":"Ulm"}}"""
        val (fields, _) = LlmResponseParser.parse(input)
        assertEquals("Musterstr", fields["address_street"])
        assertEquals("Ulm", fields["address_city"])
    }

    @Test
    fun joinsArrayOfPrimitives() {
        val input = """{"tags":["grocery","food"]}"""
        val (fields, _) = LlmResponseParser.parse(input)
        assertEquals("grocery,food", fields["tags"])
    }

    @Test
    fun storesArrayOfObjectsAsJsonString() {
        val input = """{"items":[{"name":"Milk","price":"1.50"}]}"""
        val (fields, _) = LlmResponseParser.parse(input)
        assertTrue(fields.containsKey("items"))
        assertTrue(fields["items"]!!.contains("Milk"))
    }

    @Test
    fun handlesEmptyJson() {
        val input = "{}"
        val (fields, confidence) = LlmResponseParser.parse(input)
        assertTrue(fields.isEmpty())
        assertEquals(1.0f, confidence)
    }

    @Test
    fun neverThrows() {
        val inputs = listOf("", "{", "}", "null", "{\"a\":}", "{{invalid}}", "x".repeat(10000))
        for (input in inputs) {
            try {
                LlmResponseParser.parse(input)
            } catch (e: Exception) {
                throw AssertionError("Parser threw exception on input: $input", e)
            }
        }
    }
}
