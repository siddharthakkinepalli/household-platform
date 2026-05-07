package com.household.app.domain.services

import com.household.app.domain.models.RefinedScan

data class VisionTextPayload(
    val lines: List<String>
)

interface ReceiptRefiner {
    fun refine(visionText: VisionTextPayload): RefinedScan
}
