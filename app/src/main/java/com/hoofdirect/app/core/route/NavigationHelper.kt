package com.hoofdirect.app.core.route

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.hoofdirect.app.core.route.model.RoutePlan
import com.hoofdirect.app.core.route.model.RouteStop
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper for launching external navigation apps.
 */
@Singleton
class NavigationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
        private const val WAZE_PACKAGE = "com.waze"
    }

    /**
     * Available navigation apps on the device.
     */
    enum class NavigationApp(val displayName: String, val packageName: String?) {
        GOOGLE_MAPS("Google Maps", GOOGLE_MAPS_PACKAGE),
        WAZE("Waze", WAZE_PACKAGE),
        DEFAULT("Default Maps", null)
    }

    /**
     * Get list of available navigation apps.
     */
    fun getAvailableApps(): List<NavigationApp> {
        val apps = mutableListOf<NavigationApp>()

        if (isAppInstalled(GOOGLE_MAPS_PACKAGE)) {
            apps.add(NavigationApp.GOOGLE_MAPS)
        }
        if (isAppInstalled(WAZE_PACKAGE)) {
            apps.add(NavigationApp.WAZE)
        }

        // Always add default as fallback
        apps.add(NavigationApp.DEFAULT)

        return apps
    }

    /**
     * Navigate to a single location.
     */
    fun navigateToLocation(
        latitude: Double,
        longitude: Double,
        label: String? = null,
        app: NavigationApp = NavigationApp.DEFAULT
    ): Intent {
        return when (app) {
            NavigationApp.GOOGLE_MAPS -> {
                val uri = Uri.parse("google.navigation:q=$latitude,$longitude")
                Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(GOOGLE_MAPS_PACKAGE)
                }
            }
            NavigationApp.WAZE -> {
                val uri = Uri.parse("https://waze.com/ul?ll=$latitude,$longitude&navigate=yes")
                Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(WAZE_PACKAGE)
                }
            }
            NavigationApp.DEFAULT -> {
                val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label ?: "")})")
                Intent(Intent.ACTION_VIEW, uri)
            }
        }
    }

    /**
     * Navigate to next stop in route.
     */
    fun navigateToStop(stop: RouteStop, app: NavigationApp = NavigationApp.DEFAULT): Intent {
        return navigateToLocation(
            latitude = stop.latitude,
            longitude = stop.longitude,
            label = stop.clientBusinessName ?: stop.clientName,
            app = app
        )
    }

    /**
     * Build a multi-stop route URL for Google Maps.
     * Google Maps supports up to 10 waypoints in a single navigation URL.
     */
    fun buildMultiStopRouteIntent(
        plan: RoutePlan,
        maxWaypoints: Int = 10
    ): Intent {
        // Google Maps URL format:
        // https://www.google.com/maps/dir/?api=1&origin=lat,lng&destination=lat,lng&waypoints=lat1,lng1|lat2,lng2|...

        val baseUrl = "https://www.google.com/maps/dir/?api=1"

        val origin = "${plan.startLatitude},${plan.startLongitude}"
        val destination = "${plan.endLatitude},${plan.endLongitude}"

        // Take only the first maxWaypoints - 2 stops (origin and destination are separate)
        val waypointStops = plan.stops.take(maxWaypoints)
        val waypoints = waypointStops.joinToString("|") { "${it.latitude},${it.longitude}" }

        val urlBuilder = StringBuilder(baseUrl)
        urlBuilder.append("&origin=$origin")
        urlBuilder.append("&destination=$destination")
        if (waypoints.isNotEmpty()) {
            urlBuilder.append("&waypoints=$waypoints")
        }
        urlBuilder.append("&travelmode=driving")

        val uri = Uri.parse(urlBuilder.toString())
        return Intent(Intent.ACTION_VIEW, uri).apply {
            // Prefer Google Maps if installed
            if (isAppInstalled(GOOGLE_MAPS_PACKAGE)) {
                setPackage(GOOGLE_MAPS_PACKAGE)
            }
        }
    }

    /**
     * Build navigation intent for the entire optimized route.
     * Uses Google Maps multi-stop directions if available.
     */
    fun startRouteNavigation(plan: RoutePlan): Intent {
        // For full route with all stops, use the multi-stop URL
        return buildMultiStopRouteIntent(plan)
    }

    /**
     * Open address in maps app for viewing (not navigation).
     */
    fun viewOnMap(
        latitude: Double,
        longitude: Double,
        label: String? = null
    ): Intent {
        val encodedLabel = Uri.encode(label ?: "")
        val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            if (isAppInstalled(GOOGLE_MAPS_PACKAGE)) {
                setPackage(GOOGLE_MAPS_PACKAGE)
            }
        }
    }

    /**
     * Search for an address in maps.
     */
    fun searchAddress(address: String): Intent {
        val encodedAddress = Uri.encode(address)
        val uri = Uri.parse("geo:0,0?q=$encodedAddress")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            if (isAppInstalled(GOOGLE_MAPS_PACKAGE)) {
                setPackage(GOOGLE_MAPS_PACKAGE)
            }
        }
    }

    /**
     * Check if an app is installed.
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Launch an intent safely, checking if it can be resolved.
     */
    fun canLaunchIntent(intent: Intent): Boolean {
        return intent.resolveActivity(context.packageManager) != null
    }
}
