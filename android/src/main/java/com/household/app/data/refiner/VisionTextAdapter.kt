package com.household.app.data.refiner

import com.google.mlkit.vision.text.Text
import com.household.app.domain.services.VisionTextPayload

fun Text.toVisionTextPayload(): VisionTextPayload {
    val extractedLines = textBlocks
        .flatMap { block -> block.lines.map { line -> line.text } }
        .map { it.trim() }
        .filter { it.isNotBlank() }

    if (extractedLines.isNotEmpty()) {
        return VisionTextPayload(lines = extractedLines)
    }

    val fallback = text
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }

    return VisionTextPayload(lines = fallback)
}
