package com.jugaad.feature.astro.ui.state

import com.jugaad.feature.astro.domain.model.EventAssessment
import com.jugaad.feature.astro.domain.model.LifeEventCategory
import com.jugaad.feature.astro.domain.model.NumerologyResult

/**
 * Fully immutable UI state for the Astro dashboard.
 *
 * Rules:
 *  - All fields are val. ViewModel rebuilds state via copy() — never mutates in place.
 *  - No computation logic lives here — only display-ready strings and primitives.
 *  - Screens consume this exclusively via collectAsStateWithLifecycle.
 */

sealed class AstroLoadState {
    object Idle    : AstroLoadState()
    object Loading : AstroLoadState()
    data class Error(val message: String) : AstroLoadState()
}

/**
 * Single planet's display-ready transit row.
 * All names are pre-resolved strings — composables do NOT index into arrays.
 */
data class PlanetDisplayRow(
    val planetId: Int,
    val name: String,          // "Sun", "Moon", …
    val signName: String,      // "Aries", "Taurus", …
    val nakshatraName: String,
    val pada: Int,             // 1–4
    val longitude: String,     // "120.3°"
    val retrograde: Boolean,
    val shadabalaScore: Int,   // 0–100
    val isInWar: Boolean,
    val warResult: String?     // "winner" / "loser" / null
)

/**
 * Panchanga (five-limb almanac) display block.
 */
data class PanchangaDisplay(
    val tithi: String,         // "Shukla Shashthi"
    val paksha: String,        // "Shukla"
    val nakshatra: String,
    val yoga: String,
    val karana: String,
    val vara: String           // weekday name
)

/**
 * Rahu Kaal window display values.
 * [progressFraction] in [0f, 1f]: 0 = window not started, 1 = window ended.
 * [isActive] = we are currently inside the Rahu Kaal window.
 */
data class RahuKaalDisplay(
    val startLabel: String,    // "16:00"
    val endLabel: String,      // "17:30"
    val progressFraction: Float,
    val isActive: Boolean
)

/**
 * Birth chart display block. Null until [ComputeBirthChartUseCase] completes.
 */
data class BirthChartDisplay(
    val lagnaSign: String,
    val lagnaNakshatra: String,
    val moonSign: String,
    val moonNakshatra: String,
    val birthNakshatraPada: Int,
    val planets: List<PlanetDisplayRow>,
    val topStrengthNames: List<String>    // e.g. ["Jupiter", "Venus"]
)

data class AstroUiState(
    val loadState: AstroLoadState         = AstroLoadState.Idle,
    val displayDate: String               = "",   // "Sunday, 1 Jun 2026"
    val panchanga: PanchangaDisplay?      = null,
    val rahuKaal: RahuKaalDisplay?        = null,
    val planets: List<PlanetDisplayRow>   = emptyList(),
    val activeWarLabels: List<String>     = emptyList(),   // e.g. "Mars ⚔ Saturn (Mars loses)"
    val predictionText: String            = "",
    val predictionConfidence: Float       = 0f,
    val fromNpu: Boolean                  = false,
    val predictionInferenceMs: Long       = 0L,
    val isPredictionLoading: Boolean      = false,
    val birthChart: BirthChartDisplay?   = null,
    val isBirthChartLoading: Boolean     = false,
    val feedbackSubmitted: Boolean        = false,
    
    // Tier 2: Deterministic Rule Data
    val momentumScore: Int = 0,
    val ruleSummary: String = "",
    val auspiciousWindows: List<String> = emptyList(),
    val avoidWindows: List<String> = emptyList(),

    // Day selector: -1=yesterday, 0=today, 1=tomorrow
    val selectedDayOffset: Int = 0,

    val isExporting: Boolean = false,

    // Life Event Planner (Phase B/C)
    val numerology: NumerologyResult? = null,
    val selectedLifeEvent: LifeEventCategory? = null,
    val lifeEventAssessments: Map<LifeEventCategory, EventAssessment> = emptyMap()
)
