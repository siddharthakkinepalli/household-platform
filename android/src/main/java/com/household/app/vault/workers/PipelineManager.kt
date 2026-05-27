package com.household.app.vault.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.household.app.vault.DriveSyncWorker
import com.household.app.vault.workers.BankStatementDriveWorker
import com.household.app.vault.workers.DriveTaxRouteWorker
import com.household.app.vault.workers.ReceiptMatchingWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

object PipelineManager {

    /**
     * Enqueues a chained pipeline after a CSV import:
     * CategorizationWorker → RecurringDetectionWorker
     *
     * Uses unique work name "csv_import_pipeline" with REPLACE policy so a
     * second import while the first is running always restarts the chain.
     */
    fun enqueueCsvImportChain(context: Context) {
        val categorizationRequest = OneTimeWorkRequestBuilder<CategorizationWorker>().build()
        val recurringDetectionRequest = OneTimeWorkRequestBuilder<RecurringDetectionWorker>().build()

        WorkManager.getInstance(context)
            .beginUniqueWork(
                "csv_import_pipeline",
                ExistingWorkPolicy.REPLACE,
                categorizationRequest
            )
            .then(recurringDetectionRequest)
            .enqueue()
    }

    /**
     * Enqueues a Drive sync for a single vault entry.
     * Requires a network connection. Not unique — each vault entry gets its own sync.
     */
    fun enqueueVaultDriveSync(context: Context, vaultId: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<DriveSyncWorker>()
            .setInputData(workDataOf("vault_id" to vaultId))
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(syncRequest)
    }

    /**
     * Enqueues a Drive tax routing job for a single vault entry.
     * Classifies the document via OCR/category and uploads it to the correct
     * subfolder under the Income Tax folder (e.g. Payslips, Kita, Currentbills).
     * Requires a network connection.
     */
    fun enqueueDriveTaxRoute(context: Context, vaultId: Long) {
        val request = OneTimeWorkRequestBuilder<DriveTaxRouteWorker>()
            .setInputData(workDataOf("vault_id" to vaultId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    /**
     * Enqueues a receipt-to-transaction matching job for a single vault entry.
     * Looks for a wallet transaction within ±3 days and ±10% of the vault entry's
     * amount; if exactly one match is found, links them via attachVaultEntry.
     */
    fun enqueueReceiptMatch(context: Context, vaultId: Long) {
        val request = OneTimeWorkRequestBuilder<ReceiptMatchingWorker>()
            .setInputData(workDataOf("vault_id" to vaultId))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    /**
     * Enqueues OCR (if needed) + document classification + expiry extraction for a vault entry.
     * Updates vault_entries.category / subFolder / documentTitle, and creates a DocumentEntity
     * + DocumentAlertEntity when an expiry date is detected.
     */
    fun enqueueVaultDocumentParse(context: Context, vaultId: Long) {
        val request = OneTimeWorkRequestBuilder<VaultDocumentParserWorker>()
            .setInputData(workDataOf("vault_id" to vaultId))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    /**
     * Schedules a weekly Drive backup of the Room database.
     * Uses KEEP so re-registration on restart does not reset the timer.
     * No-ops if Drive is not enabled — caller must gate with DriveDataStore.isDriveEnabled().
     */
    fun scheduleDbBackup(context: Context) {
        val request = PeriodicWorkRequestBuilder<DbBackupWorker>(7, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "db_backup_drive",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Schedules a daily check for expiring documents and fires system notifications.
     * Uses KEEP so re-registration on restart does not reset the 24-hour timer.
     */
    fun scheduleExpiryNotifications(context: Context) {
        ExpiryNotificationWorker.createChannel(context)
        val request = PeriodicWorkRequestBuilder<ExpiryNotificationWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "expiry_notifications",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Schedules a weekly Drive tax-checklist sync for the current year.
     * Uses KEEP policy so re-registration on app restart does not reset the timer.
     * No-ops if Drive is not enabled — caller must gate with DriveDataStore.isDriveEnabled().
     */
    fun scheduleTaxChecklistSync(context: Context) {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val request = PeriodicWorkRequestBuilder<TaxChecklistWorker>(7, TimeUnit.DAYS)
            .setInputData(workDataOf("year" to year))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "tax_checklist_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Enqueues an upload of a bank statement summary text file to Drive
     * BankStatements/ under the current year subfolder of the Income Tax folder.
     * Requires a network connection.
     */
    fun enqueueBankStatementUpload(context: Context, fileName: String, contentSummary: String) {
        val request = OneTimeWorkRequestBuilder<BankStatementDriveWorker>()
            .setInputData(workDataOf("file_name" to fileName, "content_summary" to contentSummary))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
