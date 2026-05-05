package com.household.expenses.backup

import android.content.Context
import com.household.core.BackupManager
import com.household.core.BackupMetadata
import com.household.core.BackupResult
import com.household.core.EncryptionUtils
import com.household.expenses.db.ExpensesDatabase
import com.household.expenses.db.entity.BudgetCategory
import com.household.expenses.db.entity.Transaction
import com.household.expenses.db.entity.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Implements BackupManager for household expenses data.
 * Handles local backup/restore of transactions, categories, and trips.
 */
class ExpensesBackupManager(
    private val context: Context,
    private val database: ExpensesDatabase
) : BackupManager {

    private val txDao = database.transactionDao()
    private val budgetDao = database.budgetCategoryDao()
    private val tripDao = database.tripDao()

    override suspend fun createBackup(
        householdId: String,
        backupFile: File,
        encrypted: Boolean
    ): BackupResult = withContext(Dispatchers.IO) {
        return@withContext try {
            // Fetch all data for this household
            val transactions = txDao.getAll(householdId)
            val categories = budgetDao.getAll(householdId)
            val trips = tripDao.getAll(householdId)

            // Build JSON structure
            val backupJson = JSONObject().apply {
                put("householdId", householdId)
                put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                put("encrypted", encrypted)

                // Transactions
                put("transactions", JSONArray(transactions.map { tx ->
                    JSONObject().apply {
                        put("id", tx.id)
                        put("date", tx.date)
                        put("description", tx.description)
                        put("amount", tx.amount)
                        put("bank", tx.bank)
                        put("category", tx.category)
                        put("budgetCategory", tx.budgetCategory)
                        put("tripId", tx.tripId)
                        put("importedAt", tx.importedAt)
                        put("hash", tx.hash)
                    }
                }))

                // Categories
                put("budgetCategories", JSONArray(categories.map { cat ->
                    JSONObject().apply {
                        put("id", cat.id)
                        put("name", cat.name)
                        put("monthlyLimit", cat.monthlyLimit)
                        put("allowedBanks", cat.allowedBanks)
                        put("priority", cat.priority)
                        put("description", cat.description)
                    }
                }))

                // Trips
                put("trips", JSONArray(trips.map { trip ->
                    JSONObject().apply {
                        put("id", trip.id)
                        put("name", trip.name)
                        put("startDate", trip.startDate)
                        put("endDate", trip.endDate)
                        put("notes", trip.notes)
                        put("createdAt", trip.createdAt)
                    }
                }))
            }

            val backupData = backupJson.toString().toByteArray()

            // Encrypt if needed
            val finalData = if (encrypted) {
                EncryptionUtils.encrypt(backupData).toByteArray(Charsets.UTF_8)
            } else {
                backupData
            }

            // Write to file
            backupFile.parentFile?.mkdirs()
            backupFile.writeBytes(finalData)

            BackupResult(
                success = true,
                householdId = householdId,
                backupSize = backupFile.length(),
                timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                encrypted = encrypted
            )
        } catch (e: Exception) {
            BackupResult(
                success = false,
                householdId = householdId,
                errorMessage = "Backup failed: ${e.message}"
            )
        }
    }

    override suspend fun restoreBackup(
        householdId: String,
        backupFile: File
    ): BackupResult = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!backupFile.exists()) {
                return@withContext BackupResult(
                    success = false,
                    householdId = householdId,
                    errorMessage = "Backup file not found: ${backupFile.absolutePath}"
                )
            }

            var data = backupFile.readBytes()

            // Try to decrypt (auto-detect)
            data = try {
                EncryptionUtils.decrypt(String(data, Charsets.UTF_8))
            } catch (e: Exception) {
                // Not encrypted, use raw data
                data
            }

            val backupJson = JSONObject(String(data))

            // Validate household ID match
            val backupHouseholdId = backupJson.getString("householdId")
            if (backupHouseholdId != householdId) {
                return@withContext BackupResult(
                    success = false,
                    householdId = householdId,
                    errorMessage = "Household ID mismatch: backup is for $backupHouseholdId, restoring to $householdId"
                )
            }

            // Restore transactions
            val txArray = backupJson.getJSONArray("transactions")
            val transactions = (0 until txArray.length()).map { i ->
                val obj = txArray.getJSONObject(i)
                Transaction(
                    id = obj.getLong("id"),
                    householdId = householdId,
                    date = obj.getString("date"),
                    description = obj.getString("description"),
                    amount = obj.getDouble("amount"),
                    bank = obj.getString("bank"),
                    category = obj.getString("category"),
                    budgetCategory = obj.getString("budgetCategory"),
                    tripId = obj.optLong("tripId", -1).takeIf { it != -1L },
                    importedAt = obj.getString("importedAt"),
                    hash = obj.getString("hash")
                )
            }
            txDao.insertAll(transactions)

            // Restore categories
            val catArray = backupJson.getJSONArray("budgetCategories")
            val categories = (0 until catArray.length()).map { i ->
                val obj = catArray.getJSONObject(i)
                BudgetCategory(
                    id = obj.getLong("id"),
                    householdId = householdId,
                    name = obj.getString("name"),
                    monthlyLimit = obj.getDouble("monthlyLimit"),
                    allowedBanks = obj.getString("allowedBanks"),
                    priority = obj.getString("priority"),
                    description = obj.getString("description")
                )
            }
            budgetDao.upsertAll(categories)

            // Restore trips
            val tripArray = backupJson.getJSONArray("trips")
            val trips = (0 until tripArray.length()).map { i ->
                val obj = tripArray.getJSONObject(i)
                Trip(
                    id = obj.getLong("id"),
                    householdId = householdId,
                    name = obj.getString("name"),
                    startDate = obj.getString("startDate"),
                    endDate = obj.getString("endDate"),
                    notes = obj.getString("notes"),
                    createdAt = obj.getString("createdAt")
                )
            }
            for (trip in trips) {
                tripDao.insert(trip)
            }

            BackupResult(
                success = true,
                householdId = householdId,
                backupSize = backupFile.length(),
                timestamp = backupJson.getString("timestamp"),
                encrypted = backupJson.getBoolean("encrypted")
            )
        } catch (e: Exception) {
            BackupResult(
                success = false,
                householdId = householdId,
                errorMessage = "Restore failed: ${e.message}"
            )
        }
    }

    override suspend fun listBackups(householdId: String): List<BackupMetadata> =
        withContext(Dispatchers.IO) {
            val backupDir = File(context.filesDir, "expenses_backups")
            if (!backupDir.exists()) return@withContext emptyList()

            backupDir.listFiles { file ->
                file.isFile && file.name.startsWith(householdId)
            }?.map { file ->
                BackupMetadata(
                    filePath = file.absolutePath,
                    fileName = file.name,
                    fileSizeBytes = file.length(),
                    createdAt = LocalDateTime.ofEpochSecond(
                        file.lastModified() / 1000,
                        0,
                        java.time.ZoneOffset.UTC
                    ).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    isEncrypted = file.name.contains("_encrypted"),
                    householdId = householdId
                )
            } ?: emptyList()
        }

    override suspend fun deleteBackup(backupFile: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                backupFile.delete()
            } catch (e: Exception) {
                false
            }
        }
}
