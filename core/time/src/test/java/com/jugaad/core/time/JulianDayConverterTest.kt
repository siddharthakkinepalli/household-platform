package com.jugaad.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class JulianDayConverterTest {

    @Test
    fun toJulianDayCorrectForJ2000() {
        val j2000Midnight = ZonedDateTime.of(2000, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC)
        assertEquals(2451545.0, JulianDayConverter.toJulianDay(j2000Midnight), 0.0001)
    }

    @Test
    fun fromJulianDayCorrectForJ2000() {
        val expected = ZonedDateTime.of(2000, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC)
        val result = JulianDayConverter.fromJulianDay(2451545.0)
        assertEquals(expected.year, result.year)
        assertEquals(expected.monthValue, result.monthValue)
        assertEquals(expected.dayOfMonth, result.dayOfMonth)
        assertEquals(expected.hour, result.hour)
    }

    @Test
    fun toJulianDayCorrectForArbitraryDate() {
        // 2024-06-01 00:00 UTC -> 2460462.5
        val date = LocalDate.of(2024, 6, 1)
        assertEquals(2460462.5, JulianDayConverter.toJulianDay(date), 0.0001)
    }

    @Test
    fun julianCenturiesCalculationCorrect() {
        // Exactly J2000 -> 0 centuries
        assertEquals(0.0, JulianDayConverter.julianCenturies(2451545.0), 0.0001)
        // 100 years (36525 days) after J2000 -> 1.0 centuries
        assertEquals(1.0, JulianDayConverter.julianCenturies(2451545.0 + 36525.0), 0.0001)
    }
}
