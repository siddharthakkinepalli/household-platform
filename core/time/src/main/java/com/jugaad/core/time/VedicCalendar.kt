package com.jugaad.core.time

import kotlin.math.floor

/**
 * Computes Vedic Panchanga (five limbs) from Sun and Moon positions.
 *
 * All inputs are sidereal longitudes in decimal degrees [0, 360).
 * No JNI dependencies — pure Kotlin arithmetic.
 *
 * Reference: B.V. Raman, "Hindu Predictive Astrology"; K.S. Krishnamurti tables.
 */
object VedicCalendar {

    private const val TITHI_ARC     = 12.0    // 1 tithi = 12° of Sun-Moon elongation
    private const val NAKSHATRA_ARC = 360.0 / 27.0  // 1 nakshatra = 13°20'
    private const val YOGA_ARC      = 360.0 / 27.0  // 1 yoga = 13°20'
    private const val KARANA_ARC    = 6.0     // 1 karana = 6° of elongation

    // ── Tithi ────────────────────────────────────────────────────────────────

    /**
     * Returns the Tithi index [1, 30].
     *   1–15  = Shukla Paksha (waxing Moon)
     *   16–30 = Krishna Paksha (waning Moon)
     *
     * @param sunLonDeg  Sidereal Sun longitude
     * @param moonLonDeg Sidereal Moon longitude
     */
    fun tithi(sunLonDeg: Double, moonLonDeg: Double): Int {
        val elongation = (moonLonDeg - sunLonDeg + 360.0) % 360.0
        return floor(elongation / TITHI_ARC).toInt() + 1
    }

    /**
     * Returns the Paksha: "Shukla" (waxing) or "Krishna" (waning).
     */
    fun paksha(sunLonDeg: Double, moonLonDeg: Double): String {
        val t = tithi(sunLonDeg, moonLonDeg)
        return if (t <= 15) "Shukla" else "Krishna"
    }

    // ── Nakshatra ────────────────────────────────────────────────────────────

    /**
     * Returns the Moon's Nakshatra index [0, 26].
     *   0=Ashwini, 1=Bharani, … 26=Revati
     */
    fun moonNakshatra(moonLonDeg: Double): Int =
        floor(moonLonDeg / NAKSHATRA_ARC).toInt()

    /**
     * Returns the Moon's Nakshatra Pada [1, 4].
     */
    fun moonNakshatraPada(moonLonDeg: Double): Int {
        val posInNak = moonLonDeg % NAKSHATRA_ARC
        return floor(posInNak / (NAKSHATRA_ARC / 4.0)).toInt() + 1
    }

    // ── Yoga ─────────────────────────────────────────────────────────────────

    /**
     * Returns the Yoga index [0, 26].
     * Yoga = (Sun longitude + Moon longitude) / (360/27), taken mod 27.
     *   0=Vishkambha, 1=Preeti, … 26=Vaidhrti
     */
    fun yoga(sunLonDeg: Double, moonLonDeg: Double): Int {
        val combined = (sunLonDeg + moonLonDeg) % 360.0
        return floor(combined / YOGA_ARC).toInt()
    }

    // ── Karana ───────────────────────────────────────────────────────────────

    /**
     * Returns the Karana index [1, 11].
     * There are 7 repeating (Chara) and 4 fixed (Sthira) karanas.
     *
     * Fixed karanas:
     *   Kimstughna  = 1st half of Shukla Pratipada
     *   Chatushpada, Naga, Sakuni = last 1.5 tithis of Krishna paksha
     *
     * Repeating karanas [1–7]: Bava, Balava, Kaulava, Taitila, Garaja, Vanija, Vishti
     */
    fun karana(sunLonDeg: Double, moonLonDeg: Double): Int {
        val elongation = (moonLonDeg - sunLonDeg + 360.0) % 360.0
        val karanaSeq  = floor(elongation / KARANA_ARC).toInt()

        return when (karanaSeq) {
            0            -> 11  // Kimstughna (fixed)
            57, 58, 59  -> 8 + (karanaSeq - 57)  // Chatushpada, Naga, Sakuni (fixed)
            else         -> ((karanaSeq - 1) % 7) + 1  // Repeating 1–7
        }
    }

    // ── Vara (Weekday) ────────────────────────────────────────────────────────

    /**
     * Returns the Vedic Vara (weekday) index [0, 6] from a Julian Day.
     *   0=Sunday, 1=Monday, 2=Tuesday, 3=Wednesday, 4=Thursday, 5=Friday, 6=Saturday
     *
     * JD 0.5 = Monday (Julian calendar epoch).
     */
    fun vara(julianDayUt: Double): Int =
        ((julianDayUt + 1.5).toLong() % 7).toInt()

    // ── Panchanga summary ─────────────────────────────────────────────────────

    data class Panchanga(
        val tithi: Int,
        val paksha: String,
        val nakshatra: Int,
        val nakshatraPada: Int,
        val yoga: Int,
        val karana: Int,
        val vara: Int
    )

    /**
     * Computes the full Panchanga from Sun and Moon longitudes and Julian Day.
     *
     * @param sunLonDeg  Sidereal Sun longitude [0, 360)
     * @param moonLonDeg Sidereal Moon longitude [0, 360)
     * @param julianDayUt Julian Day in UT (for Vara computation)
     */
    fun panchanga(sunLonDeg: Double, moonLonDeg: Double, julianDayUt: Double): Panchanga =
        Panchanga(
            tithi        = tithi(sunLonDeg, moonLonDeg),
            paksha       = paksha(sunLonDeg, moonLonDeg),
            nakshatra    = moonNakshatra(moonLonDeg),
            nakshatraPada = moonNakshatraPada(moonLonDeg),
            yoga         = yoga(sunLonDeg, moonLonDeg),
            karana       = karana(sunLonDeg, moonLonDeg),
            vara         = vara(julianDayUt)
        )
}
