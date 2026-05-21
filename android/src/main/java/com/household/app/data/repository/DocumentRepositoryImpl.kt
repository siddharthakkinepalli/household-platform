package com.household.app.data.repository

import com.household.app.data.dao.DocumentAlertDao
import com.household.app.data.dao.DocumentDao
import com.household.app.data.entities.DocumentAlertEntity
import com.household.app.data.entities.DocumentEntity
import kotlinx.coroutines.flow.Flow

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

    suspend fun insertDocument(doc: DocumentEntity): Long =
        documentDao.insertDocument(doc)

    suspend fun deleteDocument(id: Long) =
        documentDao.deleteDocumentById(id)

    fun getUnacknowledgedAlerts(): Flow<List<DocumentAlertEntity>> =
        documentAlertDao.getUnacknowledgedAlerts()
}
