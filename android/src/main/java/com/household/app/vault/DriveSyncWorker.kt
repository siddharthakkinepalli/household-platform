package com.household.app.vault

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class DriveSyncWorker(
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

        val compressed = compressImage(entity.imagePath) ?: return Result.failure()

        return withContext(Dispatchers.IO) {
            try {
                val credential = GoogleAccountCredential
                    .usingOAuth2(applicationContext, listOf(DriveScopes.DRIVE_FILE))
                    .apply { selectedAccount = account.account }

                val drive = Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("HouseholdPlatform").build()

                val folderId = getOrCreateJugaadFolder(drive)

                val metadata = DriveFile().apply {
                    name = "receipt_${vaultId}_${System.currentTimeMillis()}.jpg"
                    parents = listOf(folderId)
                }

                drive.files().create(
                    metadata,
                    ByteArrayContent("image/jpeg", compressed)
                ).setFields("id").execute()

                Result.success()
            } catch (e: Exception) {
                return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }

    private suspend fun getOrCreateJugaadFolder(drive: Drive): String {
        val cached = DriveDataStore.getJugaadFolderId(applicationContext)
        if (cached != null) return cached

        // Look for a Jugaad folder this app already created
        val existing = drive.files().list()
            .setQ("name = 'Jugaad' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
            .files
            .firstOrNull()

        val folderId = existing?.id ?: run {
            val meta = DriveFile().apply {
                name = "Jugaad"
                mimeType = "application/vnd.google-apps.folder"
            }
            drive.files().create(meta).setFields("id").execute().id
        }

        DriveDataStore.setJugaadFolderId(applicationContext, folderId)
        return folderId
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
