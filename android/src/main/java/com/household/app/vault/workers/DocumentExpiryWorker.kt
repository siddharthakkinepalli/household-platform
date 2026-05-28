package com.household.app.vault.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fires notifications for documents expiring within 30/7/1 days.
 *
 * Enqueued by [PipelineManager.onDocumentUploaded] after a vault upload completes.
 */
class DocumentExpiryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // TODO: query document_alerts where daysUntil <= 30 and send notifications
        Result.success()
    }
}
