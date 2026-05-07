package com.household.app.domain.utils

import com.household.app.data.config.RuleEngineService
import com.household.app.data.entities.MerchantRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FiscalAndRuleEngineTest {

    @Test
    fun transactionBeforeAnchorFallsIntoPreviousCycle() {
        val fiscalId = FiscalDateUtils.getFiscalCycleId(LocalDate.of(2026, 5, 24), 25)
        assertEquals("2026_04", fiscalId)
    }

    @Test
    fun transactionOnAnchorStartsCurrentCycle() {
        val fiscalId = FiscalDateUtils.getFiscalCycleId(LocalDate.of(2026, 5, 25), 25)
        assertEquals("2026_05", fiscalId)
    }

    @Test
    fun exactRuleWinsOverPartialRule() {
        val partialRule = MerchantRuleEntity(
            merchantPattern = "rewe",
            targetCategoryId = "groceries",
            priority = 1,
            updatedAt = "1"
        )
        val exactRule = MerchantRuleEntity(
            merchantPattern = "rewe city 123",
            targetCategoryId = "shopping",
            priority = 1,
            updatedAt = "2"
        )

        val winner = RuleEngineService.pickRule("REWE CITY 123", listOf(partialRule, exactRule))

        assertEquals("shopping", winner?.targetCategoryId)
    }

    @Test
    fun noRuleReturnsNull() {
        val winner = RuleEngineService.pickRule("Unknown Merchant", emptyList())
        assertNull(winner)
    }
}
