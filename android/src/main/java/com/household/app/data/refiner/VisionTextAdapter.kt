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
            TextLinePayload(
                text = raw,
                confidence = 0f,
                boundingBoxTop = line.boundingBox?.top?.toFloat() ?: 0f,
                boundingBoxBottom = line.boundingBox?.bottom?.toFloat() ?: 0f
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
                confidence = 0f,
                boundingBoxTop = 0f,
                boundingBoxBottom = 0f
            )
        }

    return VisionTextPayload(
        blocks = listOf(TextBlockPayload(lines = fallbackLines)),
        fullText = text
    )
}
