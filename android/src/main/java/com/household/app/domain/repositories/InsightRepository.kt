package com.household.app.domain.repositories

import com.household.app.ui.compose.state.Insight

interface InsightRepository {
    suspend fun getInsights(): Result<List<Insight>>
}
