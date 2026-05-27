package com.household.app.vault.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.household.app.vault.DriveAuthManager
import com.household.app.vault.DriveDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class BankStatementDriveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val fileName = inputData.getString("file_name") ?: return Result.failure()
        val contentSummary = inputData.getString("content_summary") ?: return Result.failure()

        val account = DriveAuthManager(applicationContext).lastSignedInAccount()
            ?: return Result.retry()

        val incomeTaxFolderId = DriveDataStore.getIncomeTaxFolderId(applicationContext)
            ?: return Result.failure()

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
                val bankStatementsFolderId = getOrCreateSubfolder(drive, yearFolderId, "BankStatements")

                val contentBytes = contentSummary.toByteArray(Charsets.UTF_8)
                val metadata = DriveFile().apply {
                    name = fileName
                    parents = listOf(bankStatementsFolderId)
                }

                drive.files().create(
                    metadata,
                    ByteArrayContent("text/plain", contentBytes)
                ).setFields("id").execute()

                Result.success()
            } catch (e: Exception) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
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
}
