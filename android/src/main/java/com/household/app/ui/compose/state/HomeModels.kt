package com.household.app.ui.compose.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

// ── Insight domain model ──────────────────────────────────────────────────

enum class InsightType { WARNING, INFO, SUCCESS }

enum class InsightPriority {
    HIGH,
    MEDIUM,
    LOW
}

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
    val priority: InsightPriority,
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

// ── Activity stream items ─────────────────────────────────────────────────

@Stable
sealed class ActivityItem {
    abstract val epochMs: Long

    @Immutable
    data class Scan(
        val id: Long,
        val merchant: String,
        val amount: Double?,
        val currency: String,
        override val epochMs: Long
    ) : ActivityItem()

    @Immutable
    data class Spend(
        val id: Int,
        val description: String,
        val amount: Double,
        val category: String,
        override val epochMs: Long
    ) : ActivityItem()
}

// ── Per-category budget model ─────────────────────────────────────────────

@Immutable
data class CategoryBudget(
    val id: String,
    val name: String,
    val spent: Double,
    val limit: Double,
    val color: Color
)

// ── HomeScreen MVI ────────────────────────────────────────────────────────

@Immutable
data class HomeState(
    val userName: String = "Siddharth",
    val balanceValue: Double = 0.0,
    val balanceFormatted: String = "€0.00",
    val deltaPercent: Float = 0f,
    val insights: List<Insight> = emptyList(),
    val unlinkedVaultCount: Int = 0,
    val recentActivity: List<ActivityItem> = emptyList(),
    val salaryAnchorDay: Int = 25,
    val loading: Boolean = false,
    val error: String? = null,
    val categoryBudgets: List<CategoryBudget> = emptyList()
)

sealed class HomeIntent {
    object Load : HomeIntent()
    object RefreshInsights : HomeIntent()
    data class DismissInsight(val id: String) : HomeIntent()
}
