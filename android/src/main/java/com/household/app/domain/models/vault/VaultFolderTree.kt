package com.household.app.domain.models.vault

import com.household.app.data.entities.FamilyMemberEntity
import com.household.app.data.entities.VaultEntity

/** Where the user is in the vault folder browser. */
sealed class VaultBrowseState {
    data object Root : VaultBrowseState()
    data class Category(val category: VaultCategory) : VaultBrowseState()
    /** Picking person under a category (Identity, Contracts, …). */
    data class MemberScope(
        val category: VaultCategory,
        val ownerMemberId: Long?,
        val memberLabel: String
    ) : VaultBrowseState()
    data class Folder(val path: VaultFolderPath) : VaultBrowseState()
}

data class VaultFolderRow(
    val title: String,
    val subtitle: String,
    val itemCount: Int,
    val target: VaultBrowseState
)

object VaultFolderTree {

    const val HOUSEHOLD_LABEL = "Household"

    fun categoryUsesMemberLevel(category: VaultCategory): Boolean =
        category != VaultCategory.RECEIPT

    fun entryCategory(entry: VaultEntity): VaultCategory =
        runCatching { VaultCategory.valueOf(entry.category) }.getOrDefault(VaultCategory.OTHER)

    fun filterEntries(
        entries: List<VaultEntity>,
        browse: VaultBrowseState
    ): List<VaultEntity> = when (browse) {
        is VaultBrowseState.Root -> emptyList()
        is VaultBrowseState.Category -> {
            if (categoryUsesMemberLevel(browse.category)) emptyList()
            else entries.filter { entryCategory(it) == browse.category }
        }
        is VaultBrowseState.MemberScope -> emptyList()
        is VaultBrowseState.Folder -> entries.filter { browse.path.matches(it) }
    }

    fun rowsAt(
        browse: VaultBrowseState,
        entries: List<VaultEntity>,
        members: List<FamilyMemberEntity>
    ): List<VaultFolderRow> = when (browse) {
        is VaultBrowseState.Root -> rootRows(entries)
        is VaultBrowseState.Category -> categoryMemberRows(browse.category, entries, members)
        is VaultBrowseState.MemberScope -> memberSubfolderRows(browse, entries)
        is VaultBrowseState.Folder -> emptyList()
    }

    private fun rootRows(entries: List<VaultEntity>): List<VaultFolderRow> =
        VaultCategory.entries.map { cat ->
            val count = entries.count { entryCategory(it) == cat }
            VaultFolderRow(
                title = "${cat.emoji} ${cat.label}",
                subtitle = when {
                    count == 0 -> "Empty"
                    categoryUsesMemberLevel(cat) -> "$count docs · tap to browse"
                    else -> "$count docs"
                },
                itemCount = count,
                target = VaultBrowseState.Category(cat)
            )
        }

    private fun categoryMemberRows(
        category: VaultCategory,
        entries: List<VaultEntity>,
        members: List<FamilyMemberEntity>
    ): List<VaultFolderRow> {
        if (!categoryUsesMemberLevel(category)) return emptyList()

        val inCategory = entries.filter { entryCategory(it) == category }
        val rows = mutableListOf<VaultFolderRow>()

        val householdCount = inCategory.count { it.ownerMemberId == null }
        rows += VaultFolderRow(
            title = HOUSEHOLD_LABEL,
            subtitle = if (householdCount == 0) "No documents yet" else "$householdCount documents",
            itemCount = householdCount,
            target = VaultBrowseState.MemberScope(category, null, HOUSEHOLD_LABEL)
        )

        members.forEach { member ->
            val count = inCategory.count { it.ownerMemberId == member.id }
            rows += VaultFolderRow(
                title = member.name,
                subtitle = if (count == 0) member.role else "$count documents",
                itemCount = count,
                target = VaultBrowseState.MemberScope(category, member.id, member.name)
            )
        }

        return rows
    }

    private fun memberSubfolderRows(
        scope: VaultBrowseState.MemberScope,
        entries: List<VaultEntity>
    ): List<VaultFolderRow> {
        val inScope = entries.filter { e ->
            entryCategory(e) == scope.category && e.ownerMemberId == scope.ownerMemberId
        }
        val subfolders = VaultSubFolder.forCategory(scope.category)
        return subfolders.map { sub ->
            val count = inScope.count { VaultSubFolder.normalizeId(it.subFolder) == sub.id }
            VaultFolderRow(
                title = sub.label,
                subtitle = scope.memberLabel,
                itemCount = count,
                target = VaultBrowseState.Folder(
                    VaultFolderPath(scope.category, scope.ownerMemberId, sub.id)
                )
            )
        } + VaultFolderRow(
            title = VaultSubFolder.UNFILED.label,
            subtitle = scope.memberLabel,
            itemCount = inScope.count {
                VaultSubFolder.normalizeId(it.subFolder) == VaultSubFolder.UNFILED.id
            },
            target = VaultBrowseState.Folder(
                VaultFolderPath(scope.category, scope.ownerMemberId, VaultSubFolder.UNFILED.id)
            )
        )
    }

    fun breadcrumb(
        browse: VaultBrowseState,
        members: List<FamilyMemberEntity>
    ): List<Pair<String, VaultBrowseState?>> = when (browse) {
        is VaultBrowseState.Root -> listOf("Documents" to null)
        is VaultBrowseState.Category -> listOf(
            "Documents" to VaultBrowseState.Root,
            browse.category.label to null
        )
        is VaultBrowseState.MemberScope -> listOf(
            "Documents" to VaultBrowseState.Root,
            browse.category.label to VaultBrowseState.Category(browse.category),
            browse.memberLabel to null
        )
        is VaultBrowseState.Folder -> {
            val memberLabel = browse.path.ownerMemberId?.let { id ->
                members.firstOrNull { it.id == id }?.name
            } ?: HOUSEHOLD_LABEL
            listOf(
                "Documents" to VaultBrowseState.Root,
                browse.path.category.label to VaultBrowseState.Category(browse.path.category),
                memberLabel to VaultBrowseState.MemberScope(
                    browse.path.category,
                    browse.path.ownerMemberId,
                    memberLabel
                ),
                browse.path.subFolderEnum.label to null
            )
        }
    }

    fun displayPath(entry: VaultEntity, members: List<FamilyMemberEntity>): String {
        val path = VaultFolderPath.fromEntry(entry)
        val member = path.ownerMemberId?.let { id -> members.firstOrNull { it.id == id }?.name }
            ?: HOUSEHOLD_LABEL
        return "${path.category.label} › $member › ${path.subFolderEnum.label}"
    }
}
