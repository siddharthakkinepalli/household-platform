package com.jugaad.core.time

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Converts between civil time representations and Julian Day Numbers (JDN).
 *
 * All ephemeris calculations use Julian Day in Universal Time (UT1). The Swiss
 * Ephemeris natively works in JD UT, so no ΔT (TT-UT) correction is applied here —
 * libswe handles that internally for planets; it matters only for lunar positions
 * at sub-arcsecond precision (not required for Vedic chart accuracy).
 *
 * Reference: Jean Meeus, "Astronomical Algorithms", 2nd ed., Chapter 7.
 */
object JulianDayConverter {

    private const val JD_J2000 = 2451545.0   // J2000.0 epoch = 2000-01-01 12:00 TT

    /**
     * Converts a [ZonedDateTime] to Julian Day (UT).
     *
     * The input is first converted to UTC, then to decimal Julian Day.
     * Accurate for dates between 4713 BC and 9999 AD.
     */
    fun toJulianDay(zdt: ZonedDateTime): Double {
        val utc   = zdt.withZoneSameInstant(ZoneOffset.UTC)
        val year  = utc.year
        val month = utc.monthValue
        val day   = utc.dayOfMonth +
                    utc.hour   / 24.0 +
                    utc.minute / 1440.0 +
                    utc.second / 86400.0 +
                    utc.nano   / 86_400_000_000_000.0

        return gregorianToJd(year, month, day)
    }

    /**
     * Converts a [LocalDate] at midnight UTC to Julian Day (UT).
     * Used for daily transit cache keys.
     */
    fun toJulianDay(date: LocalDate): Double =
        toJulianDay(date.atStartOfDay(ZoneOffset.UTC))

    /**
     * Converts a [LocalDateTime] treated as UTC to Julian Day.
     */
    fun toJulianDay(ldt: LocalDateTime): Double =
        toJulianDay(ldt.atZone(ZoneOffset.UTC))

    /**
     * Converts a Julian Day (UT) back to a [ZonedDateTime] in UTC.
     */
    fun fromJulianDay(jd: Double): ZonedDateTime {
        val z  = kotlin.math.floor(jd + 0.5).toLong()
        val f  = (jd + 0.5) - z

        val a = if (z < 2299161L) {
            z
        } else {
            val alpha = kotlin.math.floor((z - 1867216.25) / 36524.25).toLong()
            z + 1 + alpha - alpha / 4
        }

        val b = a + 1524
        val c = kotlin.math.floor((b - 122.1) / 365.25).toLong()
        val d = kotlin.math.floor(365.25 * c).toLong()
        val e = kotlin.math.floor((b - d) / 30.6001).toLong()

        val dayDecimal = (b - d - kotlin.math.floor(30.6001 * e)).toInt() + f
        val month      = if (e < 14) (e - 1).toInt() else (e - 13).toInt()
        val year       = if (month > 2) (c - 4716).toInt() else (c - 4715).toInt()

        val dayInt     = dayDecimal.toInt()
        val hourFrac   = (dayDecimal - dayInt) * 24.0
        val hourInt    = hourFrac.toInt()
        val minFrac    = (hourFrac - hourInt) * 60.0
        val minInt     = minFrac.toInt()
        val secFrac    = (minFrac - minInt) * 60.0
        val secInt     = secFrac.toInt()
        val nanoFrac   = ((secFrac - secInt) * 1_000_000_000).toLong()

        return ZonedDateTime.of(year, month, dayInt, hourInt, minInt, secInt, nanoFrac.toInt(), ZoneOffset.UTC)
    }

    /**
     * Returns the Julian Day for J2000.0 epoch (2000-01-01 12:00 TT).
     * Used as a reference for ayanamsha verification.
     */
    fun j2000(): Double = JD_J2000

    /**
     * Calculates the number of Julian centuries since J2000.0.
     * T = (JD - 2451545.0) / 36525.0
     */
    fun julianCenturies(jd: Double): Double = (jd - JD_J2000) / 36525.0

    // ── Private: Meeus Algorithm 7.1 (Gregorian calendar) ───────────────────

    private fun gregorianToJd(year: Int, month: Int, day: Double): Double {
        val y = if (month <= 2) year - 1 else year
        val m = if (month <= 2) month + 12 else month
        val a = (y / 100).toLong()
        val b = 2 - a + a / 4
        return kotlin.math.floor(365.25 * (y + 4716)) +
               kotlin.math.floor(30.6001 * (m + 1)) +
               day + b - 1524.5
    }
}
