package com.household.app.data.refiner

import com.google.mlkit.vision.text.Text
import com.household.app.domain.models.vault.TextBlockPayload
import com.household.app.domain.models.vault.TextLinePayload
import com.household.app.domain.models.vault.VisionTextPayload

fun Text.toVisionTextPayload(): VisionTextPayload {
    val payloadBlocks = textBlocks.mapNotNull { block ->
        val lines = block.lines.mapNotNull lineMapper@{ line ->
            val raw = line.text.trim()
            if (raw.isBlank()) return@lineMapper null

            // Aggregate word-level confidence from each TextElement
            val wordConfidences = line.elements.map { el -> el.confidence }.filter { it > 0f }
            val lineConfidence = if (wordConfidences.isNotEmpty()) {
                wordConfidences.average().toFloat()
            } else {
                estimateConfidence(raw)
            }

            val box = line.boundingBox
            TextLinePayload(
                text = raw,
                confidence = lineConfidence,
                boundingBoxTop = box?.top?.toFloat() ?: 0f,
                boundingBoxBottom = box?.bottom?.toFloat() ?: 0f,
                boundingBoxLeft = box?.left?.toFloat() ?: 0f,
                boundingBoxRight = box?.right?.toFloat() ?: 0f
            )
        }
        if (lines.isEmpty()) null else TextBlockPayload(lines = lines)
    }

    if (payloadBlocks.isNotEmpty()) {
        return VisionTextPayload(blocks = payloadBlocks, fullText = text)
    }

    val fallbackLines = text
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map {
            TextLinePayload(
                text = it,
                confidence = estimateConfidence(it),
                boundingBoxTop = 0f,
                boundingBoxBottom = 0f
            )
        }

    return VisionTextPayload(
        blocks = listOf(TextBlockPayload(lines = fallbackLines)),
        fullText = text
    )
}

/**
 * Heuristic confidence estimate when ML Kit provides no word-level scores.
 * Based on alphanumeric ratio and absence of common OCR noise characters.
 */
private fun estimateConfidence(text: String): Float {
    if (text.isBlank()) return 0f
    val alphanum = text.count { it.isLetterOrDigit() }.toFloat()
    val ratio = alphanum / text.length
    val noiseChars = text.count { it in "|\\~^`_" }
    val noisePenalty = (noiseChars * 0.05f).coerceAtMost(0.3f)
    return (ratio * 0.85f - noisePenalty).coerceIn(0f, 1f)
}
