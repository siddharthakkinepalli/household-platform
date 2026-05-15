package com.household.app.data.repository

import com.household.app.data.dao.PantryDao
import com.household.app.data.entities.PantryEntity
import com.household.app.domain.models.PantryCategory
import com.household.app.domain.models.PantryItem
import com.household.app.domain.repositories.PantryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PantryRepositoryImpl(private val dao: PantryDao) : PantryRepository {

    override fun getStagedItems(vaultId: Long): Flow<List<PantryItem>> =
        dao.getStagedItemsByVault(vaultId).map { it.map(PantryEntity::toDomain) }

    override fun getConfirmedItems(): Flow<List<PantryItem>> =
        dao.getConfirmedItems().map { it.map(PantryEntity::toDomain) }

    override suspend fun stageParsedItems(items: List<PantryItem>) {
        dao.insertItems(items.map { it.toEntity(isConfirmed = false) })
    }

    override suspend fun confirmItems(selected: List<PantryItem>) {
        dao.insertItems(selected.map { it.toEntity(isConfirmed = true) })
    }

    override suspend fun deleteStagedForVault(vaultId: Long) {
        dao.deleteStagedForVault(vaultId)
    }
}

private fun PantryEntity.toDomain() = PantryItem(
    id = id,
    name = name,
    category = runCatching { PantryCategory.valueOf(category) }.getOrDefault(PantryCategory.OTHER),
    quantity = quantity,
    expiryEstimate = expiryEstimate,
    vaultId = vaultId
)

private fun PantryItem.toEntity(isConfirmed: Boolean) = PantryEntity(
    id = id,
    name = name,
    category = category.name,
    quantity = quantity,
    expiryEstimate = expiryEstimate,
    vaultId = vaultId,
    isConfirmed = isConfirmed
)
