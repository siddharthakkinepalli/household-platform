package com.household.app.vault.classification.parsers

import com.household.app.vault.classification.ClassificationSignals
import com.household.app.vault.classification.DocumentCountry
import com.household.app.vault.classification.DocumentDocType
import com.household.app.vault.classification.DocumentParser
import com.household.app.vault.extraction.ExtractedEntity
import com.household.app.vault.extraction.EntityType
import com.household.app.vault.extraction.ExtractionResult
import com.household.app.vault.normalization.DocumentNormalizer
import java.time.format.DateTimeFormatter

/**
 * Parser for Indian Voter ID cards (Election Photo Identity Card) issued by the
 * Election Commission of India.
 *
 * Confidence tiers:
 *  0.95 – "election commission" in anchorKeywords
 *  0.80 – lines contain "voter" AND EPIC number pattern found in upperLines
 *  0.70 – lines contain "epic"
 *  0.0  – none of the above
 */
object VoterIdParser : DocumentParser {

    override val country: DocumentCountry = DocumentCountry.IN
    override val docType: DocumentDocType = DocumentDocType.VOTER_ID

    // EPIC number: 3 uppercase letters + 7 digits
    private val RE_EPIC = Regex("""[A-Z]{3}[0-9]{7}""")

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    override fun confidence(signals: ClassificationSignals): Float {
        val hasElectionCommission = signals.anchorKeywords.contains("election commission")
        val hasVoter = signals.lines.any { it.contains("voter") }
        val hasEpic  = signals.lines.any { it.contains("epic") }
        val hasEpicPattern = signals.upperLines.any { RE_EPIC.containsMatchIn(it) }
        return when {
            hasElectionCommission     -> 0.95f
            hasVoter && hasEpicPattern -> 0.80f
            hasEpic                   -> 0.70f
            else                      -> 0.0f
        }
    }

    override fun extract(signals: ClassificationSignals): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()

        // ── 1. VOTER_ID_NUMBER (EPIC number) ──────────────────────────────────
        var voterIdExtracted = false
        var epicLineIdx = -1
        for ((idx, line) in signals.upperLines.withIndex()) {
            val match = RE_EPIC.find(line)
            if (match != null) {
                entities += ExtractedEntity(
                    type            = EntityType.VOTER_ID_NUMBER,
                    rawValue        = match.value,
                    normalizedValue = DocumentNormalizer.normalizeId(match.value),
                    confidence      = 0.9f,
                    sourceContext   = line
                )
                voterIdExtracted = true
                epicLineIdx = idx
                break
            }
        }

        // ── 2. FULL_NAME ──────────────────────────────────────────────────────
        var nameExtracted = false

        // Strategy A: line after "Name" keyword
        val nameLineIdx = signals.lines.indexOfFirst { it.contains("name") }
        if (nameLineIdx >= 0) {
            val raw = extractAfterKeyword(signals, nameLineIdx)
            if (raw.isNotBlank()) {
                entities += ExtractedEntity(
                    type            = EntityType.FULL_NAME,
                    rawValue        = raw,
                    normalizedValue = DocumentNormalizer.normalizeName(raw),
                    confidence      = 0.7f,
                    sourceContext   = signals.upperLines.getOrNull(nameLineIdx) ?: ""
                )
                nameExtracted = true
            }
        }

        // Strategy B: line immediately before the EPIC number
        if (!nameExtracted && epicLineIdx > 0) {
            val candidate = signals.upperLines.getOrNull(epicLineIdx - 1)?.trim() ?: ""
            if (candidate.isNotBlank() && candidate.none { it.isDigit() } && !RE_EPIC.containsMatchIn(candidate)) {
                entities += ExtractedEntity(
                    type            = EntityType.FULL_NAME,
                    rawValue        = candidate,
                    normalizedValue = DocumentNormalizer.normalizeName(candidate),
                    confidence      = 0.7f,
                    sourceContext   = candidate
                )
                nameExtracted = true
            }
        }

        // ── 3. DATE_OF_BIRTH ─────────────────────────────────────────────────
        val dobKeywords = listOf("dob", "date of birth")
        val dobLineIdx = signals.lines.indexOfFirst { line ->
            dobKeywords.any { kw -> line.contains(kw) }
        }
        if (dobLineIdx >= 0) {
            val candidates = listOfNotNull(
                signals.upperLines.getOrNull(dobLineIdx),
                signals.upperLines.getOrNull(dobLineIdx + 1)
            )
            for (cand in candidates) {
                val parsed = runCatching { DocumentNormalizer.parseDate(cand) }.getOrNull()
                if (parsed != null) {
                    entities += ExtractedEntity(
                        type            = EntityType.DATE_OF_BIRTH,
                        rawValue        = cand,
                        normalizedValue = parsed.format(ISO),
                        confidence      = 0.7f,
                        sourceContext   = cand
                    )
                    break
                }
            }
        }

        // ── 4. GENDER ────────────────────────────────────────────────────────
        for (line in signals.lines) {
            val upperLine = line.uppercase()
            if (upperLine.contains("MALE") || line.contains("male")) {
                val isFemale = upperLine.contains("FEMALE") || line.contains("female")
                val normalized = if (isFemale) "FEMALE" else "MALE"
                entities += ExtractedEntity(
                    type            = EntityType.GENDER,
                    rawValue        = if (isFemale) "FEMALE" else "MALE",
                    normalizedValue = normalized,
                    confidence      = 0.7f,
                    sourceContext   = line
                )
                break
            }
        }

        // ── 5. ADDRESS ────────────────────────────────────────────────────────
        val addrLineIdx = signals.lines.indexOfFirst { it.contains("address") }
        if (addrLineIdx >= 0) {
            val addrLines = (addrLineIdx + 1..minOf(addrLineIdx + 3, signals.upperLines.lastIndex))
                .mapNotNull { signals.upperLines.getOrNull(it)?.trim() }
                .filter { it.isNotBlank() }
            if (addrLines.isNotEmpty()) {
                val addressRaw = addrLines.joinToString(", ")
                entities += ExtractedEntity(
                    type            = EntityType.ADDRESS,
                    rawValue        = addressRaw,
                    normalizedValue = addressRaw,
                    confidence      = 0.5f,
                    sourceContext   = signals.upperLines.getOrNull(addrLineIdx) ?: ""
                )
            }
        }

        val partial = !voterIdExtracted || !nameExtracted

        val overallConfidence = if (entities.isEmpty()) 0.0f
            else entities.map { it.confidence }.average().toFloat()

        return ExtractionResult(
            entities          = entities,
            overallConfidence = overallConfidence,
            partial           = partial
        )
    }

    /**
     * Returns text after ":" on the same line, or the next non-blank line.
     */
    private fun extractAfterKeyword(signals: ClassificationSignals, lineIdx: Int): String {
        val upperLine = signals.upperLines.getOrNull(lineIdx) ?: return ""
        val afterColon = upperLine.substringAfter(":").trim()
        if (afterColon.isNotBlank()) return afterColon
        return signals.upperLines.getOrNull(lineIdx + 1)?.trim() ?: ""
    }
}
