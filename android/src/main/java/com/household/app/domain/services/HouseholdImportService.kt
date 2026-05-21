package com.household.app.domain.services

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.GsonBuilder
import com.household.app.data.AppDatabase
import com.household.app.domain.models.HouseholdBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class ImportResult(
    val success: Boolean,
    val message: String,
    val imported: Int
)

class HouseholdImportService(private val db: AppDatabase) {

    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
        .create()

    suspend fun import(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val backup: HouseholdBackup? = context.contentResolver.openInputStream(uri)?.use { stream ->
                gson.fromJson(stream.bufferedReader(), HouseholdBackup::class.java)
            }

            if (backup == null) return@withContext ImportResult(
                success = false,
                message = "Could not open or parse file — check app storage permissions",
                imported = 0
            )

            if (backup.version > 1) {
                Log.w("HouseholdImport", "Backup version ${backup.version} is newer than supported (1). Continuing anyway.")
            }

            var count = 0

            if (backup.walletTransactions.isNotEmpty()) {
                db.walletTransactionDao().insertTransactionsIgnore(backup.walletTransactions)
                count += backup.walletTransactions.size
            }
            if (backup.vaultEntries.isNotEmpty()) {
                db.vaultDao().insertEntries(backup.vaultEntries)
                count += backup.vaultEntries.size
            }
            if (backup.pantryItems.isNotEmpty()) {
                db.pantryDao().insertItemsIgnore(backup.pantryItems)
                count += backup.pantryItems.size
            }
            if (backup.inventoryEvents.isNotEmpty()) {
                db.inventoryEventDao().insertEvents(backup.inventoryEvents)
                count += backup.inventoryEvents.size
            }
            if (backup.merchantRules.isNotEmpty()) {
                db.merchantRuleDao().insertRulesIgnore(backup.merchantRules)
                count += backup.merchantRules.size
            }
            if (backup.categoryThresholds.isNotEmpty()) {
                db.categoryThresholdDao().insertThresholdsIgnore(backup.categoryThresholds)
                count += backup.categoryThresholds.size
            }
            if (backup.familyMembers.isNotEmpty()) {
                db.familyMemberDao().insertMembers(backup.familyMembers)
                count += backup.familyMembers.size
            }
            if (backup.documents.isNotEmpty()) {
                db.documentDao().insertDocuments(backup.documents)
                count += backup.documents.size
            }
            if (backup.documentAlerts.isNotEmpty()) {
                db.documentAlertDao().insertAlertsIgnore(backup.documentAlerts)
                count += backup.documentAlerts.size
            }

            ImportResult(
                success = true,
                message = "Import complete${if (backup.version > 1) " (newer format — some fields may be missing)" else ""}",
                imported = count
            )
        } catch (e: Exception) {
            Log.e("HouseholdImport", "Import failed", e)
            ImportResult(
                success = false,
                message = "Import failed: ${e.message ?: "unknown error"}",
                imported = 0
            )
        }
    }
}

