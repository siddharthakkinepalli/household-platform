package com.household.app.domain.receipt

/**
 * Levenshtein Distance Implementation
 * Pure Kotlin - no external libraries - fully offline
 *
 * Calculates the minimum number of single-character edits needed
 * to change one string into another.
 */
object Levenshtein {

    /**
     * Calculate Levenshtein distance between two strings.
     * Lower distance = more similar strings.
     *
     * @return Number of edits required (0 = identical)
     */
    fun distance(s1: String, s2: String): Int {
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val len1 = s1.length
        val len2 = s2.length

        // Use two rows instead of full matrix for memory efficiency
        var previousRow = IntArray(len2 + 1)
        var currentRow = IntArray(len2 + 1)

        // Initialize first row
        for (j in 0..len2) {
            previousRow[j] = j
        }

        for (i in 1..len1) {
            currentRow[0] = i

            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                currentRow[j] = minOf(
                    previousRow[j] + 1,           // deletion
                    currentRow[j - 1] + 1,         // insertion
                    previousRow[j - 1] + cost     // substitution
                )
            }

            // Swap rows
            val temp = previousRow
            previousRow = currentRow
            currentRow = temp
        }

        return previousRow[len2]
    }

    /**
     * Calculate similarity as a percentage (0.0 to 1.0).
     * 1.0 = identical strings
     */
    fun similarity(s1: String, s2: String): Float {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f

        val maxLen = maxOf(s1.length, s2.length)
        val dist = distance(s1, s2)
        return 1.0f - (dist.toFloat() / maxLen)
    }

    /**
     * Quick fuzzy check - returns true if strings are "close enough".
     * Uses 80% similarity threshold.
     */
    fun isSimilar(s1: String, s2: String, threshold: Float = 0.8f): Boolean {
        return similarity(s1, s2) >= threshold
    }

    /**
     * Find the best match for a query string against a list of candidates.
     * Returns the best candidate and its similarity score.
     */
    fun findBestMatch(
        query: String,
        candidates: List<String>,
        threshold: Float = 0.6f
    ): Pair<String, Float>? {
        if (candidates.isEmpty()) return null

        var bestCandidate: String? = null
        var bestScore = 0f

        for (candidate in candidates) {
            val score = similarity(query.lowercase(), candidate.lowercase())
            if (score > bestScore && score >= threshold) {
                bestScore = score
                bestCandidate = candidate
            }
        }

        return bestCandidate?.let { Pair(it, bestScore) }
    }
}