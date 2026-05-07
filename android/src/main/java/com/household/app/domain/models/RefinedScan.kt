package com.household.app.domain.models

import java.time.LocalDate

data class ScanField<T>(
    val value: T?,
    val confidence: Float
)

data class RefinedScan(
    val compositeConfidence: Float,
    val merchant: ScanField<String>,
    val amount: ScanField<Double>,
    val date: ScanField<LocalDate>
)
