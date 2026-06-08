package com.household.app.feature.assistant

import android.content.Context
import android.database.Cursor
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

    companion object {
        // Keep this large for LAN Ollama use; local on-device models may still truncate by context window.
        private const val MAX_FULL_DB_CHARS = 1_500_000
    }

    private fun getDatabase(): RoomDatabase? {
        return try {
            val clazz = Class.forName("com.household.app.data.AppDatabase")
            val method = clazz.getMethod("getInstance", Context::class.java)
            method.invoke(null, context) as? RoomDatabase
        } catch (e: Exception) {
            null
        }
    }

    suspend fun buildContext(userQuery: String, includeFullDb: Boolean = false): String = withContext(Dispatchers.IO) {
        try {
            val db = getDatabase() ?: return@withContext ""

            if (includeFullDb) {
                return@withContext buildFullDatabaseContext(db)
            }
            
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

    suspend fun executeReadOnlySql(
        query: String,
        maxRows: Int = 200,
        maxChars: Int = 120_000
    ): String = withContext(Dispatchers.IO) {
        try {
            val db = getDatabase() ?: return@withContext "{\"error\":\"database_unavailable\"}"
            val cursor = db.query(SimpleSQLiteQuery(query))
            cursor.use {
                val cols = cursor.columnNames
                val sb = StringBuilder()
                sb.append("{\"columns\":[")
                cols.forEachIndexed { idx, col ->
                    if (idx > 0) sb.append(",")
                    sb.append("\"").append(escapeJson(col)).append("\"")
                }
                sb.append("],\"rows\":[")

                var rowCount = 0
                var firstRow = true
                while (cursor.moveToNext() && rowCount < maxRows) {
                    if (sb.length > maxChars) {
                        break
                    }
                    if (!firstRow) sb.append(",")
                    firstRow = false
                    sb.append(rowToJson(cursor, cols))
                    rowCount++
                }
                sb.append("],\"rowCount\":").append(rowCount)
                if (!cursor.isAfterLast) {
                    sb.append(",\"truncated\":true")
                }
                sb.append("}")
                sb.toString()
            }
        } catch (e: Exception) {
            val msg = e.message?.take(240) ?: "query_failed"
            "{\"error\":\"${escapeJson(msg)}\"}"
        }
    }

    private fun buildFullDatabaseContext(db: RoomDatabase): String {
        val out = StringBuilder()
        out.append("=== FULL_DB_EXPORT_JSON ===\n")
        out.append("{\"tables\":{")

        val tables = getUserTables(db)
        var firstTable = true

        for (table in tables) {
            if (!firstTable) out.append(",")
            firstTable = false

            if (out.length > MAX_FULL_DB_CHARS) {
                out.append("\"__truncated__\":{\"reason\":\"max_chars\",\"max\":")
                out.append(MAX_FULL_DB_CHARS)
                out.append("}")
                break
            }

            val safeTable = escapeJson(table)
            out.append("\"").append(safeTable).append("\":")
            out.append(exportTable(db, table))
        }

        out.append("}}\n")
        out.append("=== END_FULL_DB_EXPORT ===")
        return out.toString()
    }

    private fun getUserTables(db: RoomDatabase): List<String> {
        val result = mutableListOf<String>()
        val cursor = db.query(
            SimpleSQLiteQuery(
                """
                SELECT name
                FROM sqlite_master
                WHERE type='table'
                  AND name NOT LIKE 'sqlite_%'
                  AND name NOT LIKE 'room_%'
                ORDER BY name
                """.trimIndent()
            )
        )
        try {
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0))
            }
        } finally {
            cursor.close()
        }
        return result
    }

    private fun exportTable(db: RoomDatabase, table: String): String {
        val sql = "SELECT * FROM \"${table.replace("\"", "\"\"")}\""
        val cursor = db.query(SimpleSQLiteQuery(sql))
        return try {
            val cols = cursor.columnNames
            val sb = StringBuilder()
            sb.append("{\"columns\":[")
            cols.forEachIndexed { idx, c ->
                if (idx > 0) sb.append(",")
                sb.append("\"").append(escapeJson(c)).append("\"")
            }
            sb.append("],\"rows\":[")

            var firstRow = true
            while (cursor.moveToNext()) {
                if (!firstRow) sb.append(",")
                firstRow = false
                sb.append(rowToJson(cursor, cols))
            }

            sb.append("]}")
            sb.toString()
        } finally {
            cursor.close()
        }
    }

    private fun rowToJson(cursor: Cursor, cols: Array<String>): String {
        val sb = StringBuilder("{")
        cols.forEachIndexed { idx, name ->
            if (idx > 0) sb.append(",")
            sb.append("\"").append(escapeJson(name)).append("\":")
            sb.append(valueToJson(cursor, idx))
        }
        sb.append("}")
        return sb.toString()
    }

    private fun valueToJson(cursor: Cursor, idx: Int): String {
        return when (cursor.getType(idx)) {
            Cursor.FIELD_TYPE_NULL -> "null"
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(idx).toString()
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(idx).toString()
            Cursor.FIELD_TYPE_STRING -> "\"${escapeJson(cursor.getString(idx) ?: "")}\""
            Cursor.FIELD_TYPE_BLOB -> {
                val b = cursor.getBlob(idx)
                "\"<blob:${b?.size ?: 0} bytes>\""
            }
            else -> "\"\""
        }
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
