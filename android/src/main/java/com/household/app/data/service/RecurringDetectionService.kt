package com.household.app.data.service

import com.household.app.data.dao.RecurringBillDao
import com.household.app.data.dao.WalletTransactionDao
import com.household.app.data.entities.RecurringBillEntity
import com.household.app.data.entities.WalletTransactionEntity
import java.time.ZoneOffset

class RecurringDetectionService(
    private val walletTransactionDao: WalletTransactionDao,
    private val recurringBillDao: RecurringBillDao,
    private val salaryAnchorDay: Int
) {
    suspend fun detectAndStore() {
        // Protect patterns already managed by the user — never overwrite them
        val protectedPatterns = recurringBillDao.getUserManagedPatterns().toSet()

        val allTransactions = walletTransactionDao.getAllTransactions()

        val expenses = allTransactions.filter { tx ->
            tx.amount < 0
                && tx.category != "Excluded"
                && tx.category != "Income"
                && tx.title.isNotBlank()
        }

        val grouped: Map<String, List<WalletTransactionEntity>> =
            expenses.groupBy { tx -> normalizeMerchant(tx.title) }

        val detectedBills = mutableListOf<RecurringBillEntity>()

        for ((merchantKey, txList) in grouped) {
            if (protectedPatterns.contains(merchantKey)) continue

            val cycleSet = txList.map { tx -> fiscalCycleId(tx, salaryAnchorDay) }.toSet()
            if (cycleSet.size < 2) continue

            val absoluteAmounts = txList.map { kotlin.math.abs(it.amount) }
            val minAmt = absoluteAmounts.min()
            val maxAmt = absoluteAmounts.max()
            if (maxAmt / minAmt > 1.30) continue

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
                    minAmount = normalizedAmount * 0.85,
                    maxAmount = normalizedAmount * 1.15,
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

    private fun normalizeMerchant(title: String): String =
        title.take(14)
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .trim()

    private fun fiscalCycleId(tx: WalletTransactionEntity, anchorDay: Int): Pair<Int, Int> {
        val date = tx.date
        return if (date.dayOfMonth >= anchorDay) {
            Pair(date.year, date.monthValue)
        } else {
            val prev = date.minusMonths(1)
            Pair(prev.year, prev.monthValue)
        }
    }
}
