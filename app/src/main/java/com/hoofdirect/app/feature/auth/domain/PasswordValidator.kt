package com.hoofdirect.app.feature.auth.domain

import com.hoofdirect.app.feature.auth.domain.model.PasswordStrength
import com.hoofdirect.app.feature.auth.domain.model.PasswordValidationResult

object PasswordValidator {

    private const val MIN_LENGTH = 8
    private const val STRONG_LENGTH = 12

    fun validate(password: String): PasswordValidationResult {
        val errors = mutableListOf<String>()

        if (password.length < MIN_LENGTH) {
            errors.add("Password must be at least $MIN_LENGTH characters")
        }
        if (!password.any { it.isUpperCase() }) {
            errors.add("Password must contain an uppercase letter")
        }
        if (!password.any { it.isLowerCase() }) {
            errors.add("Password must contain a lowercase letter")
        }
        if (!password.any { it.isDigit() }) {
            errors.add("Password must contain a number")
        }

        return PasswordValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            strength = calculateStrength(password)
        )
    }

    private fun calculateStrength(password: String): PasswordStrength {
        var score = 0

        if (password.length >= MIN_LENGTH) score++
        if (password.length >= STRONG_LENGTH) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        return when {
            score <= 2 -> PasswordStrength.WEAK
            score <= 4 -> PasswordStrength.FAIR
            score <= 5 -> PasswordStrength.GOOD
            else -> PasswordStrength.STRONG
        }
    }

    fun getStrengthProgress(strength: PasswordStrength): Float = when (strength) {
        PasswordStrength.WEAK -> 0.2f
        PasswordStrength.FAIR -> 0.5f
        PasswordStrength.GOOD -> 0.75f
        PasswordStrength.STRONG -> 1.0f
    }
}
