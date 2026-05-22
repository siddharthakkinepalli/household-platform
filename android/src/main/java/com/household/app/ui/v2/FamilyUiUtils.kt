package com.household.app.ui.v2

import androidx.compose.ui.graphics.Color
import com.household.app.data.entities.FamilyMemberEntity

fun FamilyMemberEntity.initial(): String =
    name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

fun parseMemberColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color(0xFFFBBF24))
