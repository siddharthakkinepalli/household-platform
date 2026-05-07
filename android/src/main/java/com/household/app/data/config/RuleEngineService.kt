package com.household.app.data.config

import com.household.app.data.entities.MerchantRuleEntity

object RuleEngineService {
    fun pickRule(merchantName: String, rules: List<MerchantRuleEntity>): MerchantRuleEntity? {
        val normalizedMerchant = normalizeMerchant(merchantName)
        return rules
            .asSequence()
            .filter { it.isEnabled }
            .mapNotNull { rule ->
                val normalizedPattern = normalizeMerchant(rule.merchantPattern)
                when {
                    normalizedPattern.isBlank() -> null
                    normalizedMerchant == normalizedPattern -> rule to 3
                    normalizedMerchant.contains(normalizedPattern) -> rule to 2
                    normalizedPattern.contains(normalizedMerchant) -> rule to 1
                    else -> null
                }
            }
            .sortedWith(
                compareByDescending<Pair<MerchantRuleEntity, Int>> { it.second }
                    .thenByDescending { it.first.priority }
                    .thenByDescending { it.first.updatedAt }
            )
            .map { it.first }
            .firstOrNull()
    }

    fun findCollisions(rules: List<MerchantRuleEntity>): Map<String, List<MerchantRuleEntity>> {
        val enabledRules = rules.filter { it.isEnabled }
        return enabledRules.associateWith { current ->
            enabledRules.filter { other ->
                other.merchantPattern != current.merchantPattern &&
                    normalizeMerchant(other.merchantPattern).contains(normalizeMerchant(current.merchantPattern))
            }
        }.filterValues { it.isNotEmpty() }.mapKeys { it.key.merchantPattern }
    }

    private fun normalizeMerchant(raw: String): String {
        return raw
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
    }
}