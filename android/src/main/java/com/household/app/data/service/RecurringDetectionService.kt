package com.household.app.data.service

import com.household.app.data.dao.RecurringBillDao
import com.household.app.data.dao.WalletTransactionDao
import com.household.app.data.entities.RecurringBillEntity
import com.household.app.data.entities.WalletTransactionEntity
import com.household.app.domain.utils.MerchantNameCleaner
import java.time.LocalDate
import java.time.ZoneOffset

class RecurringDetectionService(
    private val walletTransactionDao: WalletTransactionDao,
    private val recurringBillDao: RecurringBillDao,
    private val salaryAnchorDay: Int,
    /** Actual salary landing dates (oldest first) — override [salaryAnchorDay] for cycle boundaries. */
    private val actualSalaryDates: List<LocalDate> = emptyList()
) {
    // Categories that are never recurring (one-off by nature)
    private val oneOffCategories = setOf(
        "travel", "income", "excluded", "transfers", "bank fees & charges"
    )

    suspend fun detectAndStore() {
        // Protect patterns already managed by the user — never overwrite them
        val protectedPatterns = recurringBillDao.getUserManagedPatterns().toSet()

        val allTransactions = walletTransactionDao.getAllTransactions()

        val expenses = allTransactions.filter { tx ->
            tx.amount < 0
                && tx.category.lowercase() !in oneOffCategories
                && tx.title.isNotBlank()
                && kotlin.math.abs(tx.amount) >= 0.50  // skip micro-transactions (rounding noise)
        }

        val grouped: Map<String, List<WalletTransactionEntity>> =
            expenses.groupBy { tx -> normalizeMerchant(tx.title) }

        val detectedBills = mutableListOf<RecurringBillEntity>()

        for ((merchantKey, txList) in grouped) {
            if (merchantKey.isBlank()) continue
            if (protectedPatterns.contains(merchantKey)) continue

            val cycleSet = txList.map { tx -> fiscalCycleId(tx, salaryAnchorDay) }.toSet()
            if (cycleSet.size < 2) continue

            val absoluteAmounts = txList.map { kotlin.math.abs(it.amount) }
            val minAmt = absoluteAmounts.min()
            val maxAmt = absoluteAmounts.max()

            // Allow up to 50% variance — covers utility bills with seasonal fluctuation
            if (minAmt > 0 && maxAmt / minAmt > 1.50) continue

            val normalizedAmount = absoluteAmounts.average()
            val category = txList
                .groupingBy { it.category }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: txList.first().category

            val lastSeenDate = txList
                .maxOf { it.date }
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()

            detectedBills.add(
                RecurringBillEntity(
                    merchantPattern = merchantKey,
                    normalizedAmount = normalizedAmount,
                    minAmount = normalizedAmount * 0.70,
                    maxAmount = normalizedAmount * 1.30,
                    category = category,
                    lastSeenDate = lastSeenDate,
                    cycleCount = cycleSet.size,
                    isActive = true,
                    source = "AUTO"
                )
            )
        }

        // Only clear AUTO bills — CONFIRMED/MANUAL/DISMISSED are preserved
        recurringBillDao.clearAutoBills()
        recurringBillDao.upsertAll(detectedBills)
    }

    /**
     * Normalize merchant title for grouping:
     * 1. Run MerchantNameCleaner to strip SEPA/PayPal noise first
     * 2. Lowercase and strip non-alphanumeric
     * 3. Truncate to 20 chars (more than previous 14, catches more full names)
     */
    private fun normalizeMerchant(title: String): String {
        val cleaned = MerchantNameCleaner.clean(title)
        return cleaned
            .take(20)
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun fiscalCycleId(tx: WalletTransactionEntity, anchorDay: Int): Pair<Int, Int> {
        val date = tx.date

        if (actualSalaryDates.isNotEmpty()) {
            // Dynamic path: find the most recent salary landing on or before this transaction.
            val anchor = actualSalaryDates.filter { !it.isAfter(date) }.maxOrNull()
                ?: actualSalaryDates.first()  // transaction is before any salary — group with first cycle
            return Pair(anchor.year, anchor.monthValue)
        }

        // Fallback: fixed anchor day
        return if (date.dayOfMonth >= anchorDay) {
            Pair(date.year, date.monthValue)
        } else {
            val prev = date.minusMonths(1)
            Pair(prev.year, prev.monthValue)
        }
    }
}
