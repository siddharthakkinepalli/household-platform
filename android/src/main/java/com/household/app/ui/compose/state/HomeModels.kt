package com.household.app.ui.compose.state

import androidx.compose.runtime.Immutable

// ── Insight domain model ──────────────────────────────────────────────────

enum class InsightType { WARNING, INFO, SUCCESS }

/**
 * Domain model for a single insight card.
 * Produced by n8n workflows → backend /api/v1/insights → InsightRepositoryImpl.
 * @Immutable tells Compose it can skip recomposition when the reference hasn't changed.
 */
@Immutable
data class Insight(
    val id: String,
    val type: InsightType,
    val category: String,
    val title: String,
    val message: String,
    /** Maps to a Screen.route value for deep-link navigation on tap. */
    val action: String = "",
    /** ISO-8601. If non-null, insight is auto-dismissed after this time. */
    val expiresAt: String? = null
)

// ── Module domain model ───────────────────────────────────────────────────

@Immutable
data class Module(
    val id: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val subtitle: String = "",
    /** Maps to Screen.route */
    val route: String
)

// ── HomeScreen MVI ────────────────────────────────────────────────────────

@Immutable
data class HomeState(
    val balanceFormatted: String = "€0.00",
    val deltaPercent: Float = 0f,
    val insights: List<Insight> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

sealed class HomeIntent {
    object Load : HomeIntent()
    object RefreshInsights : HomeIntent()
    data class DismissInsight(val id: String) : HomeIntent()
}
