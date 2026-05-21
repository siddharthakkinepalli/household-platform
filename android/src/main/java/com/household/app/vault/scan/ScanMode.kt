package com.household.app.vault.scan

enum class ScanMode {
    /** Thermal / inkjet receipts — contrast + denoise only, never perspective-warp. */
    RECEIPT,
    /** A4/letter documents — full geometric correction + binarization. */
    DOCUMENT
}
