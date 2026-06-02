package com.jugaad.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

class VedicCalendarTest {

    @Test
    fun tithiCalculationCorrect() {
        // Sun at 0, Moon at 12 -> Tithi 2
        assertEquals(2, VedicCalendar.tithi(0.0, 12.0))
        // Sun at 0, Moon at 0 -> Tithi 1
        assertEquals(1, VedicCalendar.tithi(0.0, 0.0))
        // Sun at 0, Moon at 359 -> Tithi 30
        assertEquals(30, VedicCalendar.tithi(0.0, 359.0))
    }

    @Test
    fun pakshaCalculationCorrect() {
        // Shukla (1-15)
        assertEquals("Shukla", VedicCalendar.paksha(0.0, 10.0))
        // Krishna (16-30)
        assertEquals("Krishna", VedicCalendar.paksha(0.0, 200.0))
    }

    @Test
    fun nakshatraCalculationCorrect() {
        // 0.0 -> Ashwini (0)
        assertEquals(0, VedicCalendar.moonNakshatra(0.0))
        // 359.0 -> Revati (26)
        assertEquals(26, VedicCalendar.moonNakshatra(359.0))
    }

    @Test
    fun nakshatraPadaCalculationCorrect() {
        val arc = 360.0 / 27.0
        // First quarter of Ashwini
        assertEquals(1, VedicCalendar.moonNakshatraPada(0.0))
        // Last quarter of Ashwini
        assertEquals(4, VedicCalendar.moonNakshatraPada(arc - 0.1))
    }

    @Test
    fun yogaCalculationCorrect() {
        // (0 + 0) / YOGA_ARC -> 0
        assertEquals(0, VedicCalendar.yoga(0.0, 0.0))
    }

    @Test
    fun karanaCalculationCorrect() {
        // First half of Shukla Pratipada -> Kimstughna (11)
        assertEquals(11, VedicCalendar.karana(0.0, 1.0))
    }

    @Test
    fun varaCalculationCorrect() {
        // JD 2460462.5 is a Saturday (6) in UTC
        assertEquals(6, VedicCalendar.vara(2460462.5))
    }
}
