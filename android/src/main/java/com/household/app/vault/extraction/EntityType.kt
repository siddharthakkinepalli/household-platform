package com.household.app.vault.extraction

enum class EntityType {
    // Identity
    FULL_NAME,
    GIVEN_NAMES,
    SURNAME,
    DATE_OF_BIRTH,
    GENDER,
    NATIONALITY,
    PLACE_OF_BIRTH,
    PLACE_OF_ISSUE,

    // Document identifiers
    DOCUMENT_NUMBER,
    PASSPORT_NUMBER,
    AADHAAR_NUMBER,
    PAN_NUMBER,
    VOTER_ID_NUMBER,
    OCI_NUMBER,
    DRIVING_LICENCE_NUMBER,

    // Dates
    EXPIRY_DATE,
    ISSUE_DATE,
    VALID_FROM,

    // Financial
    IBAN,
    POLICY_NUMBER,
    STEUERNUMMER,
    STEUER_ID,
    MONTHLY_COST,

    // Address / contact
    ADDRESS,
    EMPLOYER_NAME,

    // Generic fallback
    UNKNOWN_DATE,
    UNKNOWN_NUMBER,
    UNKNOWN_TEXT
}
