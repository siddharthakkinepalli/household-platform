package com.household.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.household.app.data.AppDatabase
import com.household.app.data.entities.DocumentEntity
import com.household.app.data.entities.FamilyMemberEntity
import com.household.app.data.entities.VaultEntity
import com.household.app.domain.models.FamilyRoles
import com.household.app.domain.models.vault.VaultFolderTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class FamilyMemberUi(
    val member: FamilyMemberEntity,
    val vaultDocCount: Int,
    val contractDocCount: Int
)

data class FamilySummary(
    val total: Int = 0,
    val adults: Int = 0,
    val children: Int = 0
)

data class FamilyExpiryAlert(
    val documentId: Long,
    val title: String,
    val ownerId: Long?,
    val memberName: String?,
    val daysUntil: Int
)

data class FamilyMemberDetailUi(
    val memberUi: FamilyMemberUi,
    val contracts: List<DocumentEntity>,
    val vaultDocs: List<VaultEntity>,
    val expiringSoon: List<DocumentEntity>
)

class FamilyViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val memberDao = db.familyMemberDao()
    private val vaultDao = db.vaultDao()
    private val documentDao = db.documentDao()

    private val membersRaw = memberDao.getAllMembers()
    private val vaultFlow = vaultDao.getAllEntries()
    private val documentsFlow = documentDao.getAllDocuments()

    val members: StateFlow<List<FamilyMemberUi>> = combine(
        membersRaw,
        vaultFlow,
        documentsFlow
    ) { members, vault, documents ->
        members.map { it.toUi(vault, documents) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val memberCount: StateFlow<Int> = membersRaw
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val summary: StateFlow<FamilySummary> = members.map { list ->
        FamilySummary(
            total = list.size,
            adults = list.count { FamilyRoles.isAdult(it.member.role) },
            children = list.count { FamilyRoles.isChild(it.member.role) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FamilySummary())

    val expiringAlerts: StateFlow<List<FamilyExpiryAlert>> = combine(
        documentsFlow,
        membersRaw
    ) { documents, members ->
        val now = System.currentTimeMillis()
        val horizon = now + TimeUnit.DAYS.toMillis(30)
        val nameById = members.associate { it.id to it.name }
        documents
            .filter { doc ->
                val exp = doc.expiryDate ?: return@filter false
                exp in now..horizon
            }
            .map { doc ->
                val days = ((doc.expiryDate!! - now) / TimeUnit.DAYS.toMillis(1)).toInt().coerceAtLeast(0)
                FamilyExpiryAlert(
                    documentId = doc.id,
                    title = doc.title,
                    ownerId = doc.ownerId,
                    memberName = doc.ownerId?.let { nameById[it] },
                    daysUntil = days
                )
            }
            .sortedBy { it.daysUntil }
            .take(8)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeMemberDetail(memberId: Long): StateFlow<FamilyMemberDetailUi?> = combine(
        membersRaw,
        vaultFlow,
        documentsFlow
    ) { members, vault, documents ->
        val member = members.firstOrNull { it.id == memberId } ?: return@combine null
        val memberUi = member.toUi(vault, documents)
        val now = System.currentTimeMillis()
        val horizon = now + TimeUnit.DAYS.toMillis(30)
        val owned = documents.filter { it.ownerId == memberId }
        FamilyMemberDetailUi(
            memberUi = memberUi,
            contracts = owned.sortedBy { it.expiryDate ?: Long.MAX_VALUE },
            vaultDocs = vault.filter { it.ownerMemberId == memberId }
                .sortedByDescending { it.dateEpoch },
            expiringSoon = owned.filter { doc ->
                val exp = doc.expiryDate ?: return@filter false
                exp in now..horizon
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addMember(name: String, role: String, colorCode: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            memberDao.insertMember(
                FamilyMemberEntity(name = trimmed, role = role, colorCode = colorCode)
            )
        }
    }

    fun updateMember(member: FamilyMemberEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            memberDao.updateMember(member)
        }
    }

    fun deleteMember(memberId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultDao.clearOwnerFromEntries(memberId)
            memberDao.deleteMemberById(memberId)
        }
    }

    fun addContract(document: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            documentDao.insertDocument(document)
        }
    }

    fun deleteContract(documentId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            documentDao.deleteDocumentById(documentId)
        }
    }

    suspend fun getMember(memberId: Long): FamilyMemberEntity? = withContext(Dispatchers.IO) {
        memberDao.getMemberById(memberId)
    }

    private fun FamilyMemberEntity.toUi(
        vault: List<VaultEntity>,
        documents: List<DocumentEntity>
    ) = FamilyMemberUi(
        member = this,
        vaultDocCount = vault.count { it.ownerMemberId == id },
        contractDocCount = documents.count { it.ownerId == id }
    )
}
