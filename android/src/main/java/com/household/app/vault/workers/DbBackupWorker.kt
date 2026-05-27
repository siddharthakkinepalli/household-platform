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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DbBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val account = DriveAuthManager(applicationContext).lastSignedInAccount()
            ?: return@withContext Result.retry()

        val jugaadFolderId = DriveDataStore.getJugaadFolderId(applicationContext)
            ?: return@withContext Result.failure()

        try {
            val dbFile = applicationContext.getDatabasePath("household_app.db")
            if (!dbFile.exists()) return@withContext Result.failure()

            val dbBytes = dbFile.readBytes()
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(Date())
            val fileName = "household_app_backup_$timestamp.db"

            val credential = GoogleAccountCredential
                .usingOAuth2(applicationContext, listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE))
                .apply { selectedAccount = account.account }

            val drive = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("HouseholdPlatform").build()

            // Find or create a "DB Backups" subfolder inside Jugaad
            val backupFolderId = getOrCreateFolder(drive, "DB Backups", jugaadFolderId)

            // Prune old backups — keep only last 3
            val existing = drive.files().list()
                .setQ("'$backupFolderId' in parents and trashed = false and name contains 'household_app_backup'")
                .setOrderBy("createdTime")
                .setFields("files(id, name)")
                .execute().files ?: emptyList()
            if (existing.size >= 3) {
                existing.take(existing.size - 2).forEach { f ->
                    runCatching { drive.files().delete(f.id).execute() }
                }
            }

            // Upload new backup
            val meta = DriveFile().apply {
                name = fileName
                parents = listOf(backupFolderId)
            }
            drive.files()
                .create(meta, ByteArrayContent("application/octet-stream", dbBytes))
                .setFields("id")
                .execute()

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun getOrCreateFolder(drive: Drive, name: String, parentId: String): String {
        val existing = drive.files().list()
            .setQ("'$parentId' in parents and mimeType = 'application/vnd.google-apps.folder' and name = '$name' and trashed = false")
            .setFields("files(id)")
            .execute().files
        if (!existing.isNullOrEmpty()) return existing[0].id

        val meta = DriveFile().apply {
            this.name = name
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf(parentId)
        }
        return drive.files().create(meta).setFields("id").execute().id
    }
}
