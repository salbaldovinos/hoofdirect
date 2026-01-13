# PRD-020: Onboarding Flow

**Priority**: P1  
**Phase**: 6 - Polish & Launch  
**Estimated Duration**: 3 days

---

## Overview

### Purpose
Guide new users through initial setup and first actions to maximize activation and retention.

### Business Value
- Reduces time to value
- Increases activation rate
- Proper permission requests
- Demonstrates key features

### Success Metrics
| Metric | Target |
|--------|--------|
| Onboarding completion | > 80% |
| First client created | > 70% |
| First appointment created | > 50% |
| Time to complete | < 3 minutes |

---

## Functional Requirements

### FR-020-01: Welcome Screen
- App value proposition
- Hero image
- "Get Started" CTA

### FR-020-02: Profile Setup
- Business name (required)
- Phone (required)
- Home address (required, for routes)
- Geocoding on save

### FR-020-03: Permission Requests
- Location (for routes)
- Calendar (for sync)
- Notifications (for reminders)
- Each with clear rationale
- Allow skip with later prompt

### FR-020-04: First Client
- Guided form with tooltips
- "Add your first client"
- Skip option

### FR-020-05: First Horse
- If client added, prompt for horse
- Pre-filled defaults
- Skip option

### FR-020-06: First Appointment
- Optional guided scheduling
- "Schedule your first appointment"
- Skip option

### FR-020-07: Completion
- Celebration screen
- Quick tour tips
- "Start Using Hoof Direct"

---

## Technical Implementation

```kotlin
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val onboardingPrefs: OnboardingPreferencesManager
) : ViewModel() {
    
    private val _step = MutableStateFlow(OnboardingStep.WELCOME)
    val step: StateFlow<OnboardingStep> = _step.asStateFlow()
    
    fun nextStep() {
        _step.value = when (_step.value) {
            OnboardingStep.WELCOME -> OnboardingStep.PROFILE
            OnboardingStep.PROFILE -> OnboardingStep.PERMISSIONS
            OnboardingStep.PERMISSIONS -> OnboardingStep.FIRST_CLIENT
            OnboardingStep.FIRST_CLIENT -> OnboardingStep.COMPLETE
            OnboardingStep.COMPLETE -> OnboardingStep.COMPLETE
        }
    }
    
    fun skipToComplete() {
        _step.value = OnboardingStep.COMPLETE
    }
    
    suspend fun completeOnboarding() {
        onboardingPrefs.setOnboardingComplete(true)
    }
}

enum class OnboardingStep {
    WELCOME,
    PROFILE,
    PERMISSIONS,
    FIRST_CLIENT,
    COMPLETE
}
```

---

## UI Specifications

### Welcome Screen
```
┌─────────────────────────────────────────┐
│                                         │
│          [App Logo]                     │
│                                         │
│       Welcome to Hoof Direct            │
│                                         │
│   Save hours every week with smart      │
│   route planning and scheduling         │
│                                         │
│         [Hero Illustration]             │
│                                         │
│                                         │
│         [Get Started]                   │
│                                         │
└─────────────────────────────────────────┘
```

### Profile Setup
```
┌─────────────────────────────────────────┐
│                            [Skip]       │
│                                         │
│  Let's set up your profile              │
│                                         │
│  Business Name *                        │
│  ┌─────────────────────────────────┐   │
│  │ Smith Farrier Services          │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Phone *                                │
│  ┌─────────────────────────────────┐   │
│  │ (555) 123-4567                  │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Home Address *                         │
│  ┌─────────────────────────────────┐   │
│  │ 🔍 Search address...            │   │
│  └─────────────────────────────────┘   │
│  Used for route planning                │
│                                         │
│         [Continue]                      │
│                                         │
└─────────────────────────────────────────┘
```

### Permission Request
```
┌─────────────────────────────────────────┐
│                                         │
│         📍 Location Access              │
│                                         │
│   Hoof Direct uses your location to:    │
│                                         │
│   ✓ Optimize your daily route           │
│   ✓ Track mileage automatically         │
│   ✓ Show your position on maps          │
│                                         │
│                                         │
│         [Allow Location]                │
│                                         │
│          [Maybe Later]                  │
│                                         │
└─────────────────────────────────────────┘
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-020-01 | Welcome displays on first launch | E2E test |
| AC-020-02 | Profile saves correctly | Integration test |
| AC-020-03 | Permissions request with rationale | Manual test |
| AC-020-04 | Skip option available | UI test |
| AC-020-05 | Onboarding replayable from settings | UI test |
| AC-020-06 | Completion < 3 minutes | Manual test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-001 (Auth) | Internal | Required |
| PRD-003 (Clients) | Internal | For first client |
