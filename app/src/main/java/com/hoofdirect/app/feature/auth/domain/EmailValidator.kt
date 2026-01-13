package com.hoofdirect.app.feature.auth.domain

import android.util.Patterns

object EmailValidator {

    fun validate(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun getError(email: String): String? {
        return if (email.isBlank()) {
            null // Don't show error for empty field (user hasn't typed yet)
        } else if (!validate(email)) {
            "Please enter a valid email address"
        } else {
            null
        }
    }
}
