package com.hoofdirect.app.feature.onboarding.model

/**
 * Data collected during profile setup step of onboarding.
 */
data class ProfileSetupData(
    val businessName: String = "",
    val phone: String = "",
    val homeAddress: String = "",
    val homeLatitude: Double? = null,
    val homeLongitude: Double? = null
) {
    /**
     * Whether all required fields are valid.
     */
    val isValid: Boolean
        get() = businessName.isNotBlank() &&
                phone.isNotBlank() &&
                phone.isValidPhone() &&
                homeAddress.isNotBlank()

    /**
     * List of current validation errors.
     */
    val validationErrors: List<ProfileFieldError>
        get() = buildList {
            if (businessName.isBlank()) add(ProfileFieldError.BUSINESS_NAME_REQUIRED)
            if (phone.isBlank()) add(ProfileFieldError.PHONE_REQUIRED)
            else if (!phone.isValidPhone()) add(ProfileFieldError.PHONE_INVALID)
            if (homeAddress.isBlank()) add(ProfileFieldError.ADDRESS_REQUIRED)
        }

    /**
     * Check if a specific field has an error.
     */
    fun hasError(field: ProfileFieldError): Boolean = validationErrors.contains(field)
}

/**
 * Validation errors for profile setup fields.
 */
enum class ProfileFieldError(val message: String) {
    BUSINESS_NAME_REQUIRED("Business name is required"),
    PHONE_REQUIRED("Phone number is required"),
    PHONE_INVALID("Please enter a valid phone number"),
    ADDRESS_REQUIRED("Home address is required for route planning"),
    ADDRESS_NOT_GEOCODED("Please select an address from the suggestions")
}

/**
 * Check if a string is a valid US phone number.
 */
private fun String.isValidPhone(): Boolean {
    val digitsOnly = this.filter { it.isDigit() }
    return digitsOnly.length == 10 || (digitsOnly.length == 11 && digitsOnly.startsWith("1"))
}

/**
 * Format a phone number as (XXX) XXX-XXXX.
 */
fun formatPhoneNumber(input: String): String {
    val digits = input.filter { it.isDigit() }.take(10)
    return when {
        digits.length <= 3 -> digits
        digits.length <= 6 -> "(${digits.substring(0, 3)}) ${digits.substring(3)}"
        else -> "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
    }
}
