package com.household.app.data.repository

import com.household.app.data.api.InsightDto
import com.household.app.data.api.toDomain
import com.household.app.domain.repositories.InsightRepository
import com.household.app.ui.compose.state.Insight

class InsightRepositoryImpl(
    private val fetchInsights: suspend () -> List<InsightDto>
) : InsightRepository {
    override suspend fun getInsights(): Result<List<Insight>> {
        return runCatching {
            fetchInsights().map { it.toDomain() }
        }
    }
}
