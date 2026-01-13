package com.hoofdirect.app.feature.auth.domain

object PhoneValidator {

    private val US_PHONE_REGEX = Regex("""^(\+1)?[\s.-]?\(?[0-9]{3}\)?[\s.-]?[0-9]{3}[\s.-]?[0-9]{4}$""")

    fun validate(phone: String): Boolean {
        val digits = phone.filter { it.isDigit() }
        return digits.length == 10 || (digits.length == 11 && digits.startsWith("1"))
    }

    fun format(phone: String): String {
        val digits = phone.filter { it.isDigit() }.takeLast(10)
        return if (digits.length == 10) {
            "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
        } else {
            phone
        }
    }

    fun getError(phone: String): String? {
        return if (phone.isBlank()) {
            null
        } else if (!validate(phone)) {
            "Please enter a valid 10-digit phone number"
        } else {
            null
        }
    }
}
