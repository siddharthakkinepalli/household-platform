package com.household.app.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvParserServiceTest {

    @Test
    fun parsesRowsAndDetectsBankFromN26Csv() {
        val service = CsvParserService()
        val csv = """
            Date,Merchant,Amount,Category,Bank
            2026-05-26,REWE,-42.35,Groceries,N26
            2026-05-27,DB Transfer,1200.00,Income,N26
        """.trimIndent()

        val result = service.parse(
            csvText = csv,
            fileName = "n26_statement.csv",
            fileHash = "hash-1",
            startingId = 100
        )

        val success = result as ImportParseResult.Success
        assertEquals("N26", success.summary.detectedBank)
        assertEquals(2, success.summary.parsedCount)
        assertEquals(0, success.summary.skippedCount)
        assertEquals(',', success.summary.delimiter)
        assertTrue(success.summary.transactions.first().title.contains("REWE"))
    }

    @Test
    fun cleansNoisyMerchantDescriptions() {
        val service = CsvParserService()
        val csv = """
            Booking date,Booking text,Amount
            04.05.2026,Wise Europe SA TRWIBEB1XXX BE79967040785533 P12179675 End-To-End Reference: MOB.120.UE.POS00501306,-700
        """.trimIndent()

        val result = service.parse(
            csvText = csv,
            fileName = "Commerz.csv",
            fileHash = "hash-2",
            startingId = 1
        )

        val success = result as ImportParseResult.Success
        assertEquals(1, success.summary.parsedCount)
        assertEquals("Wise", success.summary.transactions.first().title)
    }
}
