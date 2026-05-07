package com.household.app.data.api

import com.household.app.ui.compose.state.Insight
import com.household.app.ui.compose.state.InsightPriority
import com.household.app.ui.compose.state.InsightType

data class InsightDto(
    val id: String,
    val type: String,
    val category: String,
    val priority: String,
    val title: String,
    val message: String,
    val action: String,
    val expiresAt: String? = null
)

fun InsightDto.toDomain(): Insight {
    return Insight(
        id = id,
        type = when (type.uppercase()) {
            "WARNING" -> InsightType.WARNING
            "SUCCESS" -> InsightType.SUCCESS
            else -> InsightType.INFO
        },
        category = category,
        priority = when (priority.uppercase()) {
            "HIGH" -> InsightPriority.HIGH
            "MEDIUM" -> InsightPriority.MEDIUM
            else -> InsightPriority.LOW
        },
        title = title,
        message = message,
        action = action,
        expiresAt = expiresAt
    )
}
