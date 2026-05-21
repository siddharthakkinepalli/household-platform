package com.household.app.domain.repositories

import com.household.app.domain.models.PantryItem
import kotlinx.coroutines.flow.Flow

interface PantryRepository {
    fun getStagedItems(vaultId: Long): Flow<List<PantryItem>>
    fun getConfirmedItems(): Flow<List<PantryItem>>
    suspend fun stageParsedItems(items: List<PantryItem>)
    suspend fun confirmItems(selected: List<PantryItem>)
    suspend fun deleteStagedForVault(vaultId: Long)
    suspend fun consumeItem(pantryItemId: Long, quantity: Int = 1)
}
