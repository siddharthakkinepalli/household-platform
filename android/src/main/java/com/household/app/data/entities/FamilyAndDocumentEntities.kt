package com.household.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * FamilyMember - Household family members
 */
@Entity(
    tableName = "family_members",
    indices = [Index("role")]
)
data class FamilyMemberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val avatarPath: String? = null,
    val colorCode: String = "#FFC107",
    val role: String = "Member",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Document - Family documents, contracts, IDs
 */
@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = FamilyMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("ownerId"), Index("expiryDate")]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerId: Long? = null,
    val title: String,
    val type: DocumentType = DocumentType.OTHER,
    val expiryDate: Long? = null,
    val noticePeriodDays: Int = 30,
    val monthlyCost: Double? = null,
    val localUri: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class DocumentType {
    CONTRACT,      // Netflix, Insurance, Gym, etc.
    ID,            // Passport, Driver's License
    MEDICAL,       // Insurance card, prescriptions
    PROPERTY,      // Lease, deeds
    OTHER
}

/**
 * Document alert - for tracking upcoming expirations/cancellations
 */
@Entity(
    tableName = "document_alerts",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId"), Index("isAcknowledged")]
)
data class DocumentAlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val alertType: AlertType,
    val message: String,
    val daysUntil: Int,
    val isAcknowledged: Boolean = false,
    val actionTaken: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class AlertType {
    EXPIRY_WARNING,     // < 30 days to expiry
    PRICE_INCREASE,      // Price change detected
    AUTO_RENEWAL,       // Will auto-renew soon
    ACTION_REQUIRED     // Manual action needed
}