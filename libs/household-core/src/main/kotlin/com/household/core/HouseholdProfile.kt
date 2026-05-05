package com.household.core

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Represents a household unit with members and shared preferences.
 * This is the core identity class for the household platform.
 */
@Entity(tableName = "households")
data class HouseholdProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** Unique identifier for this household instance */
    val householdId: String,
    
    /** Human-readable name for the household */
    val name: String,
    
    /** List of member names/identifiers (comma-separated) */
    val members: String,
    
    /** Number of members in the household */
    val memberCount: Int,
    
    /** Household timezone (e.g., "Asia/Kolkata") */
    val timezone: String = "UTC",
    
    /** Preferred language code (e.g., "en", "hi") */
    val language: String = "en",
    
    /** Currency code (e.g., "INR", "USD") */
    val currency: String = "INR",
    
    /** Whether backups are encrypted */
    val backupEncrypted: Boolean = true,
    
    /** Creation timestamp */
    val createdAt: String = LocalDateTime.now().toString(),
    
    /** Last modified timestamp */
    val modifiedAt: String = LocalDateTime.now().toString()
) {
    
    /**
     * Gets the list of member names as a collection
     */
    fun getMembersList(): List<String> {
        return if (members.isEmpty()) emptyList() else members.split(",").map { it.trim() }
    }
    
    /**
     * Checks if a member name exists in the household
     */
    fun hasMember(name: String): Boolean {
        return getMembersList().any { it.equals(name, ignoreCase = true) }
    }
    
    /**
     * Creates a copy with updated member list
     */
    fun withMembers(memberList: List<String>): HouseholdProfile {
        return this.copy(
            members = memberList.joinToString(","),
            memberCount = memberList.size,
            modifiedAt = LocalDateTime.now().toString()
        )
    }
}
