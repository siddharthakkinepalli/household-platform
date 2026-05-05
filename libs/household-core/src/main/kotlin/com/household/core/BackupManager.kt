package com.household.core

import java.io.File

/**
 * Interface for managing household data backups and restoration.
 * Implementations handle local backup/restore operations only.
 * Cloud sync is handled separately by higher-level modules.
 */
interface BackupManager {
    
    /**
     * Creates a backup of household data to the specified file.
     * Data is automatically encrypted if backupEncrypted is true.
     *
     * @param householdId The household ID to backup
     * @param backupFile The destination file for the backup
     * @param encrypted Whether to encrypt the backup data
     * @return BackupResult with success status and metadata
     */
    suspend fun createBackup(
        householdId: String,
        backupFile: File,
        encrypted: Boolean = true
    ): BackupResult
    
    /**
     * Restores household data from a backup file.
     * Automatically detects and decrypts if the backup is encrypted.
     *
     * @param householdId The household ID to restore to
     * @param backupFile The source backup file
     * @return BackupResult with success status and metadata
     */
    suspend fun restoreBackup(
        householdId: String,
        backupFile: File
    ): BackupResult
    
    /**
     * Lists all available backup files for a household.
     *
     * @param householdId The household ID
     * @return List of backup file metadata
     */
    suspend fun listBackups(householdId: String): List<BackupMetadata>
    
    /**
     * Deletes a backup file.
     *
     * @param backupFile The file to delete
     * @return Whether deletion was successful
     */
    suspend fun deleteBackup(backupFile: File): Boolean
}

/**
 * Result of a backup or restore operation
 */
data class BackupResult(
    val success: Boolean,
    val householdId: String,
    val backupSize: Long = 0,
    val timestamp: String = "",
    val errorMessage: String? = null,
    val encrypted: Boolean = false
)

/**
 * Metadata about a backup file
 */
data class BackupMetadata(
    val filePath: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val createdAt: String,
    val isEncrypted: Boolean,
    val householdId: String
)
