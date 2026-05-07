package com.household.app.domain.services

import com.household.app.domain.models.RefinedScan
import com.household.app.domain.models.vault.VisionTextPayload

interface ReceiptRefiner {
    fun refine(visionText: VisionTextPayload): RefinedScan
}
