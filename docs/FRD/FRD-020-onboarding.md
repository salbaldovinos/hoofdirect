# FRD-020: Onboarding Flow

**Source PRD**: PRD-020-onboarding.md  
**Priority**: P1  
**Phase**: 6 - Polish & Launch  
**Estimated Duration**: 3 days

---

## 1. Overview

### 1.1 Purpose
Guide new users through initial app setup, permission grants, and first actions to maximize activation and demonstrate core value quickly. The onboarding flow creates a guided first-run experience that establishes essential configuration before users access the main app.

### 1.2 Success Metrics
| Metric | Target | Measurement |
|--------|--------|-------------|
| Onboarding completion rate | > 80% | Users who reach completion screen |
| First client created | > 70% | Users who add client during or after onboarding |
| First appointment created | > 50% | Users who create appointment within 24 hours |
| Time to complete | < 3 minutes | Average duration from welcome to completion |
| Permission grant rate | > 60% | Users who grant at least one permission |

### 1.3 Scope
This FRD covers:
- Welcome and value proposition screen
- Profile setup (business info, address)
- Permission request flow (location, calendar, notifications)
- Guided first client creation
- Guided first horse creation
- Guided first appointment scheduling
- Completion celebration and app tour tips

### 1.4 Dependencies
| Dependency | FRD | Purpose |
|------------|-----|---------|
| Authentication | FRD-001 | User account creation |
| Client Management | FRD-003 | First client creation |
| Horse Management | FRD-004 | First horse creation |
| Appointments | FRD-005 | First appointment creation |
| Settings | FRD-019 | Profile persistence |

---

## 2. Data Models

### 2.1 Onboarding State

```kotlin
// core/domain/model/OnboardingState.kt
enum class OnboardingStep {
    WELCOME,
    PROFILE_SETUP,
    PERMISSIONS_LOCATION,
    PERMISSIONS_CALENDAR,
    PERMISSIONS_NOTIFICATIONS,
    FIRST_CLIENT,
    FIRST_HORSE,
    FIRST_APPOINTMENT,
    COMPLETION
}

data class OnboardingState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val isComplete: Boolean = false,
    val profileComplete: Boolean = false,
    val permissionsRequested: PermissionsState = PermissionsState(),
    val firstClientId: String? = null,
    val firstHorseId: String? = null,
    val firstAppointmentId: String? = null
)

data class PermissionsState(
    val locationRequested: Boolean = false,
    val locationGranted: Boolean = false,
    val calendarRequested: Boolean = false,
    val calendarGranted: Boolean = false,
    val notificationsRequested: Boolean = false,
    val notificationsGranted: Boolean = false
)
```

### 2.2 Profile Setup Data

```kotlin
// feature/onboarding/model/ProfileSetupData.kt
data class ProfileSetupData(
    val businessName: String = "",
    val phone: String = "",
    val homeAddress: String = "",
    val homeLatitude: Double? = null,
    val homeLongitude: Double? = null
) {
    val isValid: Boolean
        get() = businessName.isNotBlank() && 
                phone.isNotBlank() && 
                homeAddress.isNotBlank() &&
                homeLatitude != null &&
                homeLongitude != null
                
    val validationErrors: List<ProfileFieldError>
        get() = buildList {
            if (businessName.isBlank()) add(ProfileFieldError.BUSINESS_NAME_REQUIRED)
            if (phone.isBlank()) add(ProfileFieldError.PHONE_REQUIRED)
            else if (!phone.isValidPhone()) add(ProfileFieldError.PHONE_INVALID)
            if (homeAddress.isBlank()) add(ProfileFieldError.ADDRESS_REQUIRED)
            else if (homeLatitude == null) add(ProfileFieldError.ADDRESS_NOT_GEOCODED)
        }
}

enum class ProfileFieldError(val message: String) {
    BUSINESS_NAME_REQUIRED("Business name is required"),
    PHONE_REQUIRED("Phone number is required"),
    PHONE_INVALID("Please enter a valid phone number"),
    ADDRESS_REQUIRED("Home address is required for route planning"),
    ADDRESS_NOT_GEOCODED("Please select an address from the suggestions")
}
```

### 2.3 Onboarding Preferences

```kotlin
// core/preferences/OnboardingPreferencesManager.kt
@Singleton
class OnboardingPreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val KEY_ONBOARDING_VERSION = intPreferencesKey("onboarding_version")
        val KEY_SKIP_COUNT = intPreferencesKey("onboarding_skip_count")
        val KEY_STARTED_AT = longPreferencesKey("onboarding_started_at")
        val KEY_COMPLETED_AT = longPreferencesKey("onboarding_completed_at")
    }
    
    // Current onboarding version - increment to force re-onboarding
    private const val CURRENT_ONBOARDING_VERSION = 1
    
    val isOnboardingComplete: Flow<Boolean> = dataStore.data.map { prefs ->
        val complete = prefs[KEY_ONBOARDING_COMPLETE] ?: false
        val version = prefs[KEY_ONBOARDING_VERSION] ?: 0
        complete && version >= CURRENT_ONBOARDING_VERSION
    }
    
    suspend fun setOnboardingComplete() {
        dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETE] = true
            prefs[KEY_ONBOARDING_VERSION] = CURRENT_ONBOARDING_VERSION
            prefs[KEY_COMPLETED_AT] = System.currentTimeMillis()
        }
    }
    
    suspend fun resetOnboarding() {
        dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETE] = false
        }
    }
    
    suspend fun recordSkip() {
        dataStore.edit { prefs ->
            val current = prefs[KEY_SKIP_COUNT] ?: 0
            prefs[KEY_SKIP_COUNT] = current + 1
        }
    }
}
```

---

## 3. Onboarding Flow State Machine

### 3.1 Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        ONBOARDING FLOW                          │
└─────────────────────────────────────────────────────────────────┘

  ┌──────────┐     ┌─────────────┐     ┌─────────────────────────┐
  │ WELCOME  │────>│   PROFILE   │────>│      PERMISSIONS        │
  │          │     │   SETUP     │     │ (Location → Calendar →  │
  └──────────┘     └─────────────┘     │  Notifications)         │
                         │             └───────────┬─────────────┘
                         │ [Skip]                  │
                         v                         v
                   ┌─────────────────────────────────────────────┐
                   │              FIRST CLIENT                    │
                   │      (Optional - can skip)                   │
                   └───────────────────┬─────────────────────────┘
                                       │
                         ┌─────────────┴─────────────┐
                         │                           │
                   [Client Added]              [Skipped]
                         │                           │
                         v                           │
                   ┌─────────────┐                   │
                   │ FIRST HORSE │                   │
                   │ (Optional)  │                   │
                   └──────┬──────┘                   │
                         │                           │
                         v                           │
                   ┌─────────────────┐               │
                   │ FIRST APPOINTMENT│              │
                   │    (Optional)   │               │
                   └────────┬────────┘               │
                            │                        │
                            v                        │
                   ┌─────────────────────────────────┘
                   │
                   v
            ┌──────────────┐
            │  COMPLETION  │────> [Main App]
            └──────────────┘
```

### 3.2 Step Transitions

| Current Step | Action | Next Step | Notes |
|--------------|--------|-----------|-------|
| WELCOME | Tap "Get Started" | PROFILE_SETUP | Always |
| PROFILE_SETUP | Save profile | PERMISSIONS_LOCATION | Profile data saved |
| PROFILE_SETUP | Tap "Skip" | PERMISSIONS_LOCATION | Marked incomplete |
| PERMISSIONS_LOCATION | Grant/Deny | PERMISSIONS_CALENDAR | Permission recorded |
| PERMISSIONS_LOCATION | Tap "Maybe Later" | PERMISSIONS_CALENDAR | Skipped |
| PERMISSIONS_CALENDAR | Grant/Deny | PERMISSIONS_NOTIFICATIONS | Permission recorded |
| PERMISSIONS_CALENDAR | Tap "Maybe Later" | PERMISSIONS_NOTIFICATIONS | Skipped |
| PERMISSIONS_NOTIFICATIONS | Grant/Deny | FIRST_CLIENT | Permission recorded |
| PERMISSIONS_NOTIFICATIONS | Tap "Maybe Later" | FIRST_CLIENT | Skipped |
| FIRST_CLIENT | Add client | FIRST_HORSE | clientId stored |
| FIRST_CLIENT | Tap "Skip for now" | COMPLETION | No client created |
| FIRST_HORSE | Add horse | FIRST_APPOINTMENT | horseId stored |
| FIRST_HORSE | Tap "Skip" | COMPLETION | No horse created |
| FIRST_APPOINTMENT | Create appointment | COMPLETION | appointmentId stored |
| FIRST_APPOINTMENT | Tap "Skip" | COMPLETION | No appointment created |
| COMPLETION | Tap "Start Using Hoof Direct" | Main App | Onboarding marked complete |

---

## 4. Screen Specifications

### 4.1 Welcome Screen

**Route**: `/onboarding/welcome`

**Purpose**: Create excitement and communicate core value proposition.

```
┌─────────────────────────────────────────────┐
│                                             │
│                                             │
│              ┌───────────┐                  │
│              │           │                  │
│              │  🐴 Logo  │                  │
│              │           │                  │
│              └───────────┘                  │
│                                             │
│                                             │
│         Welcome to Hoof Direct              │
│                                             │
│     Save hours every week with smart        │
│      route planning and scheduling          │
│                                             │
│                                             │
│         ┌─────────────────────┐             │
│         │                     │             │
│         │   Hero Illustration │             │
│         │   (Farrier + Route) │             │
│         │                     │             │
│         └─────────────────────┘             │
│                                             │
│                                             │
│                                             │
│    ┌─────────────────────────────────┐      │
│    │         Get Started             │      │
│    └─────────────────────────────────┘      │
│                                             │
│                                             │
└─────────────────────────────────────────────┘
```

**Elements**:
- App logo (branded, centered)
- Headline: "Welcome to Hoof Direct"
- Subheadline: "Save hours every week with smart route planning and scheduling"
- Hero illustration showing farrier work and route concept
- Primary CTA button: "Get Started"
- No back navigation available

**Behavior**:
- Entry animation: Fade in logo (300ms), then text (300ms), then illustration (300ms)
- Button bounces subtly to draw attention
- Tapping "Get Started" transitions to Profile Setup with slide animation

### 4.2 Profile Setup Screen

**Route**: `/onboarding/profile`

**Purpose**: Collect essential business information needed for app functionality.

```
┌─────────────────────────────────────────────┐
│                                      Skip   │
│                                             │
│  ●○○○○○  Step 1 of 6                        │
│                                             │
│  Let's set up your profile                  │
│                                             │
│  This information helps us optimize your    │
│  routes and communicate with clients.       │
│                                             │
│                                             │
│  Business Name *                            │
│  ┌─────────────────────────────────────┐    │
│  │ e.g., Smith Farrier Services        │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Phone Number *                             │
│  ┌─────────────────────────────────────┐    │
│  │ (555) 123-4567                      │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Home Address *                             │
│  ┌─────────────────────────────────────┐    │
│  │ 🔍 Search for your address...       │    │
│  └─────────────────────────────────────┘    │
│  Your starting point for daily routes       │
│                                             │
│                                             │
│    ┌─────────────────────────────────┐      │
│    │          Continue               │      │
│    └─────────────────────────────────┘      │
│                                             │
└─────────────────────────────────────────────┘
```

**Fields**:

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| Business Name | TextField | Yes | 1-100 characters |
| Phone Number | PhoneField | Yes | Valid US phone format |
| Home Address | PlacesAutocomplete | Yes | Must select from suggestions |

**Address Autocomplete**:
- Uses Google Places Autocomplete API
- Filters to addresses only (not businesses)
- US-biased results
- On selection: saves formatted address + lat/lng
- Shows checkmark when valid address selected

**Phone Formatting**:
- Auto-formats as user types: (XXX) XXX-XXXX
- Accepts 10-digit input
- Strips non-numeric on validation

**Behavior**:
- "Continue" button disabled until all fields valid
- Real-time validation with inline error messages
- "Skip" link at top right
- Skipping shows warning: "You can complete this later in Settings"
- Progress indicator shows "Step 1 of 6"

### 4.3 Permission Request Screens

**Purpose**: Request necessary permissions with clear rationale to maximize grant rate.

#### 4.3.1 Location Permission

**Route**: `/onboarding/permissions/location`

```
┌─────────────────────────────────────────────┐
│                                             │
│  ●●○○○○  Step 2 of 6                        │
│                                             │
│                                             │
│                ┌───────┐                    │
│                │  📍   │                    │
│                └───────┘                    │
│                                             │
│          Enable Location Access             │
│                                             │
│   Hoof Direct uses your location to:        │
│                                             │
│   ✓  Optimize your daily driving route      │
│   ✓  Track mileage automatically            │
│   ✓  Show your position on the map          │
│                                             │
│                                             │
│    ┌─────────────────────────────────┐      │
│    │       Allow Location Access     │      │
│    └─────────────────────────────────┘      │
│                                             │
│             Maybe Later                     │
│                                             │
└─────────────────────────────────────────────┘
```

**System Permission**: `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`

**For Background Location** (Android 10+):
- First request foreground permission
- If granted, show secondary screen explaining background need
- Request `ACCESS_BACKGROUND_LOCATION` separately

**Behavior**:
- Tapping "Allow Location Access" triggers system permission dialog
- On grant → Proceed to next permission
- On deny → Show explanation and "Maybe Later" becomes primary
- "Maybe Later" skips without requesting, proceeds to next permission
- Permission state recorded for later prompting

#### 4.3.2 Calendar Permission

**Route**: `/onboarding/permissions/calendar`

```
┌─────────────────────────────────────────────┐
│                                             │
│  ●●●○○○  Step 3 of 6                        │
│                                             │
│                                             │
│                ┌───────┐                    │
│                │  📅   │                    │
│                └───────┘                    │
│                                             │
│          Sync Your Calendar                 │
│                                             │
│   Keep your schedule in sync:               │
│                                             │
│   ✓  Export appointments to your calendar   │
│   ✓  See personal events while scheduling   │
│   ✓  Never double-book again                │
│                                             │
│                                             │
│    ┌─────────────────────────────────┐      │
│    │       Allow Calendar Access     │      │
│    └─────────────────────────────────┘      │
│                                             │
│             Maybe Later                     │
│                                             │
└─────────────────────────────────────────────┘
```

**System Permission**: `READ_CALENDAR` and `WRITE_CALENDAR`

#### 4.3.3 Notification Permission

**Route**: `/onboarding/permissions/notifications`

```
┌─────────────────────────────────────────────┐
│                                             │
│  ●●●●○○  Step 4 of 6                        │
│                                             │
│                                             │
│                ┌───────┐                    │
│                │  🔔   │                    │
│                └───────┘                    │
│                                             │
│         Enable Notifications                │
│                                             │
│   Stay on top of your schedule:             │
│                                             │
│   ✓  Appointment reminders                  │
│   ✓  Daily schedule digest                  │
│   ✓  Overdue horse alerts                   │
│                                             │
│                                             │
│    ┌─────────────────────────────────┐      │
│    │       Enable Notifications      │      │
│    └─────────────────────────────────┘      │
│                                             │
│             Maybe Later                     │
│                                             │
└─────────────────────────────────────────────┘
```

**System Permission**: `POST_NOTIFICATIONS` (Android 13+)

**Behavior** (Android 12 and below):
- Skip this screen entirely (notifications enabled by default)

### 4.4 First Client Screen

**Route**: `/onboarding/first-client`

```
┌─────────────────────────────────────────────┐
│                                             │
│  ●●●●●○  Step 5 of 6                        │
│                                             │
│                                             │
│          Add Your First Client              │
│                                             │
│   Start building your client list.          │
│   Don't worry - you can add more later!     │
│                                             │
│                                             │
│  Client Name *                              │
│  ┌─────────────────────────────────────┐    │
│  │ e.g., John Smith                    │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Phone                                      │
│  ┌─────────────────────────────────────┐    │
│  │ (555) 987-6543                      │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Address                                    │
│  ┌─────────────────────────────────────┐    │
│  │ 🔍 Search for address...            │    │
│  └─────────────────────────────────────┘    │
│                                             │
│                                             │
│    ┌─────────────────────────────────┐      │
│    │          Add Client             │      │
│    └─────────────────────────────────┘      │
│                                             │
│            Skip for now                     │
│                                             │
└─────────────────────────────────────────────┘
```

**Simplified Form**:
Only essential fields are shown during onboarding:
- Client Name (required)
- Phone (optional)
- Address (optional, but encouraged with tooltip)

**Behavior**:
- Tooltip on address field: "Add address for route optimization"
- On "Add Client": Creates client, shows success animation, proceeds to First Horse
- On "Skip for now": Shows "You can add clients anytime from the Clients tab"
- Skipping goes directly to Completion (skips horse and appointment)

### 4.5 First Horse Screen

**Route**: `/onboarding/first-horse`

**Shown only if**: First client was created

```
┌─────────────────────────────────────────────┐
│                                             │
│  ●●●●●○  Step 5 of 6 (continued)            │
│                                             │
│                                             │
│          Add a Horse for {Client}           │
│                                             │
│   Track service history for each horse.     │
│                                             │
│                                             │
│  Horse Name *                               │
│  ┌─────────────────────────────────────┐    │
│  │ e.g., Thunder                       │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Shoeing Cycle                              │
│  ┌─────────────────────────────────────┐    │
│  │ 6 weeks                           ▼ │    │
│  └─────────────────────────────────────┘    │
│  How often this horse needs shoes           │
│                                             │
│                                             │
│    ┌─────────────────────────────────┐      │
│    │          Add Horse              │      │
│    └─────────────────────────────────┘      │
│                                             │
│              Skip                           │
│                                             │
└─────────────────────────────────────────────┘
```

**Fields**:
- Horse Name (required)
- Shoeing Cycle (dropdown, default from user preferences)

**Behavior**:
- Client name shown in header for context
- On "Add Horse": Creates horse linked to first client, proceeds to First Appointment
- On "Skip": Proceeds to First Appointment

### 4.6 First Appointment Screen

**Route**: `/onboarding/first-appointment`

**Shown only if**: First client was created

```
┌─────────────────────────────────────────────┐
│                                             │
│  ●●●●●●  Step 6 of 6                        │
│                                             │
│                                             │
│        Schedule Your First Visit            │
│                                             │
│   Get started with your schedule.           │
│                                             │
│  Client                                     │
│  ┌─────────────────────────────────────┐    │
│  │ {First Client Name}           🔒   │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Date                                       │
│  ┌─────────────────────────────────────┐    │
│  │ Tomorrow, Jan 14                  📅│    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Time                                       │
│  ┌─────────────────────────────────────┐    │
│  │ 9:00 AM                           ⏰│    │
│  └─────────────────────────────────────┘    │
│                                             │
│                                             │
│    ┌─────────────────────────────────┐      │
│    │      Schedule Appointment       │      │
│    └─────────────────────────────────┘      │
│                                             │
│              Skip                           │
│                                             │
└─────────────────────────────────────────────┘
```

**Smart Defaults**:
- Client: Locked to first client created
- Date: Tomorrow (or next working day based on user preferences)
- Time: 9:00 AM (or work start time from preferences)
- Duration: User's default duration (45 min if not set)

**Behavior**:
- Client field is read-only (shows lock icon)
- Date picker opens calendar starting from tomorrow
- Time picker in 15-minute increments
- If horse was created, show horse selector with first horse pre-selected
- On "Schedule Appointment": Creates appointment, shows success, proceeds to Completion
- On "Skip": Proceeds to Completion

### 4.7 Completion Screen

**Route**: `/onboarding/complete`

```
┌─────────────────────────────────────────────┐
│                                             │
│                                             │
│                                             │
│                 🎉                          │
│                                             │
│           You're All Set!                   │
│                                             │
│                                             │
│   ┌─────────────────────────────────────┐   │
│   │  Quick Tips                         │   │
│   │                                     │   │
│   │  📍 Tap any date to add             │   │
│   │     appointments                    │   │
│   │                                     │   │
│   │  🗺️ Use the Map tab to optimize     │   │
│   │     your daily route                │   │
│   │                                     │   │
│   │  👤 Add clients from the            │   │
│   │     Clients tab                     │   │
│   │                                     │   │
│   └─────────────────────────────────────┘   │
│                                             │
│                                             │
│    ┌─────────────────────────────────────┐  │
│    │      Start Using Hoof Direct       │  │
│    └─────────────────────────────────────┘  │
│                                             │
│                                             │
└─────────────────────────────────────────────┘
```

**Celebration Elements**:
- Confetti animation on screen entry (2 seconds)
- Emoji celebration icon (🎉)
- "You're All Set!" headline
- Quick tips card with 3 key feature pointers
- Primary CTA: "Start Using Hoof Direct"

**Behavior**:
- Confetti particle animation plays once
- Tapping CTA marks onboarding complete and navigates to main calendar view
- No back navigation available from this screen

---

## 5. ViewModel Implementation

```kotlin
// feature/onboarding/viewmodel/OnboardingViewModel.kt
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val clientRepository: ClientRepository,
    private val horseRepository: HorseRepository,
    private val appointmentRepository: AppointmentRepository,
    private val onboardingPrefs: OnboardingPreferencesManager,
    private val permissionManager: PermissionManager,
    private val analyticsTracker: AnalyticsTracker
) : ViewModel() {
    
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()
    
    private val _profileData = MutableStateFlow(ProfileSetupData())
    val profileData: StateFlow<ProfileSetupData> = _profileData.asStateFlow()
    
    private val _events = MutableSharedFlow<OnboardingEvent>()
    val events: SharedFlow<OnboardingEvent> = _events.asSharedFlow()
    
    init {
        analyticsTracker.logEvent("onboarding_started")
    }
    
    // Profile Setup
    fun updateBusinessName(name: String) {
        _profileData.update { it.copy(businessName = name) }
    }
    
    fun updatePhone(phone: String) {
        _profileData.update { it.copy(phone = phone) }
    }
    
    fun updateAddress(address: String, lat: Double?, lng: Double?) {
        _profileData.update { 
            it.copy(homeAddress = address, homeLatitude = lat, homeLongitude = lng)
        }
    }
    
    fun saveProfile() {
        viewModelScope.launch {
            val data = _profileData.value
            if (!data.isValid) {
                _events.emit(OnboardingEvent.ValidationError(data.validationErrors))
                return@launch
            }
            
            profileRepository.updateProfile(
                businessName = data.businessName,
                phone = data.phone,
                homeAddress = data.homeAddress,
                homeLatitude = data.homeLatitude!!,
                homeLongitude = data.homeLongitude!!
            )
            
            _state.update { it.copy(profileComplete = true) }
            goToNextStep()
        }
    }
    
    fun skipProfile() {
        analyticsTracker.logEvent("onboarding_profile_skipped")
        goToNextStep()
    }
    
    // Permissions
    fun onPermissionResult(permission: AppPermission, granted: Boolean) {
        val updatedPermissions = when (permission) {
            AppPermission.LOCATION -> _state.value.permissionsRequested.copy(
                locationRequested = true,
                locationGranted = granted
            )
            AppPermission.CALENDAR -> _state.value.permissionsRequested.copy(
                calendarRequested = true,
                calendarGranted = granted
            )
            AppPermission.NOTIFICATIONS -> _state.value.permissionsRequested.copy(
                notificationsRequested = true,
                notificationsGranted = granted
            )
        }
        
        _state.update { it.copy(permissionsRequested = updatedPermissions) }
        analyticsTracker.logEvent("permission_${permission.name.lowercase()}_${if (granted) "granted" else "denied"}")
        goToNextStep()
    }
    
    fun skipPermission(permission: AppPermission) {
        analyticsTracker.logEvent("permission_${permission.name.lowercase()}_skipped")
        goToNextStep()
    }
    
    // First Client
    fun createFirstClient(name: String, phone: String?, address: String?, lat: Double?, lng: Double?) {
        viewModelScope.launch {
            val client = clientRepository.createClient(
                name = name,
                phone = phone,
                address = address,
                latitude = lat,
                longitude = lng
            )
            
            _state.update { it.copy(firstClientId = client.id) }
            analyticsTracker.logEvent("onboarding_first_client_created")
            goToNextStep()
        }
    }
    
    fun skipFirstClient() {
        analyticsTracker.logEvent("onboarding_first_client_skipped")
        // Skip directly to completion
        _state.update { it.copy(currentStep = OnboardingStep.COMPLETION) }
    }
    
    // First Horse
    fun createFirstHorse(name: String, cycleWeeks: Int) {
        viewModelScope.launch {
            val clientId = _state.value.firstClientId ?: return@launch
            
            val horse = horseRepository.createHorse(
                clientId = clientId,
                name = name,
                shoeingCycleWeeks = cycleWeeks
            )
            
            _state.update { it.copy(firstHorseId = horse.id) }
            analyticsTracker.logEvent("onboarding_first_horse_created")
            goToNextStep()
        }
    }
    
    fun skipFirstHorse() {
        analyticsTracker.logEvent("onboarding_first_horse_skipped")
        goToNextStep()
    }
    
    // First Appointment
    fun createFirstAppointment(date: LocalDate, time: LocalTime) {
        viewModelScope.launch {
            val clientId = _state.value.firstClientId ?: return@launch
            
            val appointment = appointmentRepository.createAppointment(
                clientId = clientId,
                horseIds = _state.value.firstHorseId?.let { listOf(it) } ?: emptyList(),
                scheduledAt = LocalDateTime.of(date, time)
            )
            
            _state.update { it.copy(firstAppointmentId = appointment.id) }
            analyticsTracker.logEvent("onboarding_first_appointment_created")
            goToNextStep()
        }
    }
    
    fun skipFirstAppointment() {
        analyticsTracker.logEvent("onboarding_first_appointment_skipped")
        goToNextStep()
    }
    
    // Completion
    fun completeOnboarding() {
        viewModelScope.launch {
            onboardingPrefs.setOnboardingComplete()
            
            analyticsTracker.logEvent("onboarding_completed", mapOf(
                "profile_completed" to _state.value.profileComplete,
                "location_granted" to _state.value.permissionsRequested.locationGranted,
                "calendar_granted" to _state.value.permissionsRequested.calendarGranted,
                "notifications_granted" to _state.value.permissionsRequested.notificationsGranted,
                "client_created" to (_state.value.firstClientId != null),
                "horse_created" to (_state.value.firstHorseId != null),
                "appointment_created" to (_state.value.firstAppointmentId != null)
            ))
            
            _events.emit(OnboardingEvent.NavigateToMain)
        }
    }
    
    private fun goToNextStep() {
        val currentStep = _state.value.currentStep
        val nextStep = when (currentStep) {
            OnboardingStep.WELCOME -> OnboardingStep.PROFILE_SETUP
            OnboardingStep.PROFILE_SETUP -> OnboardingStep.PERMISSIONS_LOCATION
            OnboardingStep.PERMISSIONS_LOCATION -> OnboardingStep.PERMISSIONS_CALENDAR
            OnboardingStep.PERMISSIONS_CALENDAR -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OnboardingStep.PERMISSIONS_NOTIFICATIONS
                } else {
                    OnboardingStep.FIRST_CLIENT
                }
            }
            OnboardingStep.PERMISSIONS_NOTIFICATIONS -> OnboardingStep.FIRST_CLIENT
            OnboardingStep.FIRST_CLIENT -> {
                if (_state.value.firstClientId != null) {
                    OnboardingStep.FIRST_HORSE
                } else {
                    OnboardingStep.COMPLETION
                }
            }
            OnboardingStep.FIRST_HORSE -> OnboardingStep.FIRST_APPOINTMENT
            OnboardingStep.FIRST_APPOINTMENT -> OnboardingStep.COMPLETION
            OnboardingStep.COMPLETION -> OnboardingStep.COMPLETION
        }
        
        _state.update { it.copy(currentStep = nextStep) }
    }
}

sealed class OnboardingEvent {
    data class ValidationError(val errors: List<ProfileFieldError>) : OnboardingEvent()
    object NavigateToMain : OnboardingEvent()
}

enum class AppPermission {
    LOCATION,
    CALENDAR,
    NOTIFICATIONS
}
```

---

## 6. Navigation

### 6.1 Entry Point

```kotlin
// core/navigation/AppNavigation.kt
@Composable
fun AppNavigation(
    isOnboardingComplete: Boolean,
    isAuthenticated: Boolean
) {
    val startDestination = when {
        !isAuthenticated -> "auth"
        !isOnboardingComplete -> "onboarding"
        else -> "main"
    }
    
    NavHost(
        startDestination = startDestination
    ) {
        // Auth graph
        navigation(startDestination = "signin", route = "auth") {
            composable("signin") { SignInScreen() }
            composable("signup") { SignUpScreen() }
        }
        
        // Onboarding graph
        navigation(startDestination = "welcome", route = "onboarding") {
            composable("welcome") { WelcomeScreen() }
            composable("profile") { ProfileSetupScreen() }
            composable("permissions/location") { LocationPermissionScreen() }
            composable("permissions/calendar") { CalendarPermissionScreen() }
            composable("permissions/notifications") { NotificationPermissionScreen() }
            composable("first-client") { FirstClientScreen() }
            composable("first-horse") { FirstHorseScreen() }
            composable("first-appointment") { FirstAppointmentScreen() }
            composable("complete") { CompletionScreen() }
        }
        
        // Main app graph
        navigation(startDestination = "calendar", route = "main") {
            // Main app screens...
        }
    }
}
```

### 6.2 Replay from Settings

```kotlin
// feature/settings/ui/SettingsScreen.kt
// In About section:
SettingsItem(
    title = "Replay Onboarding",
    onClick = {
        viewModel.resetOnboarding()
        navController.navigate("onboarding") {
            popUpTo("main") { inclusive = true }
        }
    }
)
```

---

## 7. Analytics Events

| Event Name | Parameters | Trigger |
|------------|------------|---------|
| `onboarding_started` | - | Welcome screen displayed |
| `onboarding_profile_completed` | - | Profile saved successfully |
| `onboarding_profile_skipped` | - | User tapped Skip on profile |
| `permission_location_granted` | - | Location permission granted |
| `permission_location_denied` | - | Location permission denied |
| `permission_location_skipped` | - | User tapped Maybe Later |
| `permission_calendar_granted` | - | Calendar permission granted |
| `permission_calendar_denied` | - | Calendar permission denied |
| `permission_calendar_skipped` | - | User tapped Maybe Later |
| `permission_notifications_granted` | - | Notification permission granted |
| `permission_notifications_denied` | - | Notification permission denied |
| `permission_notifications_skipped` | - | User tapped Maybe Later |
| `onboarding_first_client_created` | - | First client added |
| `onboarding_first_client_skipped` | - | User skipped first client |
| `onboarding_first_horse_created` | - | First horse added |
| `onboarding_first_horse_skipped` | - | User skipped first horse |
| `onboarding_first_appointment_created` | - | First appointment created |
| `onboarding_first_appointment_skipped` | - | User skipped appointment |
| `onboarding_completed` | profile_completed, location_granted, calendar_granted, notifications_granted, client_created, horse_created, appointment_created | Completion CTA tapped |

---

## 8. Acceptance Criteria

### Welcome Screen
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-020-01 | Welcome screen displays on first launch after sign-up | E2E test |
| AC-020-02 | Welcome screen does NOT display if onboarding already complete | E2E test |
| AC-020-03 | "Get Started" navigates to Profile Setup | UI test |
| AC-020-04 | Logo and illustration load within 500ms | Performance test |
| AC-020-05 | Entry animation completes in ~900ms total | UI test |

### Profile Setup
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-020-06 | Business name field accepts 1-100 characters | Unit test |
| AC-020-07 | Phone formats as (XXX) XXX-XXXX while typing | UI test |
| AC-020-08 | Invalid phone shows "Please enter a valid phone number" | UI test |
| AC-020-09 | Address field shows Google Places autocomplete suggestions | Integration test |
| AC-020-10 | Selecting address saves lat/lng coordinates | Integration test |
| AC-020-11 | "Continue" disabled until all required fields valid | UI test |
| AC-020-12 | "Skip" shows warning and proceeds to permissions | UI test |
| AC-020-13 | Profile data persists to database on Continue | Integration test |

### Permissions
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-020-14 | Location screen shows 3 bullet points explaining usage | UI test |
| AC-020-15 | "Allow Location Access" triggers system permission dialog | Integration test |
| AC-020-16 | Permission grant proceeds to next permission screen | UI test |
| AC-020-17 | Permission deny shows "Maybe Later" as secondary option | UI test |
| AC-020-18 | "Maybe Later" skips without system dialog and proceeds | UI test |
| AC-020-19 | Calendar permission screen shows 3 value propositions | UI test |
| AC-020-20 | Notification permission skipped on Android 12 and below | Integration test |
| AC-020-21 | Permission states recorded for later prompting | Integration test |

### First Client
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-020-22 | First client form shows only Name, Phone, Address fields | UI test |
| AC-020-23 | Only Name is required (Phone and Address optional) | UI test |
| AC-020-24 | Address field shows tooltip about route optimization | UI test |
| AC-020-25 | "Add Client" creates client in database | Integration test |
| AC-020-26 | Success animation plays after client creation | UI test |
| AC-020-27 | "Skip for now" shows helpful message | UI test |
| AC-020-28 | Skipping client goes directly to Completion | UI test |

### First Horse
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-020-29 | Horse screen only shown if client was created | UI test |
| AC-020-30 | Horse name field required | UI test |
| AC-020-31 | Shoeing cycle dropdown defaults to user's default (6 weeks) | UI test |
| AC-020-32 | Created horse links to first client | Integration test |
| AC-020-33 | "Skip" proceeds to First Appointment | UI test |

### First Appointment
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-020-34 | Appointment screen only shown if client was created | UI test |
| AC-020-35 | Client field is read-only showing first client | UI test |
| AC-020-36 | Date defaults to tomorrow (or next working day) | Unit test |
| AC-020-37 | Time defaults to 9:00 AM | UI test |
| AC-020-38 | If horse was created, it's pre-selected | UI test |
| AC-020-39 | Created appointment appears on calendar | Integration test |
| AC-020-40 | "Skip" proceeds to Completion | UI test |

### Completion
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-020-41 | Confetti animation plays on screen entry | UI test |
| AC-020-42 | Quick tips card shows 3 feature pointers | UI test |
| AC-020-43 | "Start Using Hoof Direct" marks onboarding complete | Integration test |
| AC-020-44 | CTA navigates to main calendar view | E2E test |
| AC-020-45 | Back navigation not available from completion | UI test |

### Overall Flow
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-020-46 | Full onboarding completes in under 3 minutes | Manual test |
| AC-020-47 | Progress indicator shows correct step count | UI test |
| AC-020-48 | "Replay Onboarding" in Settings restarts flow | Integration test |
| AC-020-49 | All analytics events fire correctly | Integration test |
| AC-020-50 | Onboarding state persists across app restarts | Integration test |

---

## 9. Performance Requirements

| Metric | Target | Notes |
|--------|--------|-------|
| Welcome screen load | < 500ms | Including animation setup |
| Address autocomplete response | < 300ms | Google Places API |
| Client creation | < 500ms | Local + sync |
| Step transition animation | 300ms | Slide animation |
| Total onboarding completion | < 3 minutes | Average user |

---

## 10. Error Handling

| Scenario | User Message | Action |
|----------|--------------|--------|
| Address geocoding fails | "Couldn't find that address. Try being more specific." | Clear field, refocus |
| Profile save fails (offline) | "Saved locally. Will sync when connected." | Proceed anyway |
| Client creation fails | "Couldn't create client. Please try again." | Retry button |
| Permission permanently denied | "Permission required. Enable in Settings." | Open app settings |

---

## 11. File References

```
feature/onboarding/
├── ui/
│   ├── WelcomeScreen.kt
│   ├── ProfileSetupScreen.kt
│   ├── PermissionScreen.kt
│   ├── LocationPermissionScreen.kt
│   ├── CalendarPermissionScreen.kt
│   ├── NotificationPermissionScreen.kt
│   ├── FirstClientScreen.kt
│   ├── FirstHorseScreen.kt
│   ├── FirstAppointmentScreen.kt
│   ├── CompletionScreen.kt
│   └── components/
│       ├── OnboardingProgressIndicator.kt
│       ├── PermissionBulletList.kt
│       ├── ConfettiAnimation.kt
│       └── QuickTipsCard.kt
├── viewmodel/
│   └── OnboardingViewModel.kt
├── model/
│   ├── OnboardingState.kt
│   └── ProfileSetupData.kt
└── navigation/
    └── OnboardingNavigation.kt

core/preferences/
└── OnboardingPreferencesManager.kt
```

---

## 12. Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-01-13 | Initial FRD creation |
