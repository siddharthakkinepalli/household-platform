package com.household.app.domain.models.vault

/**
 * Preset leaf folders under a top-level [VaultCategory] (and optional family member).
 */
enum class VaultSubFolder(
    val id: String,
    val label: String,
    val categories: Set<VaultCategory>
) {
    UNFILED("unfiled", "Unfiled", VaultCategory.entries.toSet()),

    // Identity
    PASSPORT("passport", "Passport", setOf(VaultCategory.IDENTITY)),
    NATIONAL_ID("national_id", "National ID card", setOf(VaultCategory.IDENTITY)),
    VISA("visa", "Visa", setOf(VaultCategory.IDENTITY)),
    EU_RESIDENCE("eu_residence", "EU residence permit", setOf(VaultCategory.IDENTITY)),
    EU_BLUE_CARD("eu_blue_card", "EU Blue Card", setOf(VaultCategory.IDENTITY)),
    PERMANENT_RESIDENCE("permanent_residence", "Permanent residence", setOf(VaultCategory.IDENTITY)),
    WORK_PERMIT("work_permit", "Work permit", setOf(VaultCategory.IDENTITY)),
    REGISTRATION("registration", "Registration (Anmeldung)", setOf(VaultCategory.IDENTITY)),
    DRIVING_LICENCE("driving_licence", "Driving licence", setOf(VaultCategory.IDENTITY)),
    BIRTH_CERTIFICATE("birth_certificate", "Birth certificate", setOf(VaultCategory.IDENTITY)),
    MARRIAGE_CERTIFICATE("marriage_certificate", "Marriage certificate", setOf(VaultCategory.IDENTITY)),
    OTHER_IDENTITY("other_identity", "Other identity", setOf(VaultCategory.IDENTITY)),

    // Contracts
    STREAMING("streaming", "Streaming", setOf(VaultCategory.CONTRACT)),
    MOBILE_INTERNET("mobile_internet", "Mobile & internet", setOf(VaultCategory.CONTRACT)),
    GYM("gym", "Gym & wellness", setOf(VaultCategory.CONTRACT)),
    SOFTWARE_CLOUD("software_cloud", "Software & cloud", setOf(VaultCategory.CONTRACT)),
    RENT_HOUSING("rent_housing", "Rent & housing", setOf(VaultCategory.CONTRACT)),
    OTHER_CONTRACT("other_contract", "Other contract", setOf(VaultCategory.CONTRACT)),

    // Insurance
    HEALTH_INSURANCE("health_insurance", "Health (GKV/PKV)", setOf(VaultCategory.INSURANCE)),
    DENTAL_INSURANCE("dental_insurance", "Dental", setOf(VaultCategory.INSURANCE)),
    LIABILITY("liability", "Liability (Haftpflicht)", setOf(VaultCategory.INSURANCE)),
    HOUSEHOLD_CONTENTS("household_contents", "Household contents", setOf(VaultCategory.INSURANCE)),
    TRAVEL_INSURANCE("travel_insurance", "Travel", setOf(VaultCategory.INSURANCE)),
    CAR_INSURANCE("car_insurance", "Car", setOf(VaultCategory.INSURANCE)),
    LIFE_INSURANCE("life_insurance", "Life", setOf(VaultCategory.INSURANCE)),
    OTHER_INSURANCE("other_insurance", "Other insurance", setOf(VaultCategory.INSURANCE)),

    // Property & housing
    LEASE("lease", "Lease / Mietvertrag", setOf(VaultCategory.PROPERTY)),
    UTILITY_ELECTRICITY("utility_electricity", "Electricity", setOf(VaultCategory.PROPERTY, VaultCategory.UTILITY_BILL)),
    UTILITY_GAS("utility_gas", "Gas", setOf(VaultCategory.PROPERTY, VaultCategory.UTILITY_BILL)),
    UTILITY_WATER("utility_water", "Water", setOf(VaultCategory.PROPERTY, VaultCategory.UTILITY_BILL)),
    INTERNET("internet", "Internet", setOf(VaultCategory.PROPERTY, VaultCategory.UTILITY_BILL)),
    DEPOSIT("deposit", "Deposit (Kaution)", setOf(VaultCategory.PROPERTY)),
    HANDOVER("handover", "Handover (Übergabe)", setOf(VaultCategory.PROPERTY)),
    OTHER_PROPERTY("other_property", "Other property", setOf(VaultCategory.PROPERTY, VaultCategory.UTILITY_BILL)),

    // Medical
    INSURANCE_CARD("insurance_card", "Insurance card", setOf(VaultCategory.MEDICAL)),
    VACCINATION("vaccination", "Vaccination", setOf(VaultCategory.MEDICAL)),
    PRESCRIPTIONS("prescriptions", "Prescriptions", setOf(VaultCategory.MEDICAL)),
    LAB_RESULTS("lab_results", "Lab results", setOf(VaultCategory.MEDICAL)),
    MEDICAL_DENTAL("medical_dental", "Dental", setOf(VaultCategory.MEDICAL)),
    OTHER_MEDICAL("other_medical", "Other medical", setOf(VaultCategory.MEDICAL)),

    // Other / receipts
    OTHER("other", "Other", setOf(VaultCategory.OTHER)),
    RECEIPTS_GENERAL("receipts", "Receipts", setOf(VaultCategory.RECEIPT));

    companion object {
        fun forCategory(category: VaultCategory): List<VaultSubFolder> =
            entries.filter { category in it.categories && it != UNFILED }
                .sortedBy { it.label }

        fun fromId(id: String?): VaultSubFolder =
            entries.firstOrNull { it.id == normalizeId(id) } ?: UNFILED

        fun normalizeId(id: String?): String =
            when {
                id.isNullOrBlank() -> UNFILED.id
                else -> id
            }
    }
}
