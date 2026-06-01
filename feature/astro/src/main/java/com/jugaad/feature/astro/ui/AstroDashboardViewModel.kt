package com.jugaad.feature.astro.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jugaad.core.airuntime.AstroInferenceModel
import com.jugaad.core.ephemeris.dto.HouseData
import com.jugaad.core.ephemeris.dto.PlanetPosition
import com.jugaad.core.time.JulianDayConverter
import com.jugaad.core.time.VedicCalendar
import com.jugaad.feature.astro.domain.model.DailyTransit
import com.jugaad.feature.astro.domain.model.GrahaYuddha
import com.jugaad.feature.astro.domain.model.ShadbalaSummary
import com.jugaad.feature.astro.domain.repository.AstroRepository
import com.jugaad.feature.astro.domain.engine.AstroRuleEngine
import com.jugaad.feature.astro.domain.model.EventAssessment
import com.jugaad.feature.astro.domain.model.LifeEventCategory
import com.jugaad.feature.astro.domain.model.NumerologyResult
import com.jugaad.feature.astro.domain.usecase.ComputeBirthChartUseCase
import com.jugaad.feature.astro.domain.usecase.FeedbackInput
import com.jugaad.feature.astro.domain.usecase.GetDailyTransitUseCase
import com.jugaad.feature.astro.domain.usecase.GetNumerologyUseCase
import com.jugaad.feature.astro.domain.usecase.SubmitFeedbackUseCase
import com.jugaad.feature.astro.preferences.AstroPreferencesManager
import com.jugaad.feature.astro.ui.state.AstroLoadState
import com.jugaad.feature.astro.ui.state.AstroUiState
import com.jugaad.feature.astro.ui.state.BirthChartDisplay
import com.jugaad.feature.astro.ui.state.PanchangaDisplay
import com.jugaad.feature.astro.ui.state.PlanetDisplayRow
import com.jugaad.feature.astro.ui.state.RahuKaalDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Reactive state owner for [AstroHomeScreen] and [BirthChartScreen].
 */
@HiltViewModel
class AstroDashboardViewModel @Inject constructor(
    private val getDailyTransitUseCase: GetDailyTransitUseCase,
    private val computeBirthChartUseCase: ComputeBirthChartUseCase,
    private val getNumerologyUseCase: GetNumerologyUseCase,
    private val submitFeedbackUseCase: SubmitFeedbackUseCase,
    private val inferenceModel: AstroInferenceModel,
    private val repository: AstroRepository,
    private val preferences: AstroPreferencesManager,
    private val ruleEngine: AstroRuleEngine,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AstroUiState())
    val uiState: StateFlow<AstroUiState> = _uiState.asStateFlow()

    private val _exportEvents = Channel<Uri>(Channel.BUFFERED)
    val exportEvents = _exportEvents.receiveAsFlow()

    // ── Public triggers ───────────────────────────────────────────────────────

    init {
        loadDay()
    }

    /** Loads transit + panchanga for [offset] days from today (−1=yesterday, 0=today, 1=tomorrow). */
    fun loadDay(offset: Int = 0, profileId: Long? = null) {
        val today     = LocalDate.now().plusDays(offset.toLong())
        val jd        = JulianDayConverter.toJulianDay(today)
        val dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.ENGLISH))

        _uiState.update { it.copy(loadState = AstroLoadState.Loading, displayDate = dateLabel, selectedDayOffset = offset) }

        viewModelScope.launch {
            try {
                val targetProfileId = profileId ?: repository.observeAllUserProfiles().firstOrNull()?.firstOrNull()?.id

                val transit = getDailyTransitUseCase.execute(jd)
                val sun     = transit.planets.first { it.planetId == 0 }
                val moon    = transit.planets.first { it.planetId == 1 }

                val panchanga = VedicCalendar.panchanga(sun.longitudeDeg, moon.longitudeDeg, jd)
                val rahuKaal = computeRahuKaal(today)

                // ── TIER 2: Deterministic Rule Engine ──
                val rules = ruleEngine.generateDeterministicInsight(transit, today)

                var birthChart: BirthChartDisplay? = null
                var natalPayload: String? = null
                var numerology: NumerologyResult? = null
                if (targetProfileId != null) {
                    val chartResult = runCatching { computeBirthChartUseCase.execute(targetProfileId) }.getOrNull()
                    if (chartResult != null) {
                        natalPayload = chartResult.contextPayload.json
                        val natalMoon = chartResult.planets.first { it.planetId == 1 }
                        birthChart = BirthChartDisplay(
                            lagnaSign         = SIGN_NAMES[chartResult.houseData.lagnaSignId],
                            lagnaNakshatra    = NAKSHATRA_NAMES[chartResult.houseData.lagnaNakshatraId],
                            moonSign          = SIGN_NAMES[natalMoon.signId],
                            moonNakshatra     = NAKSHATRA_NAMES[natalMoon.nakshatraId],
                            birthNakshatraPada = natalMoon.pada,
                            planets           = chartResult.planets.toDisplayRows(chartResult.shadbalaSummary, chartResult.natalGrahaYuddhaList),
                            topStrengthNames  = chartResult.shadbalaSummary.scores
                                .entries.sortedByDescending { it.value }.take(3)
                                .map { PLANET_NAMES[it.key] }
                        )
                    }
                    // Numerology — decrypts DOB once, zero-fills, runs pure Kotlin math
                    numerology = runCatching { getNumerologyUseCase.execute(targetProfileId, today) }.getOrNull()
                }

                // All 5 life event assessments built synchronously from existing data
                val assessments: Map<LifeEventCategory, EventAssessment> =
                    LifeEventCategory.entries.associateWith { category ->
                        ruleEngine.evaluateLifeEvent(category, transit, numerology, today)
                    }

                _uiState.update { state ->
                    state.copy(
                        loadState       = AstroLoadState.Idle,
                        panchanga       = panchanga.toDisplay(),
                        rahuKaal        = rahuKaal,
                        planets         = transit.planets.toDisplayRows(transit.shadbalaSummary, transit.grahaYuddhaList),
                        activeWarLabels = transit.grahaYuddhaList.toWarLabels(),
                        birthChart      = birthChart,
                        numerology      = numerology,
                        lifeEventAssessments = assessments,

                        // Rule engine outputs (immediate)
                        momentumScore     = rules.score,
                        ruleSummary       = rules.summary,
                        auspiciousWindows = rules.auspiciousWindows,
                        avoidWindows      = rules.avoidWindows
                    )
                }

                // Cache Rahu Kaal for worker + widget
                rahuKaal?.let {
                    val startMs = parseTimeLabel(it.startLabel, today)
                    val endMs   = parseTimeLabel(it.endLabel, today)
                    if (startMs > 0) preferences.saveRahuKaal(startMs, endMs, jd)
                }

                // Run foreground inference (Optional Layer 3)
                runForegroundInference(transit, natalPayload)
            } catch (e: Exception) {
                _uiState.update { it.copy(loadState = AstroLoadState.Error(e.message ?: "Unknown error")) }
            }
        }
    }

    /** Forces a fresh birth chart computation for [profileId]. */
    fun loadBirthChart(profileId: Long) {
        _uiState.update { it.copy(isBirthChartLoading = true) }
        viewModelScope.launch {
            runCatching {
                val chart = computeBirthChartUseCase.execute(profileId)
                val moon  = chart.planets.first { it.planetId == 1 }
                _uiState.update { state ->
                    state.copy(
                        isBirthChartLoading = false,
                        birthChart = BirthChartDisplay(
                            lagnaSign         = SIGN_NAMES[chart.houseData.lagnaSignId],
                            lagnaNakshatra    = NAKSHATRA_NAMES[chart.houseData.lagnaNakshatraId],
                            moonSign          = SIGN_NAMES[moon.signId],
                            moonNakshatra     = NAKSHATRA_NAMES[moon.nakshatraId],
                            birthNakshatraPada = moon.pada,
                            planets           = chart.planets.toDisplayRows(chart.shadbalaSummary, chart.natalGrahaYuddhaList),
                            topStrengthNames  = chart.shadbalaSummary.scores
                                .entries.sortedByDescending { it.value }.take(3)
                                .map { PLANET_NAMES[it.key] }
                        )
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isBirthChartLoading = false) }
            }
        }
    }

    /** Submits a 1–5 feedback rating for the current prediction. */
    fun submitFeedback(rating: Int) {
        val current = _uiState.value
        if (current.predictionText.isBlank()) return
        viewModelScope.launch {
            runCatching {
                submitFeedbackUseCase.execute(
                    FeedbackInput(
                        profileId          = 1L,   // Phase 5: single-profile; multi-profile in Phase 6
                        predictionId       = UUID.randomUUID().toString(),
                        predictionType     = "DAILY_TRANSIT",
                        predictionContent  = current.predictionText,
                        userRating         = rating,
                        modelConfidence    = current.predictionConfidence,
                        modelVersion       = "1.0.0",
                        predictionTimestamp = System.currentTimeMillis()
                    )
                )
                _uiState.update { it.copy(feedbackSubmitted = true) }
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun runForegroundInference(
        transit: DailyTransit,
        natalPayload: String? = null,
        force: Boolean = false
    ) {
        _uiState.update { it.copy(isPredictionLoading = true) }

        // Check cache first — skip inference if already computed for today's JD (unless forced)
        if (!force) {
            val cached = preferences.getCachedPrediction()
            if (cached != null && kotlin.math.abs(cached.julianDayUt - transit.julianDayUt) < 0.01) {
                _uiState.update { state ->
                    state.copy(
                        predictionText       = cached.text,
                        predictionConfidence = cached.confidence,
                        fromNpu              = cached.fromNpu,
                        predictionInferenceMs = cached.inferenceMs,
                        isPredictionLoading  = false
                    )
                }
                return
            }
        }

        runCatching {
            inferenceModel.initialize()
            
            val result = if (natalPayload != null) {
                inferenceModel.predictPersonalized(natalPayload, transit.contextPayload.json)
            } else {
                inferenceModel.predictTransit(transit.contextPayload.json)
            }

            if (result.predictionText.isNotBlank()) {
                preferences.savePrediction(
                    text        = result.predictionText,
                    julianDayUt = transit.julianDayUt,
                    confidence  = result.confidence,
                    fromNpu     = result.fromNpu,
                    inferenceMs = result.inferenceMs
                )
            }

            _uiState.update { state ->
                state.copy(
                    predictionText        = result.predictionText,
                    predictionConfidence  = result.confidence,
                    fromNpu               = result.fromNpu,
                    predictionInferenceMs = result.inferenceMs,
                    isPredictionLoading   = false
                )
            }
        }.onFailure { e ->
            android.util.Log.e("AstroDashboard", "Inference failed", e)
            _uiState.update { it.copy(isPredictionLoading = false) }
        }
    }

    /**
     * Computes Rahu Kaal window for [today].
     *
     * Rahu Kaal = 1.5-hour slot within the sunrise→sunset window, indexed by day of week.
     * Uses a standard 6am–6pm approximation when sunrise/sunset data is unavailable.
     * The TransitRefreshWorker updates this with precise EphemerisEngine times.
     */
    private fun computeRahuKaal(today: LocalDate): RahuKaalDisplay? {
        val cached = runCatching {
            // Try to use cached precise times from TransitRefreshWorker
            null  // preferences.getRahuKaal() is suspend; use standard approximation here
        }.getOrNull()

        // Slot index by day (0=Sun … 6=Sat), 1-indexed within 8 equal day parts
        val slotByDay = intArrayOf(8, 2, 7, 5, 6, 4, 3)
        val dayOfWeek = today.dayOfWeek.value % 7  // 0=Sunday
        val slot      = slotByDay[dayOfWeek]

        // Approximate day: 6am → 6pm (12h), each of 8 parts = 90min
        val sunriseMs  = today.atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000 + 6 * 3600_000L
        val partMs     = 90 * 60_000L
        val startMs    = sunriseMs + (slot - 1) * partMs
        val endMs      = startMs + partMs
        val nowMs      = System.currentTimeMillis()

        val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val startZdt = java.time.Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault())
        val endZdt   = java.time.Instant.ofEpochMilli(endMs).atZone(ZoneId.systemDefault())

        val progress = when {
            nowMs < startMs -> 0f
            nowMs > endMs   -> 1f
            else            -> (nowMs - startMs).toFloat() / partMs
        }

        return RahuKaalDisplay(
            startLabel       = startZdt.format(fmt),
            endLabel         = endZdt.format(fmt),
            progressFraction = progress,
            isActive         = nowMs in startMs..endMs
        )
    }

    private fun parseTimeLabel(label: String, date: LocalDate): Long {
        return runCatching {
            val (h, m) = label.split(":").map { it.toInt() }
            date.atTime(h, m).atZone(ZoneId.systemDefault()).toEpochSecond() * 1000L
        }.getOrDefault(0L)
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun List<PlanetPosition>.toDisplayRows(
        shadbala: ShadbalaSummary,
        wars: List<GrahaYuddha>
    ): List<PlanetDisplayRow> {
        val loserIds  = wars.map { it.loserPlanetId }.toSet()
        val winnerIds = wars.map { it.winnerPlanetId }.toSet()
        return sortedBy { it.planetId }.map { p ->
            PlanetDisplayRow(
                planetId      = p.planetId,
                name          = PLANET_NAMES[p.planetId],
                signName      = SIGN_NAMES[p.signId],
                nakshatraName = NAKSHATRA_NAMES[p.nakshatraId],
                pada          = p.pada,
                longitude     = "${"%.1f".format(p.degreeInSign)}°",
                retrograde    = p.retrograde,
                shadabalaScore= shadbala.scoreFor(p.planetId),
                isInWar       = p.planetId in loserIds || p.planetId in winnerIds,
                warResult     = when (p.planetId) {
                    in loserIds  -> "loser"
                    in winnerIds -> "winner"
                    else         -> null
                }
            )
        }
    }

    private fun List<GrahaYuddha>.toWarLabels(): List<String> = map { war ->
        "${PLANET_NAMES[war.planet1Id]} ⚔ ${PLANET_NAMES[war.planet2Id]} " +
        "(${PLANET_NAMES[war.loserPlanetId]} loses, ${war.shadabalaReductionPct.roundToInt()}% strength)"
    }

    private fun VedicCalendar.Panchanga.toDisplay(): PanchangaDisplay {
        val tithiPaksha = if (tithi <= 15) "Shukla" else "Krishna"
        val tithiNum    = if (tithi <= 15) tithi else tithi - 15
        return PanchangaDisplay(
            tithi     = "$tithiPaksha ${TITHI_NAMES.getOrElse(tithiNum - 1) { "Purnima" }}",
            paksha    = paksha,
            nakshatra = NAKSHATRA_NAMES[nakshatra],
            yoga      = YOGA_NAMES[yoga],
            karana    = KARANA_NAMES.getOrElse(karana - 1) { "Bava" },
            vara      = VARA_NAMES[vara]
        )
    }

    /** Selects or deselects a life event category for the planner card. */
    fun selectLifeEvent(category: LifeEventCategory) {
        _uiState.update { state ->
            val next = if (state.selectedLifeEvent == category) null else category
            state.copy(selectedLifeEvent = next)
        }
    }

    // ── PDF export ────────────────────────────────────────────────────────────

    /** Generates a birth chart PDF and emits a share-ready [Uri] via [exportEvents]. */
    fun exportBirthChartPdf() {
        val chart = _uiState.value.birthChart ?: return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val uri = buildBirthChartPdf(chart)
                _exportEvents.send(uri)
            }.onFailure { e ->
                android.util.Log.e("AstroDashboard", "PDF export failed", e)
            }
            _uiState.update { it.copy(isExporting = false) }
        }
    }

    private fun buildBirthChartPdf(chart: BirthChartDisplay): Uri {
        val doc      = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page     = doc.startPage(pageInfo)
        drawPage(page.canvas, chart)
        doc.finishPage(page)

        val file = File(appContext.cacheDir, "birth_chart.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()

        return FileProvider.getUriForFile(appContext, "${appContext.packageName}.provider", file)
    }

    private fun drawPage(canvas: android.graphics.Canvas, chart: BirthChartDisplay) {
        val L = 40f   // left margin
        val R = 555f  // right margin

        val titlePaint = Paint().apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = android.graphics.Color.BLACK }
        val subPaint   = Paint().apply { textSize = 10f; color = android.graphics.Color.GRAY }
        val secPaint   = Paint().apply { textSize = 9f;  typeface = Typeface.DEFAULT_BOLD; color = android.graphics.Color.rgb(80, 40, 140) }
        val bodyPaint  = Paint().apply { textSize = 11f; color = android.graphics.Color.BLACK }
        val boldPaint  = Paint().apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD; color = android.graphics.Color.BLACK }
        val colPaint   = Paint().apply { textSize = 8f;  typeface = Typeface.DEFAULT_BOLD; color = android.graphics.Color.GRAY }
        val linePaint  = Paint().apply { strokeWidth = 0.5f; color = android.graphics.Color.LTGRAY }
        val footPaint  = Paint().apply { textSize = 8f;  color = android.graphics.Color.GRAY }

        var y = 58f

        // Title
        canvas.drawText("Vedic Natal Birth Chart", L, y, titlePaint)
        y += 22f
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
        canvas.drawText("Generated: $dateStr  ·  Lahiri Ayanamsha  ·  JUGAAD Astro", L, y, subPaint)
        y += 14f
        canvas.drawLine(L, y, R, y, linePaint); y += 18f

        // Birth profile
        canvas.drawText("BIRTH PROFILE", L, y, secPaint); y += 16f
        canvas.drawText("Ascendant (Lagna)", L, y, subPaint)
        canvas.drawText("${chart.lagnaSign}  ·  ${chart.lagnaNakshatra}", L + 130f, y, bodyPaint); y += 16f
        canvas.drawText("Moon Sign (Rashi)", L, y, subPaint)
        canvas.drawText("${chart.moonSign}  ·  ${chart.moonNakshatra} Pada ${chart.birthNakshatraPada}", L + 130f, y, bodyPaint); y += 22f

        // Strongest planets
        canvas.drawText("STRONGEST PLANETS", L, y, secPaint); y += 16f
        canvas.drawText(chart.topStrengthNames.joinToString("   ·   "), L, y, boldPaint); y += 24f

        canvas.drawLine(L, y, R, y, linePaint); y += 18f

        // Planet table
        canvas.drawText("NATAL PLANET POSITIONS", L, y, secPaint); y += 18f

        // Column headers
        val c = floatArrayOf(L, L+90f, L+200f, L+330f, L+380f, L+440f, L+490f)
        // Planet | Sign | Nakshatra | Pada | Longitude | Śaktī | Flags
        listOf("PLANET", "SIGN", "NAKSHATRA", "PADA", "LON", "ŚAKTĪ", "").zip(c.toList()).forEach { (h, x) ->
            canvas.drawText(h, x, y, colPaint)
        }
        y += 6f
        canvas.drawLine(L, y, R, y, linePaint); y += 14f

        // Planet rows
        chart.planets.forEach { p ->
            val flags = buildString {
                if (p.retrograde) append("℞")
                if (p.warResult != null) append(" ⚔${p.warResult[0].uppercaseChar()}")
            }
            canvas.drawText(p.name,             c[0], y, bodyPaint)
            canvas.drawText(p.signName,         c[1], y, bodyPaint)
            canvas.drawText(p.nakshatraName,    c[2], y, bodyPaint)
            canvas.drawText("P${p.pada}",       c[3], y, bodyPaint)
            canvas.drawText(p.longitude,        c[4], y, bodyPaint)
            canvas.drawText("${p.shadabalaScore}", c[5], y, bodyPaint)
            if (flags.isNotEmpty()) canvas.drawText(flags, c[6], y, subPaint)
            y += 19f
        }

        // Footer
        canvas.drawLine(L, 820f, R, 820f, linePaint)
        canvas.drawText("JUGAAD Astro · Vedic Astrology · Offline computation · For personal use only", L, 834f, footPaint)
    }

    // ── Static lookup tables ──────────────────────────────────────────────────

    companion object {
        val PLANET_NAMES    = arrayOf("Sun","Moon","Mercury","Venus","Mars","Jupiter","Saturn","Rahu","Ketu")
        val SIGN_NAMES      = arrayOf("Aries","Taurus","Gemini","Cancer","Leo","Virgo","Libra","Scorpio","Sagittarius","Capricorn","Aquarius","Pisces")
        val NAKSHATRA_NAMES = arrayOf(
            "Ashwini","Bharani","Krittika","Rohini","Mrigashira","Ardra",
            "Punarvasu","Pushya","Ashlesha","Magha","Purva Phalguni","Uttara Phalguni",
            "Hasta","Chitra","Swati","Vishakha","Anuradha","Jyeshtha",
            "Mula","Purva Ashadha","Uttara Ashadha","Shravana","Dhanishtha","Shatabhisha",
            "Purva Bhadrapada","Uttara Bhadrapada","Revati"
        )
        val VARA_NAMES   = arrayOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
        val TITHI_NAMES  = arrayOf("Pratipada","Dvitiya","Tritiya","Chaturthi","Panchami","Shashthi","Saptami","Ashtami","Navami","Dashami","Ekadashi","Dwadashi","Trayodashi","Chaturdashi","Purnima")
        val YOGA_NAMES   = arrayOf("Vishkambha","Preeti","Ayushman","Saubhagya","Shobhana","Atiganda","Sukarma","Dhriti","Shoola","Ganda","Vriddhi","Dhruva","Vyaghata","Harshana","Vajra","Siddhi","Vyatipata","Variyan","Parigha","Shiva","Siddha","Sadhya","Shubha","Shukla","Brahma","Indra","Vaidhrti")
        val KARANA_NAMES = arrayOf("Bava","Balava","Kaulava","Taitila","Garaja","Vanija","Vishti","Shakuni","Chatushpada","Naga","Kimstughna")
    }
}
