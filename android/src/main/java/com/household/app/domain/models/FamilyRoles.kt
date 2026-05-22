package com.household.app.domain.models

object FamilyRoles {
    val ALL = listOf("Primary", "Adult", "Child", "Member", "Other")

    fun isAdult(role: String): Boolean =
        role.equals("Primary", ignoreCase = true) ||
            role.equals("Adult", ignoreCase = true) ||
            role.equals("Member", ignoreCase = true)

    fun isChild(role: String): Boolean = role.equals("Child", ignoreCase = true)
}
