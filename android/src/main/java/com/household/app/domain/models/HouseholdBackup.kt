package com.household.app.domain.models

import com.household.app.data.entities.CategoryThresholdEntity
import com.household.app.data.entities.DocumentAlertEntity
import com.household.app.data.entities.DocumentEntity
import com.household.app.data.entities.FamilyMemberEntity
import com.household.app.data.entities.InventoryEventEntity
import com.household.app.data.entities.MerchantRuleEntity
import com.household.app.data.entities.PantryEntity
import com.household.app.data.entities.VaultEntity
import com.household.app.data.entities.WalletTransactionEntity

data class HouseholdBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val walletTransactions: List<WalletTransactionEntity> = emptyList(),
    val vaultEntries: List<VaultEntity> = emptyList(),
    val pantryItems: List<PantryEntity> = emptyList(),
    val inventoryEvents: List<InventoryEventEntity> = emptyList(),
    val merchantRules: List<MerchantRuleEntity> = emptyList(),
    val categoryThresholds: List<CategoryThresholdEntity> = emptyList(),
    val familyMembers: List<FamilyMemberEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList(),
    val documentAlerts: List<DocumentAlertEntity> = emptyList()
)
