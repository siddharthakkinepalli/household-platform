package com.jugaad.feature.astro.domain.model

data class NumerologyResult(
    val lifePathNumber: Int,
    val personalYearNumber: Int,
    val personalMonthNumber: Int,
    val personalDayNumber: Int,
    val personalDayLabel: String,   // "Creativity & Expression"
    val personalDayBrief: String    // "Creative flow is strong today."
)
