package com.household.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.household.app.data.entities.DocumentAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentAlertDao {

    @Query("SELECT * FROM document_alerts ORDER BY daysUntil ASC")
    fun getAllAlerts(): Flow<List<DocumentAlertEntity>>

    @Query("SELECT * FROM document_alerts WHERE isAcknowledged = 0 ORDER BY daysUntil ASC")
    fun getUnacknowledgedAlerts(): Flow<List<DocumentAlertEntity>>

    @Query("SELECT * FROM document_alerts WHERE daysUntil <= :thresholdDays ORDER BY daysUntil ASC")
    fun getAlertsDueWithinDays(thresholdDays: Int): Flow<List<DocumentAlertEntity>>

    @Query("SELECT * FROM document_alerts WHERE documentId = :documentId ORDER BY createdAt DESC")
    fun getAlertsForDocument(documentId: Long): Flow<List<DocumentAlertEntity>>

    @Query("SELECT * FROM document_alerts WHERE id = :id")
    suspend fun getAlertById(id: Long): DocumentAlertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: DocumentAlertEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<DocumentAlertEntity>)

    @Update
    suspend fun updateAlert(alert: DocumentAlertEntity)

    @Query("UPDATE document_alerts SET isAcknowledged = 1, actionTaken = :action WHERE id = :alertId")
    suspend fun acknowledgeAlert(alertId: Long, action: String? = null)

    @Query("UPDATE document_alerts SET isAcknowledged = 1 WHERE documentId = :documentId")
    suspend fun acknowledgeAllForDocument(documentId: Long)

    @Delete
    suspend fun deleteAlert(alert: DocumentAlertEntity)

    @Query("DELETE FROM document_alerts WHERE documentId = :documentId")
    suspend fun deleteAlertsForDocument(documentId: Long)

    @Query("SELECT COUNT(*) FROM document_alerts WHERE isAcknowledged = 0")
    fun getUnacknowledgedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM document_alerts WHERE daysUntil <= 7 AND isAcknowledged = 0")
    fun getUrgentCount(): Flow<Int>
}