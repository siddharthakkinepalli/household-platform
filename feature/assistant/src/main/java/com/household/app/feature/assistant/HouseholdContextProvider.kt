package com.household.app.feature.assistant

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HouseholdContextProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun getDatabase(): RoomDatabase? {
        return try {
            val clazz = Class.forName("com.household.app.data.AppDatabase")
            val method = clazz.getMethod("getInstance", Context::class.java)
            method.invoke(null, context) as? RoomDatabase
        } catch (e: Exception) {
            null
        }
    }

    suspend fun buildContext(userQuery: String): String = withContext(Dispatchers.IO) {
        try {
            val db = getDatabase() ?: return@withContext ""
            
            // 1. Month spend and top 3 categories
            val now = LocalDate.now()
            val startOfMonth = YearMonth.from(now).atDay(1).toString()
            val endOfMonth = YearMonth.from(now).atEndOfMonth().toString()
            
            val spendCursor = db.query(SimpleSQLiteQuery(
                "SELECT SUM(ABS(amount)) FROM wallet_transactions WHERE excluded = 0 AND amount < 0 AND date BETWEEN '$startOfMonth' AND '$endOfMonth'"
            ))
            val totalSpend = if (spendCursor.moveToFirst()) spendCursor.getDouble(0) else 0.0
            spendCursor.close()

            val catCursor = db.query(SimpleSQLiteQuery(
                "SELECT category, SUM(ABS(amount)) as sum_amt FROM wallet_transactions WHERE excluded = 0 AND amount < 0 AND date BETWEEN '$startOfMonth' AND '$endOfMonth' GROUP BY category ORDER BY sum_amt DESC LIMIT 3"
            ))
            val categories = mutableListOf<String>()
            while (catCursor.moveToNext()) {
                categories.add("${catCursor.getString(0)} €${"%.2f".format(catCursor.getDouble(1))}")
            }
            catCursor.close()
            val catStr = categories.joinToString(", ")

            // 2. Recurring bills
            val billCursor = db.query(SimpleSQLiteQuery("SELECT SUM(normalizedAmount) FROM recurring_bills WHERE isActive = 1"))
            val totalFixedCosts = if (billCursor.moveToFirst()) billCursor.getDouble(0) else 0.0
            billCursor.close()

            // 3. Salary source
            val salaryCursor = db.query(SimpleSQLiteQuery("SELECT lastAmount FROM salary_sources WHERE id = 1"))
            val salary = if (salaryCursor.moveToFirst()) salaryCursor.getDouble(0) else 0.0
            salaryCursor.close()

            // 4. Vault documents count
            val vaultCursor = db.query(SimpleSQLiteQuery("SELECT COUNT(DISTINCT vaultEntryId) FROM vault_document_pages WHERE processingState = 'INDEXED'"))
            val indexedCount = if (vaultCursor.moveToFirst()) vaultCursor.getInt(0) else 0
            vaultCursor.close()

            // 5. Recent 5 transactions
            var recentTxns = ""
            try {
                val txnCursor = db.query(SimpleSQLiteQuery(
                    "SELECT title, amount, category, date FROM wallet_transactions WHERE excluded = 0 ORDER BY date DESC LIMIT 5"
                ))
                val txns = mutableListOf<String>()
                while (txnCursor.moveToNext()) {
                    val amt = txnCursor.getDouble(1)
                    val sign = if (amt >= 0) "+" else "-"
                    txns.add("${txnCursor.getString(0)} $sign€${"%.2f".format(Math.abs(amt))} ${txnCursor.getString(2)}")
                }
                txnCursor.close()
                if (txns.isNotEmpty()) recentTxns = "Recent txns: ${txns.joinToString(", ")}"
            } catch (e: Exception) {}

            // 6. Next upcoming bill — oldest lastSeenDate = most likely due soonest
            var nextBill = ""
            try {
                val nextBillCursor = db.query(SimpleSQLiteQuery(
                    "SELECT merchantPattern, normalizedAmount FROM recurring_bills WHERE isActive = 1 ORDER BY lastSeenDate ASC LIMIT 1"
                ))
                if (nextBillCursor.moveToFirst()) {
                    nextBill = "Next bill: ${nextBillCursor.getString(0)} €${"%.2f".format(nextBillCursor.getDouble(1))}"
                }
                nextBillCursor.close()
            } catch (e: Exception) {}

            // 7. Next expiring document
            var expiringDoc = ""
            try {
                val docCursor = db.query(SimpleSQLiteQuery(
                    "SELECT title, expiryDate FROM documents WHERE expiryDate IS NOT NULL ORDER BY expiryDate ASC LIMIT 1"
                ))
                if (docCursor.moveToFirst()) {
                    // expiryDate is stored as epoch millis in Room usually, converting to string
                    val expiryMillis = docCursor.getLong(1)
                    val expiryDate = LocalDate.ofEpochDay(expiryMillis / (24 * 60 * 60 * 1000))
                    expiringDoc = "Expiring soon: ${docCursor.getString(0)} on $expiryDate"
                }
                docCursor.close()
            } catch (e: Exception) {}

            val contextBuilder = StringBuilder()
            contextBuilder.append("=== VERIFIED HOUSEHOLD DATA ===\n")
            contextBuilder.append("Month spend: €${"%.2f".format(totalSpend)} | Top categories: $catStr\n")
            contextBuilder.append("Fixed monthly costs: €${"%.2f".format(totalFixedCosts)}\n")
            contextBuilder.append("Salary: €${"%.2f".format(salary)}\n")
            contextBuilder.append("Vault documents indexed: $indexedCount\n")
            if (recentTxns.isNotEmpty()) contextBuilder.append("$recentTxns\n")
            if (nextBill.isNotEmpty()) contextBuilder.append("$nextBill\n")
            if (expiringDoc.isNotEmpty()) contextBuilder.append("$expiringDoc\n")
            contextBuilder.append("=== END DATA ===")

            contextBuilder.toString()
        } catch (e: Exception) {
            ""
        }
    }
}
