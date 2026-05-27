package com.household.app.vault.workers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.household.app.data.AppDatabase
import com.household.app.data.entities.VaultEntity
import com.household.app.vault.DriveAuthManager
import com.household.app.vault.DriveDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Calendar

class DriveTaxRouteWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val vaultId = inputData.getLong("vault_id", -1L)
        if (vaultId < 0) return Result.failure()

        val entity = withContext(Dispatchers.IO) {
            AppDatabase.getInstance(applicationContext).vaultDao().getEntryById(vaultId)
        } ?: return Result.failure()

        val account = DriveAuthManager(applicationContext).lastSignedInAccount()
            ?: return Result.retry()

        val incomeTaxFolderId = DriveDataStore.getIncomeTaxFolderId(applicationContext)
            ?: return Result.failure()

        val compressed = compressImage(entity.imagePath) ?: return Result.failure()

        return withContext(Dispatchers.IO) {
            try {
                val credential = GoogleAccountCredential
                    .usingOAuth2(
                        applicationContext,
                        listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE)
                    )
                    .apply { selectedAccount = account.account }

                val drive = Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("HouseholdPlatform").build()

                val currentYear = Calendar.getInstance().get(Calendar.YEAR).toString()
                val yearFolderId = getOrCreateSubfolder(drive, incomeTaxFolderId, currentYear)

                val subfolder = classifySubfolder(entity)
                val targetFolderId = getOrCreateSubfolder(drive, yearFolderId, subfolder)

                val fileName = (entity.documentTitle?.takeIf { it.isNotBlank() }
                    ?: "doc_${vaultId}") + "_${System.currentTimeMillis()}.jpg"

                val metadata = DriveFile().apply {
                    name = fileName
                    parents = listOf(targetFolderId)
                }

                drive.files().create(
                    metadata,
                    ByteArrayContent("image/jpeg", compressed)
                ).setFields("id").execute()

                Result.success()
            } catch (e: Exception) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }

    private fun classifySubfolder(entry: VaultEntity): String {
        val text = (entry.rawOcrContent + " " + entry.documentTitle.orEmpty()).lowercase()
        return when {
            text.contains("brutto") || text.contains("netto") || text.contains("lohnsteuer")
                || text.contains("payslip") -> "Payslips"
            text.contains("kita") || text.contains("betreuung") || text.contains("childcare") -> "Kita"
            text.contains("strom") || text.contains("gas") || text.contains("wasser")
                || text.contains("telekom") || text.contains("vodafone") -> "Currentbills"
            text.contains("spende") || text.contains("donation") -> "Donation"
            entry.category == "RECEIPT" -> "BankStatements"
            else -> "BankStatements"
        }
    }

    private fun getOrCreateSubfolder(drive: Drive, parentId: String, name: String): String {
        val existing = drive.files().list()
            .setQ("'$parentId' in parents and name = '$name' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
            .setFields("files(id)")
            .execute().files.firstOrNull()
        return existing?.id ?: drive.files().create(
            DriveFile().apply {
                this.name = name
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf(parentId)
            }
        ).setFields("id").execute().id
    }

    private fun compressImage(imagePath: String): ByteArray? {
        return try {
            val bitmap = when {
                imagePath.startsWith("content://") ->
                    applicationContext.contentResolver
                        .openInputStream(Uri.parse(imagePath))
                        ?.use { BitmapFactory.decodeStream(it) }
                else ->
                    BitmapFactory.decodeFile(imagePath)
            } ?: return null

            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }
}
