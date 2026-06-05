package com.household.app.feature.assistant.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val isLoading: Boolean = false
) {
    enum class Role { USER, ASSISTANT }
}
