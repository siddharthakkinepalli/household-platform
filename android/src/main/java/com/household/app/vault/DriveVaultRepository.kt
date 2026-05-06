package com.household.app.vault

import android.net.Uri

/**
 * Vault data contract: metadata should be cached locally for snappy vault rendering,
 * while full document bytes are fetched only when the user opens a file.
 */
interface DriveVaultRepository {
    suspend fun uploadDocument(localUri: Uri, mimeType: String): Result<VaultDocument>
    suspend fun listRecentDocuments(): Result<List<VaultDocument>>
    suspend fun fetchDocumentForView(documentId: String): Result<Uri>
}

data class VaultDocument(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val thumbnailUri: String? = null,
    val modifiedAt: String? = null
)
