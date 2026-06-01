package com.jugaad.core.ephemeris.dto

/**
 * Topographic sunrise and sunset times with elevation-corrected refraction.
 *
 * Times are expressed as Julian Day Numbers (UT) for precision arithmetic.
 * Convert to wall-clock time via [com.jugaad.core.time.JulianDayConverter].
 */
data class SunriseSunset(
    /** Julian Day (UT) of local sunrise. */
    val sunriseJd: Double,

    /** Julian Day (UT) of local sunset. */
    val sunsetJd: Double,

    /** Observer latitude used for this computation. */
    val observerLatDeg: Double,

    /** Observer longitude used for this computation. */
    val observerLonDeg: Double,

    /** Observer elevation in metres. Used for refraction: h = -0.8333° - (0.0347° × √elev). */
    val elevationMeters: Double,

    /** Duration of daylight in decimal hours. */
    val daylightHours: Double = (sunsetJd - sunriseJd) * 24.0
)
