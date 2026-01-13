# Hoof Direct: Android App Development Plan

*Technical specification for the native Android application*

---

## 1. Overview

### App Identity

| Attribute | Value |
|-----------|-------|
| App Name | Hoof Direct |
| Package Name | `com.hoofdirect.app` |
| Min SDK | 26 (Android 8.0 Oreo) |
| Target SDK | 34 (Android 14) |
| Language | Kotlin 1.9+ |
| UI Framework | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |

### Why These SDK Choices

| SDK Level | Android Version | US Market Share | Rationale |
|-----------|-----------------|-----------------|-----------|
| 26 (min) | 8.0 Oreo | 97%+ coverage | WorkManager support, modern APIs |
| 34 (target) | 14 | Current | Latest features, Play Store requirement |

Farriers tend toward budget/mid-range Android phones (price-conscious demographic). SDK 26 ensures broad compatibility while enabling modern architecture.

---

## 2. Project Structure

```
com.hoofdirect.app/
├── HoofDirectApplication.kt          # Application class, Hilt setup
├── MainActivity.kt                   # Single activity, Compose host
│
├── core/                             # Shared utilities and base classes
│   ├── database/                     # Room database setup
│   │   ├── HoofDirectDatabase.kt
│   │   ├── Converters.kt            # Type converters (JSON, dates)
│   │   └── migrations/              # Database migrations
│   ├── network/                      # Networking setup
│   │   ├── SupabaseClient.kt
│   │   ├── AuthInterceptor.kt
│   │   └── NetworkMonitor.kt
│   ├── sync/                         # Offline sync engine
│   │   ├── SyncManager.kt
│   │   ├── SyncWorker.kt
│   │   └── ConflictResolver.kt
│   ├── di/                           # Hilt modules
│   │   ├── DatabaseModule.kt
│   │   ├── NetworkModule.kt
│   │   └── RepositoryModule.kt
│   └── util/                         # Extensions, helpers
│       ├── DateExtensions.kt
│       ├── FlowExtensions.kt
│       └── ResourceState.kt
│
├── designsystem/                     # Material 3 theme and components
│   ├── theme/
│   │   ├── Theme.kt                 # Dynamic color + fallback
│   │   ├── Color.kt
│   │   ├── Typography.kt
│   │   └── Shapes.kt
│   └── components/                   # Reusable UI components
│       ├── HdTopAppBar.kt
│       ├── HdCard.kt
│       ├── HdButton.kt
│       ├── HdTextField.kt
│       ├── HdBottomSheet.kt
│       ├── HdEmptyState.kt
│       ├── HdLoadingIndicator.kt
│       └── HdSyncStatusBanner.kt
│
├── navigation/                       # App navigation
│   ├── HoofDirectNavHost.kt
│   ├── NavDestinations.kt
│   └── BottomNavBar.kt
│
├── feature/                          # Feature modules
│   ├── auth/
│   │   ├── data/
│   │   │   ├── AuthRepository.kt
│   │   │   └── AuthRemoteDataSource.kt
│   │   ├── domain/
│   │   │   ├── model/User.kt
│   │   │   └── usecase/
│   │   │       ├── SignInUseCase.kt
│   │   │       ├── SignUpUseCase.kt
│   │   │       └── SignOutUseCase.kt
│   │   └── ui/
│   │       ├── SignInScreen.kt
│   │       ├── SignUpScreen.kt
│   │       ├── ForgotPasswordScreen.kt
│   │       └── AuthViewModel.kt
│   │
│   ├── clients/
│   │   ├── data/
│   │   │   ├── local/
│   │   │   │   ├── ClientDao.kt
│   │   │   │   └── ClientEntity.kt
│   │   │   ├── remote/
│   │   │   │   └── ClientRemoteDataSource.kt
│   │   │   └── ClientRepository.kt
│   │   ├── domain/
│   │   │   ├── model/Client.kt
│   │   │   └── usecase/
│   │   │       ├── GetClientsUseCase.kt
│   │   │       ├── GetClientByIdUseCase.kt
│   │   │       ├── CreateClientUseCase.kt
│   │   │       ├── UpdateClientUseCase.kt
│   │   │       └── DeleteClientUseCase.kt
│   │   └── ui/
│   │       ├── list/
│   │       │   ├── ClientListScreen.kt
│   │       │   └── ClientListViewModel.kt
│   │       └── detail/
│   │           ├── ClientDetailScreen.kt
│   │           ├── ClientEditScreen.kt
│   │           └── ClientDetailViewModel.kt
│   │
│   ├── horses/
│   │   ├── data/
│   │   ├── domain/
│   │   └── ui/
│   │
│   ├── appointments/
│   │   ├── data/
│   │   ├── domain/
│   │   └── ui/
│   │       ├── calendar/
│   │       │   ├── CalendarScreen.kt
│   │       │   ├── DayView.kt
│   │       │   ├── WeekView.kt
│   │       │   ├── AgendaView.kt
│   │       │   └── CalendarViewModel.kt
│   │       └── detail/
│   │           ├── AppointmentDetailScreen.kt
│   │           └── AppointmentEditScreen.kt
│   │
│   ├── routes/
│   │   ├── data/
│   │   │   ├── RouteRepository.kt
│   │   │   └── GoogleRoutesDataSource.kt
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── Route.kt
│   │   │   │   └── RouteStop.kt
│   │   │   └── usecase/
│   │   │       ├── OptimizeRouteUseCase.kt
│   │   │       └── GetRouteForDayUseCase.kt
│   │   └── ui/
│   │       ├── RouteMapScreen.kt
│   │       ├── RouteListScreen.kt
│   │       └── RouteViewModel.kt
│   │
│   ├── invoices/
│   │   ├── data/
│   │   ├── domain/
│   │   ├── ui/
│   │   └── pdf/
│   │       └── InvoicePdfGenerator.kt
│   │
│   ├── mileage/
│   │   ├── data/
│   │   ├── domain/
│   │   ├── ui/
│   │   └── tracking/
│   │       ├── MileageTrackingService.kt
│   │       └── GeofenceManager.kt
│   │
│   ├── settings/
│   │   ├── data/
│   │   ├── domain/
│   │   └── ui/
│   │
│   └── onboarding/
│       └── ui/
│           ├── OnboardingScreen.kt
│           └── OnboardingViewModel.kt
│
└── service/                          # Background services
    ├── SyncService.kt
    ├── ReminderScheduler.kt
    └── CalendarSyncService.kt
```

---

## 3. Dependencies

### build.gradle.kts (app module)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.hoofdirect.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hoofdirect.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase config (from local.properties or CI secrets)
        buildConfigField("String", "SUPABASE_URL", "\"${project.findProperty("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${project.findProperty("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"${project.findProperty("GOOGLE_MAPS_API_KEY")}\"")
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:2.2.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-android:2.3.8")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Google Maps
    implementation("com.google.maps.android:maps-compose:4.3.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Google Places (address autocomplete)
    implementation("com.google.android.libraries.places:places:3.3.0")

    // WorkManager (background sync)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // DataStore (preferences)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coil (image loading)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Calendar
    implementation("com.kizitonwose.calendar:compose:2.5.0")

    // PDF Generation
    implementation("com.itextpdf:itext7-core:7.2.5")

    // Phone number formatting
    implementation("io.michaelrocks:libphonenumber-android:8.13.27")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

---

## 4. Material 3 Theme Implementation

### Theme.kt

```kotlin
package com.hoofdirect.app.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Branded colors (fallback for Android 11 and below)
private val HdLightColorScheme = lightColorScheme(
    primary = HdGreen,                    // Route actions, primary buttons
    onPrimary = HdOnGreen,
    primaryContainer = HdGreenContainer,
    onPrimaryContainer = HdOnGreenContainer,
    secondary = HdBrown,                  // Client/horse accents
    onSecondary = HdOnBrown,
    secondaryContainer = HdBrownContainer,
    onSecondaryContainer = HdOnBrownContainer,
    tertiary = HdBlue,                    // Financial elements
    onTertiary = HdOnBlue,
    tertiaryContainer = HdBlueContainer,
    onTertiaryContainer = HdOnBlueContainer,
    error = HdError,
    onError = HdOnError,
    errorContainer = HdErrorContainer,
    onErrorContainer = HdOnErrorContainer,
    background = HdBackground,
    onBackground = HdOnBackground,
    surface = HdSurface,
    onSurface = HdOnSurface,
    surfaceVariant = HdSurfaceVariant,
    onSurfaceVariant = HdOnSurfaceVariant,
    outline = HdOutline,
    outlineVariant = HdOutlineVariant,
)

private val HdDarkColorScheme = darkColorScheme(
    primary = HdGreenDark,
    onPrimary = HdOnGreenDark,
    primaryContainer = HdGreenContainerDark,
    onPrimaryContainer = HdOnGreenContainerDark,
    secondary = HdBrownDark,
    onSecondary = HdOnBrownDark,
    secondaryContainer = HdBrownContainerDark,
    onSecondaryContainer = HdOnBrownContainerDark,
    tertiary = HdBlueDark,
    onTertiary = HdOnBlueDark,
    tertiaryContainer = HdBlueContainerDark,
    onTertiaryContainer = HdOnBlueContainerDark,
    error = HdErrorDark,
    onError = HdOnErrorDark,
    errorContainer = HdErrorContainerDark,
    onErrorContainer = HdOnErrorContainerDark,
    background = HdBackgroundDark,
    onBackground = HdOnBackgroundDark,
    surface = HdSurfaceDark,
    onSurface = HdOnSurfaceDark,
    surfaceVariant = HdSurfaceVariantDark,
    onSurfaceVariant = HdOnSurfaceVariantDark,
    outline = HdOutlineDark,
    outlineVariant = HdOutlineVariantDark,
)

@Composable
fun HoofDirectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Enable Material You on Android 12+
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic color available on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> HdDarkColorScheme
        else -> HdLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HdTypography,
        shapes = HdShapes,
        content = content
    )
}
```

### Color.kt

```kotlin
package com.hoofdirect.app.designsystem.theme

import androidx.compose.ui.graphics.Color

// Primary - Green (routes, navigation, primary actions)
val HdGreen = Color(0xFF2E7D32)           // Green 800
val HdOnGreen = Color(0xFFFFFFFF)
val HdGreenContainer = Color(0xFFC8E6C9)   // Green 100
val HdOnGreenContainer = Color(0xFF1B5E20) // Green 900

// Secondary - Brown (clients, horses, earth tones)
val HdBrown = Color(0xFF6D4C41)           // Brown 600
val HdOnBrown = Color(0xFFFFFFFF)
val HdBrownContainer = Color(0xFFD7CCC8)   // Brown 100
val HdOnBrownContainer = Color(0xFF3E2723) // Brown 900

// Tertiary - Blue (financial, invoices)
val HdBlue = Color(0xFF1565C0)            // Blue 800
val HdOnBlue = Color(0xFFFFFFFF)
val HdBlueContainer = Color(0xFFBBDEFB)    // Blue 100
val HdOnBlueContainer = Color(0xFF0D47A1)  // Blue 900

// Error
val HdError = Color(0xFFB00020)
val HdOnError = Color(0xFFFFFFFF)
val HdErrorContainer = Color(0xFFFCD8DF)
val HdOnErrorContainer = Color(0xFF8C0016)

// Neutrals - Light
val HdBackground = Color(0xFFFFFBFE)
val HdOnBackground = Color(0xFF1C1B1F)
val HdSurface = Color(0xFFFFFBFE)
val HdOnSurface = Color(0xFF1C1B1F)
val HdSurfaceVariant = Color(0xFFE7E0EC)
val HdOnSurfaceVariant = Color(0xFF49454F)
val HdOutline = Color(0xFF79747E)
val HdOutlineVariant = Color(0xFFCAC4D0)

// Dark theme variants
val HdGreenDark = Color(0xFF81C784)
val HdOnGreenDark = Color(0xFF003300)
val HdGreenContainerDark = Color(0xFF1B5E20)
val HdOnGreenContainerDark = Color(0xFFC8E6C9)

val HdBrownDark = Color(0xFFBCAAA4)
val HdOnBrownDark = Color(0xFF3E2723)
val HdBrownContainerDark = Color(0xFF4E342E)
val HdOnBrownContainerDark = Color(0xFFD7CCC8)

val HdBlueDark = Color(0xFF90CAF9)
val HdOnBlueDark = Color(0xFF0D47A1)
val HdBlueContainerDark = Color(0xFF1565C0)
val HdOnBlueContainerDark = Color(0xFFBBDEFB)

val HdErrorDark = Color(0xFFCF6679)
val HdOnErrorDark = Color(0xFF000000)
val HdErrorContainerDark = Color(0xFF8C0016)
val HdOnErrorContainerDark = Color(0xFFFCD8DF)

val HdBackgroundDark = Color(0xFF1C1B1F)
val HdOnBackgroundDark = Color(0xFFE6E1E5)
val HdSurfaceDark = Color(0xFF1C1B1F)
val HdOnSurfaceDark = Color(0xFFE6E1E5)
val HdSurfaceVariantDark = Color(0xFF49454F)
val HdOnSurfaceVariantDark = Color(0xFFCAC4D0)
val HdOutlineDark = Color(0xFF938F99)
val HdOutlineVariantDark = Color(0xFF49454F)

// Semantic colors (same in both themes)
val HdSuccess = Color(0xFF4CAF50)
val HdWarning = Color(0xFFFFC107)
val HdInfo = Color(0xFF2196F3)

// Appointment status colors
val HdStatusScheduled = Color(0xFF2196F3)    // Blue
val HdStatusConfirmed = Color(0xFF4CAF50)    // Green
val HdStatusCompleted = Color(0xFF9E9E9E)    // Grey
val HdStatusCancelled = Color(0xFFFF9800)    // Orange
val HdStatusNoShow = Color(0xFFF44336)       // Red
```

### Typography.kt

```kotlin
package com.hoofdirect.app.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hoofdirect.app.R

// Using system default for better readability
// Can swap to custom font later (e.g., Inter, Roboto Flex)
val HdFontFamily = FontFamily.Default

val HdTypography = Typography(
    // Display - Large numbers, dashboard metrics
    displayLarge = TextStyle(
        fontFamily = HdFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = HdFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = HdFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),

    // Headline - Section headers
    headlineLarge = TextStyle(
        fontFamily = HdFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = HdFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = HdFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    // Title - Card titles, list primaries
    titleLarge = TextStyle(
        fontFamily = HdFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = HdFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = HdFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // Body - Content text
    bodyLarge = TextStyle(
        fontFamily = HdFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = HdFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = HdFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // Label - Buttons, chips, captions
    labelLarge = TextStyle(
        fontFamily = HdFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = HdFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = HdFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

---

## 5. Navigation Architecture

### Bottom Navigation Structure

```
┌─────────────────────────────────────────────────────────────────┐
│                     NAVIGATION STRUCTURE                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  BOTTOM NAV TABS                                                │
│  ├─ Schedule (home)                                             │
│  │   ├─ CalendarScreen (agenda/day/week views)                  │
│  │   ├─ AppointmentDetailScreen                                 │
│  │   └─ AppointmentEditScreen                                   │
│  │                                                              │
│  ├─ Clients                                                     │
│  │   ├─ ClientListScreen                                        │
│  │   ├─ ClientDetailScreen                                      │
│  │   │   └─ HorseDetailScreen (nested)                          │
│  │   └─ ClientEditScreen                                        │
│  │                                                              │
│  ├─ Routes                                                      │
│  │   ├─ RouteMapScreen                                          │
│  │   └─ RouteOptimizeScreen                                     │
│  │                                                              │
│  ├─ Invoices                                                    │
│  │   ├─ InvoiceListScreen                                       │
│  │   ├─ InvoiceDetailScreen                                     │
│  │   └─ InvoiceEditScreen                                       │
│  │                                                              │
│  └─ More                                                        │
│      ├─ SettingsScreen                                          │
│      ├─ ProfileScreen                                           │
│      ├─ MileageScreen                                           │
│      ├─ ReportsScreen                                           │
│      ├─ SubscriptionScreen                                      │
│      └─ HelpScreen                                              │
│                                                                 │
│  MODAL/OVERLAY SCREENS (not in nav stack)                       │
│  ├─ QuickAddAppointmentSheet (bottom sheet)                     │
│  ├─ AppointmentCompleteSheet (bottom sheet)                     │
│  ├─ SearchScreen (full screen overlay)                          │
│  └─ SyncStatusSheet (bottom sheet)                              │
│                                                                 │
│  AUTH FLOW (separate nav graph)                                 │
│  ├─ SignInScreen                                                │
│  ├─ SignUpScreen                                                │
│  ├─ ForgotPasswordScreen                                        │
│  └─ OnboardingScreen                                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### NavDestinations.kt

```kotlin
package com.hoofdirect.app.navigation

import kotlinx.serialization.Serializable

sealed interface NavDestination {

    // Auth flow
    @Serializable data object SignIn : NavDestination
    @Serializable data object SignUp : NavDestination
    @Serializable data object ForgotPassword : NavDestination
    @Serializable data object Onboarding : NavDestination

    // Main tabs
    @Serializable data object Schedule : NavDestination
    @Serializable data object Clients : NavDestination
    @Serializable data object Routes : NavDestination
    @Serializable data object Invoices : NavDestination
    @Serializable data object More : NavDestination

    // Detail screens
    @Serializable data class AppointmentDetail(val id: String) : NavDestination
    @Serializable data class AppointmentEdit(val id: String? = null) : NavDestination
    @Serializable data class ClientDetail(val id: String) : NavDestination
    @Serializable data class ClientEdit(val id: String? = null) : NavDestination
    @Serializable data class HorseDetail(val id: String) : NavDestination
    @Serializable data class HorseEdit(val clientId: String, val horseId: String? = null) : NavDestination
    @Serializable data class InvoiceDetail(val id: String) : NavDestination
    @Serializable data class InvoiceEdit(val id: String? = null, val appointmentId: String? = null) : NavDestination

    // More section
    @Serializable data object Settings : NavDestination
    @Serializable data object Profile : NavDestination
    @Serializable data object Mileage : NavDestination
    @Serializable data object Reports : NavDestination
    @Serializable data object Subscription : NavDestination
    @Serializable data object Help : NavDestination
}
```

---

## 6. Room Database Schema

### HoofDirectDatabase.kt

```kotlin
package com.hoofdirect.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hoofdirect.app.feature.appointments.data.local.*
import com.hoofdirect.app.feature.clients.data.local.*
import com.hoofdirect.app.feature.horses.data.local.*
import com.hoofdirect.app.feature.invoices.data.local.*
import com.hoofdirect.app.feature.mileage.data.local.*
import com.hoofdirect.app.feature.routes.data.local.*
import com.hoofdirect.app.core.sync.SyncQueueEntity

@Database(
    entities = [
        UserEntity::class,
        ClientEntity::class,
        HorseEntity::class,
        AppointmentEntity::class,
        AppointmentHorseEntity::class,
        InvoiceEntity::class,
        ServicePriceEntity::class,
        MileageLogEntity::class,
        RoutePlanEntity::class,
        SyncQueueEntity::class,
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class HoofDirectDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun clientDao(): ClientDao
    abstract fun horseDao(): HorseDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun servicePriceDao(): ServicePriceDao
    abstract fun mileageLogDao(): MileageLogDao
    abstract fun routePlanDao(): RoutePlanDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        const val DATABASE_NAME = "hoof_direct_db"
    }
}
```

### ClientEntity.kt (Example Entity)

```kotlin
package com.hoofdirect.app.feature.clients.data.local

import androidx.room.*
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "clients",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["name"]),
        Index(value = ["is_active"])
    ]
)
data class ClientEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "user_id")
    val userId: String,

    val name: String,

    val email: String?,

    val phone: String,

    val address: String,

    val city: String,

    val state: String,

    val zip: String,

    val latitude: Double?,

    val longitude: Double?,

    @ColumnInfo(name = "access_notes")
    val accessNotes: String?,

    @ColumnInfo(name = "general_notes")
    val generalNotes: String?,

    @ColumnInfo(name = "custom_pricing")
    val customPricing: String?, // JSON string

    @ColumnInfo(name = "reminder_preference")
    val reminderPreference: String = "sms", // sms, email, both, none

    @ColumnInfo(name = "reminder_hours_before")
    val reminderHoursBefore: Int = 24,

    @ColumnInfo(name = "requires_confirmation")
    val requiresConfirmation: Boolean = false,

    @ColumnInfo(name = "preferred_days")
    val preferredDays: String?, // JSON array

    @ColumnInfo(name = "preferred_time_start")
    val preferredTimeStart: String?,

    @ColumnInfo(name = "preferred_time_end")
    val preferredTimeEnd: String?,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),

    // Sync tracking
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.SYNCED,

    @ColumnInfo(name = "server_updated_at")
    val serverUpdatedAt: Instant? = null
)

enum class SyncStatus {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    CONFLICT
}
```

---

## 7. Offline Sync Architecture

### Sync Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                    OFFLINE SYNC STRATEGY                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  WRITE PATH (User creates/updates/deletes)                      │
│  ─────────────────────────────────────────                      │
│  1. Write to Room immediately                                   │
│  2. Set syncStatus = PENDING_*                                  │
│  3. Add entry to sync_queue table                               │
│  4. UI updates instantly (optimistic)                           │
│  5. SyncWorker processes queue when online                      │
│                                                                 │
│  READ PATH (User views data)                                    │
│  ────────────────────────────                                   │
│  1. Always read from Room (single source of truth)              │
│  2. Background sync pulls latest from server                    │
│  3. Merge with conflict resolution                              │
│  4. UI observes Room via Flow (auto-updates)                    │
│                                                                 │
│  SYNC TRIGGERS                                                  │
│  ─────────────                                                  │
│  • App foreground (immediate)                                   │
│  • Network connectivity restored (immediate)                    │
│  • WorkManager periodic (every 15 min when backgrounded)        │
│  • Manual pull-to-refresh                                       │
│                                                                 │
│  CONFLICT RESOLUTION                                            │
│  ────────────────────                                           │
│  • Last-write-wins based on updated_at timestamp                │
│  • Server timestamp is authoritative                            │
│  • If local change is newer, push to server                     │
│  • If server change is newer, overwrite local                   │
│  • Deletes always win (soft delete preserves data)              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### SyncManager.kt

```kotlin
package com.hoofdirect.app.core.sync

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val syncQueueDao: SyncQueueDao
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: Flow<SyncState> = _syncState.asStateFlow()

    private val workManager = WorkManager.getInstance(context)

    fun initialize() {
        // Schedule periodic sync
        val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSyncRequest
        )
    }

    suspend fun syncNow() {
        if (!networkMonitor.isOnline()) {
            _syncState.value = SyncState.Offline
            return
        }

        _syncState.value = SyncState.Syncing

        val oneTimeSyncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            ONE_TIME_SYNC_WORK,
            ExistingWorkPolicy.REPLACE,
            oneTimeSyncRequest
        )
    }

    fun getPendingChangesCount(): Flow<Int> = syncQueueDao.getPendingCount()

    companion object {
        private const val PERIODIC_SYNC_WORK = "periodic_sync"
        private const val ONE_TIME_SYNC_WORK = "one_time_sync"
    }
}

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data object Offline : SyncState
    data class Error(val message: String) : SyncState
    data class Success(val timestamp: Long) : SyncState
}
```

---

## 8. Android Permissions & Manifest

### AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Network -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- Location (for routes, mileage tracking) -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

    <!-- Calendar sync -->
    <uses-permission android:name="android.permission.READ_CALENDAR" />
    <uses-permission android:name="android.permission.WRITE_CALENDAR" />

    <!-- Notifications -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Background work -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

    <!-- Camera (for horse photos) -->
    <uses-permission android:name="android.permission.CAMERA" />

    <!-- Phone (for click-to-call) -->
    <uses-permission android:name="android.permission.CALL_PHONE" />

    <application
        android:name=".HoofDirectApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.HoofDirect"
        android:enableOnBackInvokedCallback="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.HoofDirect">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Google Maps API Key -->
        <meta-data
            android:name="com.google.android.geo.API_KEY"
            android:value="${GOOGLE_MAPS_API_KEY}" />

        <!-- Firebase Cloud Messaging -->
        <service
            android:name=".service.HdFirebaseMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>

        <!-- Mileage tracking foreground service -->
        <service
            android:name=".feature.mileage.tracking.MileageTrackingService"
            android:foregroundServiceType="location"
            android:exported="false" />

        <!-- WorkManager initialization -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>

    </application>

</manifest>
```

### Permission Request Strategy

| Permission | When Requested | Rationale Shown |
|------------|----------------|-----------------|
| Location (fine) | First route view or mileage tracking enable | "To optimize your route and track mileage" |
| Location (background) | Mileage auto-tracking enable | "To track miles when app is in background" |
| Calendar | First calendar sync enable | "To sync appointments with your calendar" |
| Notifications | After onboarding | "To remind you of upcoming appointments" |
| Camera | First horse photo | "To take photos of horses" |
| Phone | First client call tap | "To call clients directly" |

---

## 9. Screen Specifications

### Schedule Screen (Home Tab)

```
┌─────────────────────────────────────────────────────────────────┐
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │  LargeTopAppBar                                             │ │
│ │  ┌─────────────────────────────────────────────────────────┐│ │
│ │  │  Today                                    [Search] [Sync]││ │
│ │  │  Monday, January 13                                     ││ │
│ │  └─────────────────────────────────────────────────────────┘│ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │  SegmentedButton: [Agenda] [Day] [Week]                     │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │  Sync Status Banner (if pending changes)                    │ │
│ │  "3 changes pending sync"                    [Sync Now]     │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │  Route Summary Card                                         │ │
│ │  ┌───────────────────────────────────────────────────────┐  │ │
│ │  │  6 appointments · 47 miles · ~2h 15m drive            │  │ │
│ │  │  [Optimize Route]                     [Start Route →] │  │ │
│ │  └───────────────────────────────────────────────────────┘  │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │  Appointment List                                           │ │
│ │  ┌───────────────────────────────────────────────────────┐  │ │
│ │  │  8:00 AM                                 [Confirmed ✓]│  │ │
│ │  │  Johnson Ranch                                        │  │ │
│ │  │  3 horses · Full set, 2 trims                         │  │ │
│ │  │  15 min drive from home                               │  │ │
│ │  └───────────────────────────────────────────────────────┘  │ │
│ │  ┌───────────────────────────────────────────────────────┐  │ │
│ │  │  10:30 AM                                 [Scheduled] │  │ │
│ │  │  Williams Farm                                        │  │ │
│ │  │  2 horses · Trims                                     │  │ │
│ │  │  12 min drive from previous                           │  │ │
│ │  └───────────────────────────────────────────────────────┘  │ │
│ │  ...                                                        │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│                                    ┌───────────────────────────┐│
│                                    │  + New Appointment        ││
│                                    │  (Extended FAB)           ││
│                                    └───────────────────────────┘│
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │  [Schedule]  [Clients]  [Routes]  [Invoices]  [More]       │ │
│ └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Client Detail Screen

```
┌─────────────────────────────────────────────────────────────────┐
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │  LargeTopAppBar (collapsing)                                │ │
│ │  [←]                                           [Edit] [···] │ │
│ │                                                             │ │
│ │  Johnson Ranch                                              │ │
│ │  Active client · Since Jan 2024                             │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │  Contact Card                                               │ │
│ │  ┌───────────────────────────────────────────────────────┐  │ │
│ │  │  📞 (555) 123-4567                         [Call]     │  │ │
│ │  │  ✉️  sarah@johnsonranch.com                [Email]    │  │ │
│ │  │  📍 1234 Ranch Road, Austin, TX 78701      [Navigate] │  │ │
│ │  └───────────────────────────────────────────────────────┘  │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │  Horses (3)                                     [Add Horse] │ │
│ │  ┌─────────────────────────────────────────────────────────┐│ │
│ │  │ [🐴] Midnight                                           ││ │
│ │  │      Quarter Horse · Full set every 6 weeks             ││ │
│ │  │      Last: Dec 15 · Due: Jan 26 (in 13 days)            ││ │
│ │  └─────────────────────────────────────────────────────────┘│ │
│ │  ┌─────────────────────────────────────────────────────────┐│ │
│ │  │ [🐴] Dusty                                              ││ │
│ │  │      Paint · Trim every 8 weeks                         ││ │
│ │  │      Last: Dec 20 · Due: Feb 14                         ││ │
│ │  └─────────────────────────────────────────────────────────┘│ │
│ │  ...                                                        │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │  Upcoming Appointments                                      │ │
│ │  ┌─────────────────────────────────────────────────────────┐│ │
│ │  │  Jan 26, 8:00 AM                         [Scheduled]    ││ │
│ │  │  Midnight (Full set), Dusty (Trim)                      ││ │
│ │  └─────────────────────────────────────────────────────────┘│ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │  Notes                                                      │ │
│ │  Gate code: 1234#                                           │ │
│ │  Park by the red barn. Horses in pasture 2.                 │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│                                    ┌───────────────────────────┐│
│                                    │  + Schedule Appointment   ││
│                                    └───────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### Route Optimization Screen

```
┌─────────────────────────────────────────────────────────────────┐
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │  TopAppBar                                                  │ │
│ │  [←]  Today's Route                          [Share] [···]  │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │                                                             │ │
│ │                    [MAP VIEW]                               │ │
│ │                                                             │ │
│ │     🏠 ──── 1️⃣ ──── 2️⃣ ──── 3️⃣ ──── 4️⃣ ──── 🏠          │ │
│ │                                                             │ │
│ │     Route line with numbered stops                          │ │
│ │                                                             │ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │  Route Summary                                              │ │
│ │  47.3 miles · 1h 52m drive · 4h 30m total                   │ │
│ │                                                             │ │
│ │  [Optimize Route]                          [Start Navigation]│ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │  Stops (drag to reorder)                                    │ │
│ │  ┌─────────────────────────────────────────────────────────┐│ │
│ │  │ ≡  1. Johnson Ranch                        8:00 AM      ││ │
│ │  │      15 min from start                                  ││ │
│ │  └─────────────────────────────────────────────────────────┘│ │
│ │  ┌─────────────────────────────────────────────────────────┐│ │
│ │  │ ≡  2. Williams Farm                       10:15 AM      ││ │
│ │  │      12 min from previous                               ││ │
│ │  └─────────────────────────────────────────────────────────┘│ │
│ │  ┌─────────────────────────────────────────────────────────┐│ │
│ │  │ ≡  3. Martinez Stables                    11:45 AM      ││ │
│ │  │      18 min from previous                               ││ │
│ │  └─────────────────────────────────────────────────────────┘│ │
│ │  ┌─────────────────────────────────────────────────────────┐│ │
│ │  │ ≡  4. Oak Hill Equestrian                  1:30 PM      ││ │
│ │  │      25 min from previous                               ││ │
│ │  └─────────────────────────────────────────────────────────┘│ │
│ └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │  [Schedule]  [Clients]  [Routes]  [Invoices]  [More]       │ │
│ └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## 10. Testing Strategy

### Test Pyramid

```
┌─────────────────────────────────────────────────────────────────┐
│                       TEST PYRAMID                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│                          /\                                     │
│                         /  \        E2E Tests (5%)              │
│                        /    \       - Critical user journeys    │
│                       /──────\      - Maestro or manual         │
│                      /        \                                 │
│                     /          \    Integration Tests (20%)     │
│                    /            \   - Room + Repository         │
│                   /──────────────\  - API integration           │
│                  /                \ - WorkManager               │
│                 /                  \                            │
│                /                    \ Unit Tests (75%)          │
│               /                      \ - ViewModels             │
│              /                        \ - UseCases              │
│             /                          \ - Repositories         │
│            /____________________________\ - Utilities           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Critical Test Scenarios

| Scenario | Type | Description |
|----------|------|-------------|
| Offline CRUD | Integration | Create/update/delete while offline, verify sync |
| Conflict resolution | Unit | Last-write-wins behavior |
| Route optimization | Unit | Verify stop ordering logic |
| Calendar sync | Integration | Write to device calendar, verify events |
| Tier limit enforcement | Unit | Block actions at limits |
| Auth flow | E2E | Sign up → onboarding → first appointment |
| Appointment completion | E2E | Complete → invoice → mark paid |

---

## 11. Build Variants & Environments

```kotlin
android {
    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Use development Supabase project
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(...)
            // Use production Supabase project
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            buildConfigField("String", "SUPABASE_URL", "\"https://xxx.supabase.co\"")
            buildConfigField("String", "API_BASE_URL", "\"https://dev-api.hoofdirect.com\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "SUPABASE_URL", "\"https://yyy.supabase.co\"")
            buildConfigField("String", "API_BASE_URL", "\"https://api.hoofdirect.com\"")
        }
    }
}
```

---

## 12. Release Checklist

### Pre-Launch

- [ ] ProGuard rules tested (no runtime crashes)
- [ ] All API keys in secrets (not hardcoded)
- [ ] Firebase Crashlytics enabled
- [ ] Analytics events implemented
- [ ] Deep links configured
- [ ] App signing key backed up securely
- [ ] Privacy policy URL live
- [ ] Terms of service URL live

### Play Store Assets

| Asset | Specification |
|-------|---------------|
| App icon | 512×512 PNG |
| Feature graphic | 1024×500 PNG |
| Screenshots (phone) | 16:9, minimum 2, maximum 8 |
| Screenshots (tablet) | 16:9, optional but recommended |
| Short description | ≤80 characters |
| Full description | ≤4000 characters |
| Category | Business |
| Content rating | Complete questionnaire |

### Staged Rollout Plan

| Stage | Percentage | Duration | Criteria to Advance |
|-------|------------|----------|---------------------|
| Internal | Team only | 1 week | No critical bugs |
| Closed beta | 20 farriers | 2 weeks | <1% crash rate, 4+ star feedback |
| Open beta | Anyone | 1 week | No new critical issues |
| Production | 10% | 3 days | Stable crash rate |
| Production | 50% | 3 days | No increase in ANRs |
| Production | 100% | — | Full launch |

---

## 13. Performance Targets

| Metric | Target | Measurement |
|--------|--------|-------------|
| Cold start | <2 seconds | Firebase Performance |
| Screen transition | <100ms | Compose metrics |
| List scroll | 60 fps | GPU profiler |
| Offline data load | <50ms | Room query time |
| Sync completion | <30 seconds | Custom logging |
| APK size | <30 MB | Bundle analyzer |
| Memory usage | <150 MB | Android Profiler |
| Battery (1hr active) | <5% | Battery Historian |

---

## 14. Phase-by-Phase Implementation

### Phase 1: Foundation (Weeks 1-8)

| Week | Focus | Deliverables |
|------|-------|--------------|
| 1 | Project setup | Gradle config, Hilt, Room skeleton, theme |
| 2 | Auth | Sign in/up screens, Supabase Auth integration |
| 3 | Offline core | SyncManager, SyncWorker, Room DAOs |
| 4 | Client module | ClientEntity, ClientDao, ClientRepository |
| 5 | Client UI | ClientListScreen, ClientDetailScreen |
| 6 | Horse module | HorseEntity, HorseDao, HorseRepository |
| 7 | Horse UI | Horse screens, photo capture |
| 8 | Integration | End-to-end client/horse flow, offline test |

### Phase 2: Scheduling (Weeks 9-14)

| Week | Focus | Deliverables |
|------|-------|--------------|
| 9 | Appointment data | AppointmentEntity, relationships |
| 10 | Calendar UI | Agenda view, day view |
| 11 | Appointment CRUD | Create/edit/complete flows |
| 12 | Calendar sync | CalendarContract integration |
| 13 | Reminders | FCM setup, ReminderScheduler |
| 14 | Polish | Week view, status colors, animations |

### Phase 3: Routes (Weeks 15-20)

| Week | Focus | Deliverables |
|------|-------|--------------|
| 15 | Maps setup | Google Maps SDK, basic map screen |
| 16 | Client pins | Display clients on map |
| 17 | Route API | Google Routes integration |
| 18 | Optimization UI | Route screen, drag reorder |
| 19 | Navigation | Handoff to Google Maps/Waze |
| 20 | Mileage | MileageTrackingService, logging |

### Phase 4-6: Financial, Monetization, Launch (Weeks 21-38)

See main product plan for details.

---

*Document version: 1.0*
*Last updated: January 2026*
