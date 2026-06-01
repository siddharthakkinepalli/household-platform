package com.household.app.ai

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point for accessing AI singletons from non-Hilt contexts:
 *   - Composables inside non-@HiltViewModel screens (DocumentDetailSheet)
 *   - Legacy Fragments without @AndroidEntryPoint
 *
 * Usage:
 *   val ep = EntryPointAccessors.fromApplication(context, AiEntryPoint::class.java)
 *   val enhancer = ep.documentEnhancer()
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AiEntryPoint {
    fun documentEnhancer(): AiDocumentEnhancer
    fun expenseCategorizer(): AiExpenseCategorizer
}
