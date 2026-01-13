# PRD-019: Settings & Preferences

**Priority**: P1  
**Phase**: 6 - Polish & Launch  
**Estimated Duration**: 3 days

---

## Overview

### Purpose
Centralized location for app configuration, user preferences, and account management.

### Business Value
- User control over experience
- Data portability (export)
- Account management
- Support access

---

## Functional Requirements

### FR-019-01: Profile Settings
- Edit business name, phone, email
- Update home address
- Change service radius
- Profile photo

### FR-019-02: Scheduling Defaults
- Default appointment duration
- Default shoeing cycle
- Working hours
- Default reminder timing

### FR-019-03: Notification Settings
- Push notifications on/off
- SMS reminders on/off
- Email reminders on/off
- Daily digest time

### FR-019-04: Calendar Sync Settings
- Enable/disable export
- Enable/disable import
- Select import calendars

### FR-019-05: Data Management
- Export all data (JSON)
- Clear cached data
- Account deletion request

### FR-019-06: App Info
- Version number
- Privacy policy link
- Terms of service link
- Contact support (email)
- Send feedback (Canny link)

---

## UI Specifications

```
┌─────────────────────────────────────────┐
│ [←] Settings                            │
├─────────────────────────────────────────┤
│                                         │
│  Account                                │
│  ─────────────────────────────────────  │
│  Profile                            >   │
│  Subscription                       >   │
│  Payment Methods                    >   │
│                                         │
│  Scheduling                             │
│  ─────────────────────────────────────  │
│  Default Duration          45 min   >   │
│  Default Shoeing Cycle     6 weeks  >   │
│  Working Hours                      >   │
│                                         │
│  Notifications                          │
│  ─────────────────────────────────────  │
│  Push Notifications            [ON]    │
│  Daily Digest               6:00 AM >   │
│                                         │
│  Calendar                               │
│  ─────────────────────────────────────  │
│  Calendar Sync                      >   │
│                                         │
│  Data                                   │
│  ─────────────────────────────────────  │
│  Export My Data                     >   │
│  Delete Account                     >   │
│                                         │
│  About                                  │
│  ─────────────────────────────────────  │
│  Version 1.0.0 (Build 42)              │
│  Privacy Policy                     >   │
│  Terms of Service                   >   │
│  Send Feedback                      >   │
│  Contact Support                    >   │
│                                         │
└─────────────────────────────────────────┘
```

---

## Technical Implementation

```kotlin
@Singleton
class UserPreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val defaultDuration = dataStore.data.map { it[KEY_DEFAULT_DURATION] ?: 45 }
    val defaultCycle = dataStore.data.map { it[KEY_DEFAULT_CYCLE] ?: 6 }
    val pushEnabled = dataStore.data.map { it[KEY_PUSH_ENABLED] ?: true }
    val digestTime = dataStore.data.map { it[KEY_DIGEST_TIME] ?: "06:00" }
    
    suspend fun setDefaultDuration(minutes: Int) {
        dataStore.edit { it[KEY_DEFAULT_DURATION] = minutes }
    }
    
    companion object {
        val KEY_DEFAULT_DURATION = intPreferencesKey("default_duration")
        val KEY_DEFAULT_CYCLE = intPreferencesKey("default_cycle")
        val KEY_PUSH_ENABLED = booleanPreferencesKey("push_enabled")
        val KEY_DIGEST_TIME = stringPreferencesKey("digest_time")
    }
}
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-019-01 | Settings persist across restarts | Integration test |
| AC-019-02 | Export generates complete data | Integration test |
| AC-019-03 | Delete account shows 30-day warning | UI test |
| AC-019-04 | Support email opens client | Manual test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| DataStore | Library | Available |
