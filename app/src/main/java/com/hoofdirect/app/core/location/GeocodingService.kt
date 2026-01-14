package com.hoofdirect.app.core.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Result of a geocoding operation.
 */
data class GeocodingResult(
    val latitude: Double,
    val longitude: Double,
    val formattedAddress: String?,
    val city: String?,
    val state: String?,
    val zipCode: String?,
    val country: String?
)

/**
 * Service for converting addresses to GPS coordinates.
 */
@Singleton
class GeocodingService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val geocoder: Geocoder by lazy {
        Geocoder(context, Locale.US)
    }

    /**
     * Geocode an address to get GPS coordinates.
     * Returns null if geocoding fails.
     */
    suspend fun geocodeAddress(address: String): GeocodingResult? {
        if (address.isBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Use the new async API for Android 13+
                    geocodeAddressAsync(address)
                } else {
                    // Use the legacy synchronous API
                    geocodeAddressLegacy(address)
                }
            } catch (e: Exception) {
                // Log error but don't crash
                null
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun geocodeAddressLegacy(address: String): GeocodingResult? {
        val addresses = geocoder.getFromLocationName(address, 1)
        return addresses?.firstOrNull()?.let { addr ->
            GeocodingResult(
                latitude = addr.latitude,
                longitude = addr.longitude,
                formattedAddress = addr.getAddressLine(0),
                city = addr.locality ?: addr.subAdminArea,
                state = addr.adminArea,
                zipCode = addr.postalCode,
                country = addr.countryCode
            )
        }
    }

    private suspend fun geocodeAddressAsync(address: String): GeocodingResult? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null

        return suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocationName(address, 1) { addresses ->
                val result = addresses.firstOrNull()?.let { addr ->
                    GeocodingResult(
                        latitude = addr.latitude,
                        longitude = addr.longitude,
                        formattedAddress = addr.getAddressLine(0),
                        city = addr.locality ?: addr.subAdminArea,
                        state = addr.adminArea,
                        zipCode = addr.postalCode,
                        country = addr.countryCode
                    )
                }
                continuation.resume(result)
            }
        }
    }

    /**
     * Check if geocoding is available on this device.
     */
    fun isGeocodingAvailable(): Boolean {
        return Geocoder.isPresent()
    }
}
