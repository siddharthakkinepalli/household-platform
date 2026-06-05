package com.jugaad.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityUtilsTest {

    @Test
    fun sha256ReturnsCorrectHash() {
        val input = "jugaad"
        val expected = "30bad95b0def26b19a5dc99942b7a4cdbd13931814074deef544234f9228f6d3"
        assertEquals(expected, SecurityUtils.sha256(input))
    }

    @Test
    fun sha256ReturnsDifferentHashForDifferentInput() {
        val input1 = "jugaad1"
        val input2 = "jugaad2"
        assert(SecurityUtils.sha256(input1) != SecurityUtils.sha256(input2))
    }
}
