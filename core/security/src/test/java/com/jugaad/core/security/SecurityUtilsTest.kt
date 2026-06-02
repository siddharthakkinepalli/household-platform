package com.jugaad.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityUtilsTest {

    @Test
    fun sha256ReturnsCorrectHash() {
        val input = "jugaad"
        val expected = "274a169994c6439567990141f92e59df957df38814777d0187889154f9d45465"
        assertEquals(expected, SecurityUtils.sha256(input))
    }

    @Test
    fun sha256ReturnsDifferentHashForDifferentInput() {
        val input1 = "jugaad1"
        val input2 = "jugaad2"
        assert(SecurityUtils.sha256(input1) != SecurityUtils.sha256(input2))
    }
}
