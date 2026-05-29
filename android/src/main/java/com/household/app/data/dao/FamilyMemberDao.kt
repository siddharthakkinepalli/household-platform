package com.household.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.household.app.data.entities.DocumentEntity
import com.household.app.data.entities.FamilyMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyMemberDao {

    @Query("SELECT * FROM family_members ORDER BY createdAt DESC")
    fun getAllMembers(): Flow<List<FamilyMemberEntity>>

    @Query("SELECT * FROM family_members WHERE id = :id")
    suspend fun getMemberById(id: Long): FamilyMemberEntity?

    @Query("SELECT * FROM family_members WHERE role = :role ORDER BY name ASC")
    fun getMembersByRole(role: String): Flow<List<FamilyMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: FamilyMemberEntity): Long

    @Update
    suspend fun updateMember(member: FamilyMemberEntity): Int

    @Delete
    suspend fun deleteMember(member: FamilyMemberEntity): Int

    @Query("DELETE FROM family_members WHERE id = :id")
    suspend fun deleteMemberById(id: Long): Int

    @Query("SELECT COUNT(*) FROM family_members")
    fun getMembersCount(): Flow<Int>

    @Query("SELECT * FROM family_members ORDER BY createdAt DESC")
    suspend fun getAllMembersList(): List<FamilyMemberEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembers(members: List<FamilyMemberEntity>): List<Long>
}

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY CASE WHEN expiryDate IS NULL THEN 1 ELSE 0 END, expiryDate ASC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE ownerId = :ownerId ORDER BY expiryDate ASC")
    fun getDocumentsForOwner(ownerId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE type = :type ORDER BY expiryDate ASC")
    fun getDocumentsByType(type: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE expiryDate IS NOT NULL AND expiryDate <= :thresholdTime ORDER BY expiryDate ASC")
    fun getExpiringDocuments(thresholdTime: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE expiryDate IS NULL OR expiryDate > :thresholdTime ORDER BY title ASC")
    fun getValidDocuments(thresholdTime: Long): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity): Int

    @Delete
    suspend fun deleteDocument(document: DocumentEntity): Int

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long): Int

    @Query("SELECT COUNT(*) FROM documents")
    fun getDocumentsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents WHERE expiryDate IS NOT NULL AND expiryDate <= :thresholdTime")
    fun getExpiringDocumentsCount(thresholdTime: Long): Flow<Int>

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    suspend fun getAllDocumentsList(): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDocuments(documents: List<DocumentEntity>): List<Long>

    @Query("SELECT * FROM documents WHERE expiryDate IS NOT NULL AND expiryDate BETWEEN :now AND :future ORDER BY expiryDate ASC")
    fun getDocumentsExpiringSoon(now: Long, future: Long): Flow<List<DocumentEntity>>
}