package com.household.app.domain.models.vault

import com.household.app.data.entities.FamilyMemberEntity
import com.household.app.data.entities.VaultEntity

/**
 * Virtual folder path: top category → person (or household) → document type.
 */
data class VaultFolderPath(
    val category: VaultCategory,
    val ownerMemberId: Long? = null,
    val subFolder: String = VaultSubFolder.UNFILED.id
) {
    val subFolderEnum: VaultSubFolder get() = VaultSubFolder.fromId(subFolder)

    fun matches(entry: VaultEntity): Boolean {
        val entryCategory = runCatching { VaultCategory.valueOf(entry.category) }
            .getOrDefault(VaultCategory.OTHER)
        if (entryCategory != category) return false
        if (entry.ownerMemberId != ownerMemberId) return false
        return VaultSubFolder.normalizeId(entry.subFolder) == VaultSubFolder.normalizeId(subFolder)
    }

    companion object {
        fun fromEntry(entry: VaultEntity): VaultFolderPath {
            val category = runCatching { VaultCategory.valueOf(entry.category) }
                .getOrDefault(VaultCategory.OTHER)
            return VaultFolderPath(
                category = category,
                ownerMemberId = entry.ownerMemberId,
                subFolder = VaultSubFolder.normalizeId(entry.subFolder)
            )
        }

        fun household(category: VaultCategory, subFolder: VaultSubFolder) =
            VaultFolderPath(category, ownerMemberId = null, subFolder = subFolder.id)

        fun member(category: VaultCategory, memberId: Long, subFolder: VaultSubFolder) =
            VaultFolderPath(category, ownerMemberId = memberId, subFolder = subFolder.id)
    }
}
