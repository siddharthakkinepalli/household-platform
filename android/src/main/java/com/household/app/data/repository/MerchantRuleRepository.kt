package com.household.app.data.repository

import com.household.app.data.dao.MerchantRuleDao
import com.household.app.data.entities.MerchantRuleEntity
import kotlinx.coroutines.flow.Flow

interface MerchantRuleRepository {
    fun getAllRules(): Flow<List<MerchantRuleEntity>>
    suspend fun getEnabledRules(): List<MerchantRuleEntity>
    suspend fun upsertRule(rule: MerchantRuleEntity)
    suspend fun deleteRule(pattern: String)
    suspend fun seedDefaultRules()
}

class MerchantRuleRepositoryImpl(
    private val dao: MerchantRuleDao
) : MerchantRuleRepository {

    override fun getAllRules(): Flow<List<MerchantRuleEntity>> = dao.observeAllRules()

    override suspend fun getEnabledRules(): List<MerchantRuleEntity> = dao.getEnabledRules()

    override suspend fun upsertRule(rule: MerchantRuleEntity) {
        dao.upsertRule(rule)
    }

    override suspend fun deleteRule(pattern: String) {
        dao.deleteRule(pattern)
    }

    override suspend fun seedDefaultRules() {
        val defaults = listOf(
            MerchantRuleEntity(
                merchantPattern = "paypal*db*",
                targetCategoryId = "Transport",
                isExclusion = false,
                isEnabled = true,
                priority = 10,
                updatedAt = System.currentTimeMillis().toString()
            ),
            MerchantRuleEntity(
                merchantPattern = "paypal*takeaway*",
                targetCategoryId = "Eat Out",
                isExclusion = false,
                isEnabled = true,
                priority = 10,
                updatedAt = System.currentTimeMillis().toString()
            ),
            MerchantRuleEntity(
                merchantPattern = "paypal*google*",
                targetCategoryId = "Shopping",
                isExclusion = false,
                isEnabled = true,
                priority = 10,
                updatedAt = System.currentTimeMillis().toString()
            )
        )
        defaults.forEach { rule ->
            // Only insert if the pattern doesn't already exist
            if (dao.getRule(rule.merchantPattern) == null) {
                dao.upsertRule(rule)
            }
        }
    }
}
