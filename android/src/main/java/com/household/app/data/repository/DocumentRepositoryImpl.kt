package com.household.app.data.repository

import com.household.app.data.dao.DocumentAlertDao
import com.household.app.data.dao.DocumentDao
import com.household.app.data.entities.AlertType
import com.household.app.data.entities.DocumentAlertEntity
import com.household.app.data.entities.DocumentEntity
import kotlinx.coroutines.flow.Flow
import java.time.temporal.ChronoUnit
import java.time.Instant

class DocumentRepositoryImpl(
    private val documentDao: DocumentDao,
    private val documentAlertDao: DocumentAlertDao
) {
    fun getAllDocuments(): Flow<List<DocumentEntity>> =
        documentDao.getAllDocuments()

    fun getDocumentsExpiringSoon(withinDays: Int = 30): Flow<List<DocumentEntity>> {
        val now = System.currentTimeMillis()
        val future = now + withinDays.toLong() * 24 * 60 * 60 * 1000
        return documentDao.getDocumentsExpiringSoon(now = now, future = future)
    }

    suspend fun insertDocument(doc: DocumentEntity): Long {
        val id = documentDao.insertDocument(doc)
        createAlertIfExpiring(id, doc)
        return id
    }

    suspend fun updateDocument(doc: DocumentEntity) {
        documentDao.updateDocument(doc)
        documentAlertDao.deleteAlertsForDocument(doc.id)
        createAlertIfExpiring(doc.id, doc)
    }

    suspend fun deleteDocument(id: Long) =
        documentDao.deleteDocumentById(id)

    fun getUnacknowledgedAlerts(): Flow<List<DocumentAlertEntity>> =
        documentAlertDao.getUnacknowledgedAlerts()

    private suspend fun createAlertIfExpiring(documentId: Long, doc: DocumentEntity) {
        val expiryEpoch = doc.expiryDate ?: return
        val now = Instant.now()
        val expiry = Instant.ofEpochMilli(expiryEpoch)
        val daysUntil = ChronoUnit.DAYS.between(now, expiry).toInt()
        // Only create alert if document expires within noticePeriodDays from now
        if (daysUntil <= doc.noticePeriodDays) {
            val alertType = when {
                daysUntil < 0 -> AlertType.ACTION_REQUIRED
                daysUntil <= 7 -> AlertType.EXPIRY_WARNING
                else -> AlertType.EXPIRY_WARNING
            }
            val message = when {
                daysUntil < 0 -> "${doc.title} expired ${-daysUntil} day${if (-daysUntil == 1) "" else "s"} ago"
                daysUntil == 0 -> "${doc.title} expires today"
                daysUntil == 1 -> "${doc.title} expires tomorrow"
                else -> "${doc.title} expires in $daysUntil days"
            }
            documentAlertDao.insertAlert(
                DocumentAlertEntity(
                    documentId = documentId,
                    alertType = alertType,
                    message = message,
                    daysUntil = daysUntil,
                    isAcknowledged = false,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }
}
