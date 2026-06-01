package com.jugaad.feature.astro.domain.model

enum class LifeEventCategory(val label: String, val emoji: String) {
    CONTRACT_SIGNING("Sign Contract", "📝"),
    NEW_HABIT       ("New Habit",     "🌱"),
    TRAVEL          ("Travel",        "✈️"),
    LARGE_INVESTMENT("Investment",    "💰"),
    CAREER_CHANGE   ("Career Move",   "📈")
}

enum class Verdict { GO, CAUTION, NO_GO }

data class EventAssessment(
    val category: LifeEventCategory,
    val score: Int,                      // 0–100
    val verdict: Verdict,
    val astroIndicator: String,          // e.g. "Mercury Retrograde — read fine print"
    val numerologyIndicator: String,     // e.g. "Personal Day 5 — instability"
    val advice: String,                  // actionable one-liner
    val bestWindowHint: String?          // e.g. "Best window: 9:00 AM–10:00 AM  Jupiter Hora"
)
