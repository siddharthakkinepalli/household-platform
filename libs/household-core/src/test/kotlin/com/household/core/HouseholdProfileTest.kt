package com.household.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseholdProfileTest {

    @Test
    fun `test getMembersList with multiple members`() {
        val profile = HouseholdProfile(
            householdId = "h1",
            name = "Test",
            members = "Alice, Bob, Charlie",
            memberCount = 3
        )
        val list = profile.getMembersList()
        assertEquals(3, list.size)
        assertEquals("Alice", list[0])
        assertEquals("Bob", list[1])
        assertEquals("Charlie", list[2])
    }

    @Test
    fun `test getMembersList with empty members`() {
        val profile = HouseholdProfile(
            householdId = "h1",
            name = "Test",
            members = "",
            memberCount = 0
        )
        val list = profile.getMembersList()
        assertTrue(list.isEmpty())
    }

    @Test
    fun `test hasMember case insensitivity`() {
        val profile = HouseholdProfile(
            householdId = "h1",
            name = "Test",
            members = "Alice, Bob",
            memberCount = 2
        )
        assertTrue(profile.hasMember("alice"))
        assertTrue(profile.hasMember("BOB"))
        assertFalse(profile.hasMember("Charlie"))
    }

    @Test
    fun `test withMembers updates count and timestamp`() {
        val profile = HouseholdProfile(
            householdId = "h1",
            name = "Test",
            members = "Alice",
            memberCount = 1
        )
        val updated = profile.withMembers(listOf("Alice", "Bob"))
        assertEquals("Alice,Bob", updated.members)
        assertEquals(2, updated.memberCount)
        // Check that modifiedAt is different or at least set
        assertTrue(updated.modifiedAt.isNotEmpty())
    }
}
