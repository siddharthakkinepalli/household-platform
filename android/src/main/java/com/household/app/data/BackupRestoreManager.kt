package com.household.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.household.app.data.entities.WalletTransactionEntity
import com.household.app.data.entities.WalletTripEntity
import com.household.app.data.entities.TransactionOverrideEntity
import com.household.app.data.entities.ExcludedTransactionEntity
import com.household.app.data.entities.WeightSnapshotEntity
import com.household.app.data.entities.MealsSummaryEntity
import com.household.app.data.entities.DashboardPrefsEntity

class BackupRestoreManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val backupDir = File(context.getExternalFilesDir(null), "backups")

    init {
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
    }

    suspend fun createBackup(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val backupData = JSONObject()

            // Export all data from database
            val transactions = db.walletTransactionDao().getAllTransactions()
            val trips = db.walletTripDao().getAllTrips()
            val overrides = db.transactionOverrideDao().getAllOverrides()
            val excluded = db.excludedTransactionDao().getExcludedIds()
            val weightSnapshot = db.weightSnapshotDao().getLatestSnapshot()
            val mealsSummary = db.mealsSummaryDao().getSummary()
            val dashboardPrefs = db.dashboardPrefsDao().getPrefs()

            // Convert to JSON
            backupData.put("transactions", transactionsToJson(transactions))
            backupData.put("trips", tripsToJson(trips))
            backupData.put("overrides", overridesToJson(overrides))
            backupData.put("excluded", JSONArray(excluded))
            if (weightSnapshot != null) {
                backupData.put("weight", weightSnapshotToJson(weightSnapshot))
            }
            if (mealsSummary != null) {
                backupData.put("meals", mealsSummaryToJson(mealsSummary))
            }
            if (dashboardPrefs != null) {
                backupData.put("prefs", dashboardPrefsToJson(dashboardPrefs))
            }

            // Add metadata
            backupData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
            backupData.put("version", 1)

            // Save to file
            val fileName = "household_backup_${System.currentTimeMillis()}.json"
            val file = File(backupDir, fileName)
            file.writeText(backupData.toString(2))

            Log.d("BackupRestoreManager", "Backup created: ${file.absolutePath}")
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Log.e("BackupRestoreManager", "Backup failed", e)
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(filePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Backup file not found"))
            }

            val backupData = JSONObject(file.readText())

            // Restore transactions
            val txArray = backupData.optJSONArray("transactions")
            if (txArray != null) {
                val transactions = jsonToTransactions(txArray)
                db.walletTransactionDao().deleteAllTransactions()
                db.walletTransactionDao().insertTransactions(transactions)
            }

            // Restore trips
            val tripArray = backupData.optJSONArray("trips")
            if (tripArray != null) {
                val trips = jsonToTrips(tripArray)
                db.walletTripDao().deleteAllTrips()
                db.walletTripDao().insertTrips(trips)
            }

            // Restore overrides
            val overrideArray = backupData.optJSONArray("overrides")
            if (overrideArray != null) {
                val overrides = jsonToOverrides(overrideArray)
                db.transactionOverrideDao().deleteAllOverrides()
                overrides.forEach { db.transactionOverrideDao().insertOverride(it) }
            }

            // Restore excluded
            val excludedArray = backupData.optJSONArray("excluded")
            if (excludedArray != null) {
                val excludedList = mutableListOf<ExcludedTransactionEntity>()
                for (i in 0 until excludedArray.length()) {
                    excludedList.add(ExcludedTransactionEntity(excludedArray.getInt(i)))
                }
                db.excludedTransactionDao().deleteAllExcluded()
                db.excludedTransactionDao().insertExcludedBatch(excludedList)
            }

            // Restore weight
            val weightObj = backupData.optJSONObject("weight")
            if (weightObj != null) {
                val weight = jsonToWeightSnapshot(weightObj)
                db.weightSnapshotDao().insertSnapshot(weight)
            }

            // Restore meals
            val mealsObj = backupData.optJSONObject("meals")
            if (mealsObj != null) {
                val meals = jsonToMealsSummary(mealsObj)
                db.mealsSummaryDao().insertSummary(meals)
            }

            // Restore prefs
            val prefsObj = backupData.optJSONObject("prefs")
            if (prefsObj != null) {
                val prefs = jsonToDashboardPrefs(prefsObj)
                db.dashboardPrefsDao().insertPrefs(prefs)
            }

            Log.d("BackupRestoreManager", "Restore completed from: $filePath")
            Result.success("Restore completed successfully")
        } catch (e: Exception) {
            Log.e("BackupRestoreManager", "Restore failed", e)
            Result.failure(e)
        }
    }

    suspend fun listBackups(): List<File> = withContext(Dispatchers.IO) {
        backupDir.listFiles { file -> file.name.endsWith(".json") }?.toList() ?: emptyList()
    }

    suspend fun deleteBackup(filePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.delete()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete backup"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============ Conversion helpers ============
    private fun transactionsToJson(transactions: List<com.household.app.data.entities.WalletTransactionEntity>): JSONArray {
        val array = JSONArray()
        transactions.forEach { tx ->
            array.put(JSONObject().apply {
                put("id", tx.id)
                put("title", tx.title)
                put("category", tx.category)
                put("amount", tx.amount)
                put("date", tx.date.toString())
                put("paymentType", tx.paymentType)
                put("trip", tx.trip)
                put("note", tx.note)
                put("bankName", tx.bankName)
                put("excluded", tx.excluded)
            })
        }
        return array
    }

    private fun jsonToTransactions(array: JSONArray): List<com.household.app.data.entities.WalletTransactionEntity> {
        val list = mutableListOf<com.household.app.data.entities.WalletTransactionEntity>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                com.household.app.data.entities.WalletTransactionEntity(
                    id = obj.getInt("id"),
                    title = obj.getString("title"),
                    category = obj.getString("category"),
                    amount = obj.getDouble("amount"),
                    date = LocalDate.parse(obj.getString("date")),
                    paymentType = obj.getString("paymentType"),
                    trip = obj.optString("trip", "").ifBlank { null },
                    note = obj.optString("note", "").ifBlank { "" },
                    bankName = obj.optString("bankName", ""),
                    excluded = obj.optBoolean("excluded", false)
                )
            )
        }
        return list
    }

    private fun tripsToJson(trips: List<com.household.app.data.entities.WalletTripEntity>): JSONArray {
        val array = JSONArray()
        trips.forEach { trip ->
            array.put(JSONObject().apply {
                put("name", trip.name)
                put("budget", trip.budget)
            })
        }
        return array
    }

    private fun jsonToTrips(array: JSONArray): List<com.household.app.data.entities.WalletTripEntity> {
        val list = mutableListOf<com.household.app.data.entities.WalletTripEntity>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                com.household.app.data.entities.WalletTripEntity(
                    name = obj.getString("name"),
                    budget = obj.getDouble("budget")
                )
            )
        }
        return list
    }

    private fun overridesToJson(overrides: List<TransactionOverrideEntity>): JSONArray {
        val array = JSONArray()
        overrides.forEach { override ->
            array.put(JSONObject().apply {
                put("transactionId", override.transactionId)
                put("categoryOverride", override.categoryOverride)
                put("tripOverride", override.tripOverride)
            })
        }
        return array
    }

    private fun jsonToOverrides(array: JSONArray): List<TransactionOverrideEntity> {
        val list = mutableListOf<TransactionOverrideEntity>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                TransactionOverrideEntity(
                    transactionId = obj.getInt("transactionId"),
                    categoryOverride = obj.optString("categoryOverride", "").ifBlank { null },
                    tripOverride = obj.optString("tripOverride", "").ifBlank { null }
                )
            )
        }
        return list
    }

    private fun weightSnapshotToJson(snapshot: com.household.app.data.entities.WeightSnapshotEntity): JSONObject {
        return JSONObject().apply {
            put("currentKg", snapshot.currentKg)
            put("previousKg", snapshot.previousKg)
            put("date", snapshot.date)
        }
    }

    private fun jsonToWeightSnapshot(obj: JSONObject): com.household.app.data.entities.WeightSnapshotEntity {
        return com.household.app.data.entities.WeightSnapshotEntity(
            currentKg = obj.getDouble("currentKg"),
            previousKg = obj.optDouble("previousKg", Double.NaN).let { if (it.isNaN()) null else it },
            date = obj.getString("date")
        )
    }

    private fun mealsSummaryToJson(summary: MealsSummaryEntity): JSONObject {
        return JSONObject().apply {
            put("mealsToday", summary.mealsToday)
            put("nextMeal", summary.nextMeal)
            put("updatedOn", summary.updatedOn)
        }
    }

    private fun jsonToMealsSummary(obj: JSONObject): MealsSummaryEntity {
        return MealsSummaryEntity(
            mealsToday = obj.getInt("mealsToday"),
            nextMeal = obj.getString("nextMeal"),
            updatedOn = obj.getString("updatedOn")
        )
    }

    private fun dashboardPrefsToJson(prefs: DashboardPrefsEntity): JSONObject {
        return JSONObject().apply {
            put("walletQuickCategory", prefs.walletQuickCategory)
            put("walletQuickQuery", prefs.walletQuickQuery)
            put("monthlyBudget", prefs.monthlyBudget)
        }
    }

    private fun jsonToDashboardPrefs(obj: JSONObject): DashboardPrefsEntity {
        return DashboardPrefsEntity(
            walletQuickCategory = obj.optString("walletQuickCategory", ""),
            walletQuickQuery = obj.optString("walletQuickQuery", ""),
            monthlyBudget = obj.optInt("monthlyBudget", 3000)
        )
    }

}
