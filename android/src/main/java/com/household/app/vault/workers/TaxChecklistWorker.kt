package com.household.app.vault.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.household.app.data.AppDatabase
import com.household.app.data.entities.TaxCheckEntity
import com.household.app.vault.DriveAuthManager
import com.household.app.vault.DriveDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class TaxChecklistWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val year = inputData.getInt("year", Calendar.getInstance().get(Calendar.YEAR))

        val account = DriveAuthManager(applicationContext).lastSignedInAccount()
            ?: return Result.retry()

        return withContext(Dispatchers.IO) {
            try {
                val credential = GoogleAccountCredential
                    .usingOAuth2(applicationContext, listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE))
                    .apply { selectedAccount = account.account }

                val drive = Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("HouseholdPlatform").build()

                val db = AppDatabase.getInstance(applicationContext)
                val taxCheckDao = db.taxCheckDao()

                // Resolve the Income Tax shortcut/folder in Jugaad
                val jugaadId = DriveDataStore.getJugaadFolderId(applicationContext)
                    ?: return@withContext Result.failure()

                val jugaadChildren = drive.files().list()
                    .setQ("'$jugaadId' in parents and trashed = false")
                    .setFields("files(id,name,mimeType,shortcutDetails)")
                    .execute().files

                val taxEntry = jugaadChildren.firstOrNull {
                    it.name.contains("income tax", ignoreCase = true) ||
                        it.name.contains("income_tax", ignoreCase = true)
                }

                val incomeTaxFolderId = when {
                    taxEntry?.mimeType == "application/vnd.google-apps.shortcut" ->
                        taxEntry.shortcutDetails?.targetId
                    else -> taxEntry?.id
                } ?: return@withContext Result.failure()

                DriveDataStore.setIncomeTaxFolderId(applicationContext, incomeTaxFolderId)

                // Find the year subfolder
                val yearChildren = drive.files().list()
                    .setQ("'$incomeTaxFolderId' in parents and trashed = false")
                    .setFields("files(id,name,mimeType)")
                    .execute().files

                val yearFolder = yearChildren.firstOrNull { it.name == year.toString() }

                if (yearFolder == null) {
                    // No year folder yet — record a minimal synced entry
                    taxCheckDao.upsert(
                        TaxCheckEntity(
                            year = year,
                            driveIncomeTaxFolderId = incomeTaxFolderId,
                            syncStatus = "SYNCED",
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                    taxCheckDao.setDriveFolderId(year, incomeTaxFolderId)
                    return@withContext Result.success()
                }

                val yearFolderId = yearFolder.id

                // List children of the year folder (direct files + subfolders)
                val yearFolderChildren = drive.files().list()
                    .setQ("'$yearFolderId' in parents and trashed = false")
                    .setFields("files(id,name,mimeType)")
                    .execute().files

                // Helper: count files inside a named subfolder
                fun countInSubfolder(subfolderName: String): List<com.google.api.services.drive.model.File> {
                    val subfolder = yearFolderChildren.firstOrNull {
                        it.name.equals(subfolderName, ignoreCase = true) &&
                            it.mimeType == "application/vnd.google-apps.folder"
                    } ?: return emptyList()
                    return drive.files().list()
                        .setQ("'${subfolder.id}' in parents and trashed = false")
                        .setFields("files(id,name,mimeType)")
                        .execute().files
                }

                // Payslips
                val payslipFiles = countInSubfolder("Payslips")
                val payslipsCount = payslipFiles.size
                val lohnsteuerFound = payslipFiles.any {
                    it.name.contains("lohnsteuer", ignoreCase = true)
                }
                val lohnsteuerChitraFound = payslipFiles.any {
                    it.name.contains("chithra", ignoreCase = true)
                }

                // Kita
                val kitaFiles = countInSubfolder("Kita")
                val kitaCount = kitaFiles.size

                // Currentbills
                val utilityFiles = countInSubfolder("Currentbills")
                val utilityCount = utilityFiles.size

                // BankStatements
                val bankFiles = countInSubfolder("BankStatements")
                val bankCount = bankFiles.size

                // Donation
                val donationFiles = countInSubfolder("Donation")
                val donationCount = donationFiles.size

                // TAXATION_COMPLETE.json in year folder
                val taxationComplete = yearFolderChildren.any {
                    it.name.equals("TAXATION_COMPLETE.json", ignoreCase = true)
                }

                // German Tax return Info xlsx in year folder
                val taxExcelFound = yearFolderChildren.any {
                    it.name.contains("german tax return info", ignoreCase = true) &&
                        (it.name.endsWith(".xlsx", ignoreCase = true) ||
                            it.mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
                            it.mimeType == "application/vnd.google-apps.spreadsheet")
                }

                val entity = TaxCheckEntity(
                    year = year,
                    driveIncomeTaxFolderId = incomeTaxFolderId,
                    payslipsFound = payslipsCount,
                    lohnsteuerFound = lohnsteuerFound,
                    lohnsteuerChitraFound = lohnsteuerChitraFound,
                    kitaDocsFound = kitaCount,
                    taxExcelFound = taxExcelFound,
                    bankStatementsFound = bankCount,
                    donationDocsFound = donationCount,
                    utilityBillsFound = utilityCount,
                    isComplete = taxationComplete,
                    lastSyncedAt = System.currentTimeMillis(),
                    syncStatus = "SYNCED"
                )

                taxCheckDao.upsert(entity)
                taxCheckDao.setDriveFolderId(year, incomeTaxFolderId)

                Result.success()
            } catch (e: Exception) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }
}
