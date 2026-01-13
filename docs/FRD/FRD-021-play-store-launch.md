# FRD-021: Play Store Launch Preparation

**Source PRD**: PRD-021-play-store-launch.md  
**Priority**: P0  
**Phase**: 6 - Polish & Launch  
**Estimated Duration**: 2 weeks

---

## 1. Overview

### 1.1 Purpose
Prepare all required assets, complete comprehensive testing, and execute a staged rollout to the Google Play Store. This FRD documents all technical requirements, asset specifications, testing procedures, and rollout strategies for a successful public launch.

### 1.2 Success Metrics
| Metric | Target | Measurement Source |
|--------|--------|-------------------|
| Play Store rating | > 4.5 stars | Play Console |
| Crash-free rate | > 99% | Firebase Crashlytics |
| ANR rate | < 0.5% | Play Console Vitals |
| Week 1 downloads | 500+ | Play Console |
| 30-day retention | > 25% | Firebase Analytics |

### 1.3 Scope
This FRD covers:
- Play Store listing assets and content
- Build configuration and signing
- Pre-launch testing requirements
- Beta testing phases
- Staged rollout execution
- Post-launch monitoring
- Contingency procedures

### 1.4 Dependencies
| Dependency | Type | Required For |
|------------|------|--------------|
| All FRDs 001-020 | Internal | Feature completeness |
| Google Play Developer Account | External | Publishing |
| App signing key | Security | Release builds |
| Firebase project | External | Crashlytics, Analytics |
| hovedirect.com live | External | Privacy policy, Terms |

---

## 2. Play Store Listing Assets

### 2.1 App Icon

**Specification**:
| Property | Requirement |
|----------|-------------|
| Size | 512 × 512 pixels |
| Format | PNG |
| Bit depth | 32-bit with alpha |
| Shape | Full square (Play Store applies rounding) |
| Max file size | 1 MB |

**Design Requirements**:
- Horseshoe icon with route/path element
- Brand colors (primary brand color)
- Simple, recognizable at small sizes
- No text or wordmark
- No transparency around edges (adaptive icon compatible)

**File**: `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` (for app) + `store_assets/icon_512.png` (for Play Store)

### 2.2 Feature Graphic

**Specification**:
| Property | Requirement |
|----------|-------------|
| Size | 1024 × 500 pixels |
| Format | PNG or JPG |
| Max file size | 1 MB |

**Design Requirements**:
- Hoof Direct logo/wordmark
- Tagline: "Route-Optimized Scheduling for Farriers"
- App screenshot or mockup (optional)
- Clean background with brand gradient
- Text legible at small sizes

**File**: `store_assets/feature_graphic.png`

### 2.3 Screenshots

#### Phone Screenshots (Required)

**Specification**:
| Property | Requirement |
|----------|-------------|
| Aspect ratio | 16:9 or 9:16 |
| Min dimension | 320 px |
| Max dimension | 3840 px |
| Count | 2-8 images |
| Format | PNG or JPG (no alpha) |

**Required Screenshots** (in order):

| # | Screen | Caption |
|---|--------|---------|
| 1 | Route optimization map | "Optimize your daily route in one tap" |
| 2 | Calendar week view | "See your schedule at a glance" |
| 3 | Client list | "Manage clients and their horses" |
| 4 | Horse profile with due date | "Never miss a shoeing cycle" |
| 5 | Invoice screen | "Professional invoices in seconds" |
| 6 | Mileage report | "Track miles for tax deductions" |
| 7 | Offline indicator | "Works without cell service" |
| 8 | Subscription tiers | "Plans for every business size" |

**Screenshot Frame Treatment**:
- Device frame (Pixel 8 or similar)
- Text caption at top
- Brand color background
- Consistent styling across all

**Files**: `store_assets/screenshots/phone_01.png` through `phone_08.png`

#### Tablet Screenshots (Recommended)

**Specification**:
| Property | Requirement |
|----------|-------------|
| Aspect ratio | 16:9 |
| Min dimension | 1024 px (width) |
| Count | 2-8 images |

**Files**: `store_assets/screenshots/tablet_01.png` through `tablet_04.png`

### 2.4 Store Listing Content

#### Short Description
```
Save hours weekly with route-optimized scheduling for farriers.
```
- Character count: 61/80 max
- Keywords: route-optimized, scheduling, farriers
- Clear value proposition

#### Full Description

```
Hoof Direct is the only farrier scheduling app with intelligent route optimization. Stop wasting hours planning routes manually — let Hoof Direct find the fastest path through your daily appointments.

🗺️ ROUTE OPTIMIZATION
Automatically reorder your stops to minimize drive time. Save 20%+ on fuel and get home earlier.

📅 SMART SCHEDULING  
Create appointments, set up recurring visits, and never miss a shoeing cycle with automatic due date tracking.

🔄 CALENDAR SYNC
Two-way sync with Google Calendar and device calendars. Your schedule everywhere, automatically.

🔴 WORKS OFFLINE
Full functionality without cell service. Sync when you're back in range.

💰 SIMPLE INVOICING
Generate professional invoices in seconds. Track payments and see what's outstanding.

📊 MILEAGE TRACKING
Log trips for tax deductions. Export IRS-ready mileage reports.

SUBSCRIPTION TIERS:
• Free: Try with 10 clients
• Solo ($29/mo): Unlimited clients + 8-stop routes
• Growing ($79/mo): 15-stop routes + 2 users
• Multi ($149/mo): Unlimited routes + 5 users

Questions? Contact support@hoofdirect.com

Made by farriers, for farriers.
```

- Character count: ~1,150/4,000 max
- Structured with emoji headers for scannability
- Feature-benefit format
- Clear pricing information
- Support contact

#### Category
**Primary**: Business  
**Secondary**: (none - single category recommended)

#### Tags/Keywords
```
farrier, horseshoeing, equine, scheduling, route planning, 
horse care, appointment booking, mileage tracker, invoicing,
field service, mobile CRM, offline app
```

### 2.5 Content Rating

**Questionnaire Answers**:
- Violence: No
- Sexual content: No
- Language: No
- Controlled substances: No
- User interaction: No (no user-generated content shared between users)
- Location sharing: Yes (for route optimization - device only)
- Financial transactions: Yes (in-app purchases for subscriptions)

**Expected Rating**: Everyone

### 2.6 Contact Information

| Field | Value |
|-------|-------|
| Email | support@hoofdirect.com |
| Website | https://hoofdirect.com |
| Privacy Policy | https://hoofdirect.com/privacy |

---

## 3. Build Configuration

### 3.1 Release Build Setup

```kotlin
// app/build.gradle.kts
android {
    namespace = "com.hoofdirect.app"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.hoofdirect.app"
        minSdk = 26  // Android 8.0+
        targetSdk = 34
        versionCode = 1  // Increment for each release
        versionName = "1.0.0"
    }
    
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    
    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }
}
```

### 3.2 ProGuard Configuration

```proguard
# proguard-rules.pro

# Keep data models for Gson/Kotlinx Serialization
-keep class com.hoofdirect.app.core.domain.model.** { *; }
-keep class com.hoofdirect.app.core.database.entity.** { *; }
-keep class com.hoofdirect.app.data.remote.dto.** { *; }

# Supabase
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Crashlytics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Google Maps
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.maps.** { *; }
```

### 3.3 App Signing

**Play App Signing (Recommended)**:
- Enroll in Play App Signing
- Upload app signing key to Google
- Keep upload key locally
- Google manages distribution signing

**Key Storage**:
- Upload key stored securely (not in repo)
- Environment variables for CI/CD
- Backup in secure location (e.g., 1Password)

### 3.4 Version Management

**Version Code Strategy**:
```
Format: YYYYMMDDNN
Example: 2025011301 (Jan 13, 2025, build 01)
```

**Version Name Strategy**:
```
Format: Major.Minor.Patch
Example: 1.0.0, 1.0.1, 1.1.0
```

| Release Type | Version Bump |
|--------------|--------------|
| Bug fixes | Patch (1.0.X) |
| New features | Minor (1.X.0) |
| Breaking changes | Major (X.0.0) |

---

## 4. Pre-Launch Technical Checklist

### 4.1 Security

| Item | Status | Verification |
|------|--------|--------------|
| API keys in BuildConfig (not hardcoded) | ☐ | Code review |
| Google Maps API key restricted to app | ☐ | GCP Console |
| Supabase anon key only (no service key in app) | ☐ | Code review |
| ProGuard obfuscation working | ☐ | APK analysis |
| Network security config (HTTPS only) | ☐ | Manifest check |
| No sensitive data in logs | ☐ | Logcat review |
| Certificate pinning (optional) | ☐ | Network test |

**Network Security Config**:
```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

### 4.2 Crash Reporting

```kotlin
// core/analytics/CrashlyticsTree.kt
class CrashlyticsTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority >= Log.WARN) {
            Firebase.crashlytics.log(message)
            t?.let { Firebase.crashlytics.recordException(it) }
        }
    }
}

// Application.kt
class HoofDirectApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        if (!BuildConfig.DEBUG) {
            Timber.plant(CrashlyticsTree())
        }
        
        // Set user identifier for crash reports
        Firebase.crashlytics.setUserId(authRepository.currentUserId ?: "anonymous")
    }
}
```

### 4.3 Analytics Events

**Required Launch Events**:

| Event | Parameters | Trigger |
|-------|------------|---------|
| `app_launch` | version, first_launch | App opened |
| `sign_up` | method | Account created |
| `sign_in` | method | User logged in |
| `onboarding_complete` | steps_completed | Onboarding finished |
| `client_created` | - | New client added |
| `appointment_created` | - | New appointment |
| `route_optimized` | stop_count | Route calculated |
| `invoice_created` | amount | Invoice generated |
| `subscription_started` | tier, billing_period | Subscription purchased |
| `subscription_cancelled` | tier, reason | Subscription cancelled |

### 4.4 Deep Links

**App Links Configuration**:

```xml
<!-- AndroidManifest.xml -->
<activity
    android:name=".MainActivity"
    android:exported="true">
    
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="https"
            android:host="hoofdirect.com"
            android:pathPrefix="/app" />
    </intent-filter>
    
    <!-- Custom scheme for auth callbacks -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="hoofdirect"
            android:host="callback" />
    </intent-filter>
</activity>
```

**Digital Asset Links** (host at `https://hoofdirect.com/.well-known/assetlinks.json`):
```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.hoofdirect.app",
    "sha256_cert_fingerprints": ["SHA256_FINGERPRINT_HERE"]
  }
}]
```

### 4.5 Performance Targets

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Cold start time | < 2s | - | ☐ |
| App size (AAB) | < 30 MB | - | ☐ |
| Memory usage (idle) | < 150 MB | - | ☐ |
| Battery drain/hour | < 3% | - | ☐ |
| Network timeout | < 10s | - | ☐ |

---

## 5. Pre-Launch Quality Checklist

### 5.1 Functional Testing

| Area | Test Cases | Status |
|------|------------|--------|
| Authentication | Sign up, sign in, sign out, password reset | ☐ |
| Client management | Create, edit, delete, search | ☐ |
| Horse management | Create, edit, delete, photos | ☐ |
| Appointments | Create, edit, delete, complete, cancel | ☐ |
| Calendar | Day/week/month views, navigation | ☐ |
| Route optimization | Calculate, reorder, navigate | ☐ |
| Invoicing | Create, send, mark paid | ☐ |
| Mileage | Log trip, view report, export | ☐ |
| Subscriptions | Upgrade, downgrade, cancel | ☐ |
| Offline mode | All core features work offline | ☐ |
| Sync | Data syncs correctly when online | ☐ |

### 5.2 Device Testing Matrix

| Device | OS Version | Screen Size | Status |
|--------|------------|-------------|--------|
| Pixel 8 | Android 14 | 6.2" | ☐ |
| Pixel 6a | Android 14 | 6.1" | ☐ |
| Samsung S23 | Android 14 | 6.1" | ☐ |
| Samsung A54 | Android 13 | 6.4" | ☐ |
| OnePlus 11 | Android 13 | 6.7" | ☐ |
| Pixel 4a | Android 12 | 5.8" | ☐ |
| Samsung Tab S8 | Android 13 | 11" | ☐ |

### 5.3 Accessibility Checklist

| Item | Requirement | Status |
|------|-------------|--------|
| Touch targets | Min 48dp × 48dp | ☐ |
| Color contrast | 4.5:1 for text | ☐ |
| Content descriptions | All images/icons | ☐ |
| Screen reader | TalkBack compatible | ☐ |
| Font scaling | Supports 200% text size | ☐ |
| Focus order | Logical tab order | ☐ |

### 5.4 Bug Triage

**Before Launch**:
- P0 (Critical): 0 open
- P1 (High): 0 open
- P2 (Medium): ≤ 5 open with plan
- P3 (Low): Tracked for future

**Bug Priority Definitions**:

| Priority | Definition | Response |
|----------|------------|----------|
| P0 | App crash, data loss, security | Block release |
| P1 | Feature broken, major UX issue | Block release |
| P2 | Minor feature issue, workaround exists | Track for hotfix |
| P3 | Cosmetic, edge case | Track for future |

---

## 6. Beta Testing Plan

### 6.1 Internal Testing (Week 1)

**Track**: Internal testing (Firebase App Distribution)  
**Participants**: Development team (3-5 people)  
**Duration**: 3-5 days

**Focus Areas**:
- Critical user flows work end-to-end
- No crashes on primary paths
- Offline/sync behavior
- Subscription flows

**Exit Criteria**:
| Criteria | Target |
|----------|--------|
| Crash-free sessions | 100% |
| Core flows complete | Pass |
| P0 bugs | 0 |

### 6.2 Closed Beta (Weeks 2-3)

**Track**: Closed testing (Play Console)  
**Participants**: 20 farrier contacts  
**Duration**: 2 weeks

**Recruitment**:
- Personal contacts from Sal's network
- Farrier association members
- Social media outreach

**Feedback Collection**:
- In-app feedback button (opens Canny)
- Weekly survey via email
- Direct communication channel (Slack/Discord)

**Feedback Form Questions**:
1. How easy was it to set up your first client? (1-5)
2. Did route optimization save you time? (Yes/No/Didn't use)
3. Did you experience any crashes? (Yes/No, describe)
4. What feature would you most like improved?
5. Would you recommend Hoof Direct? (1-10 NPS)

**Exit Criteria**:
| Criteria | Target |
|----------|--------|
| Crash-free rate | > 99% |
| Average rating | > 4.0 |
| NPS score | > 30 |
| Critical feedback items | Addressed |

### 6.3 Open Beta (Week 4)

**Track**: Open testing (Play Console)  
**Participants**: Anyone who opts in  
**Duration**: 1 week

**Focus Areas**:
- Scale testing
- Edge cases from diverse usage
- Geographic diversity
- Device compatibility

**Exit Criteria**:
| Criteria | Target |
|----------|--------|
| Crash-free rate | > 99% |
| No new P0/P1 bugs | Pass |
| ANR rate | < 1% |

---

## 7. Staged Rollout Plan

### 7.1 Rollout Stages

| Stage | Percentage | Duration | Date (Example) |
|-------|------------|----------|----------------|
| Beta Complete | - | - | Day 0 |
| Stage 1 | 10% | 3 days | Day 1-3 |
| Stage 2 | 25% | 2 days | Day 4-5 |
| Stage 3 | 50% | 2 days | Day 6-7 |
| Stage 4 | 100% | - | Day 8+ |

### 7.2 Stage Advancement Criteria

**Advance to next stage when**:
- Crash-free rate remains > 99%
- ANR rate remains < 0.5%
- No new P0/P1 bugs reported
- No significant negative review patterns
- Error rates stable or improving

**Pause/Rollback when**:
- Crash-free rate drops below 98%
- ANR rate exceeds 1%
- P0 bug discovered
- Multiple similar complaints

### 7.3 Rollback Procedure

1. **Pause Rollout** (immediate):
   - Play Console → Release management → Halt rollout
   - Stops new users from getting update
   - Existing users keep current version

2. **Investigate** (within 1 hour):
   - Check Crashlytics for crash patterns
   - Review error logs
   - Identify root cause

3. **Decide Action**:
   - Hotfix: Patch and resume rollout
   - Full rollback: Revert to previous version
   - Wait: Monitor if self-resolving

4. **Communicate**:
   - Update status page
   - Respond to affected reviews
   - Internal team notification

---

## 8. Launch Day Procedures

### 8.1 Launch Day Timeline

**T-1 Day (Preparation)**:
- [ ] Final QA pass complete
- [ ] Release notes written
- [ ] Support team briefed
- [ ] Monitoring dashboards ready
- [ ] Rollback plan reviewed

**Launch Day Morning**:
| Time | Action | Owner |
|------|--------|-------|
| 8:00 AM | Team standup | All |
| 8:30 AM | Final go/no-go decision | Lead |
| 9:00 AM | Start 10% rollout | Dev |
| 9:15 AM | Verify rollout started | Dev |
| 9:30 AM | First monitoring check | QA |

**Launch Day Monitoring** (every 2 hours):
| Check | Source | Action if Concerning |
|-------|--------|---------------------|
| Crash rate | Crashlytics | Pause if > 2% |
| ANR rate | Play Console | Pause if > 1% |
| New reviews | Play Console | Respond within 1 hour |
| Support emails | Inbox | Prioritize and respond |
| Error rates | Server logs | Investigate anomalies |

**End of Day**:
- [ ] Review day's metrics
- [ ] Triage any new bugs
- [ ] Decision: Continue/pause rollout
- [ ] Team debrief

### 8.2 Monitoring Dashboard

**Crashlytics Dashboard**:
- Crash-free users (%)
- Crashes by version
- Top crash issues
- Affected users count

**Play Console Vitals**:
- ANR rate
- Crash rate
- Startup time
- Battery usage

**Firebase Analytics**:
- Active users (real-time)
- Key events (sign ups, clients created)
- Funnel completion rates

**Custom Alerts**:
- Crash rate > 2% → Slack notification
- ANR rate > 1% → Slack notification
- Error rate spike → PagerDuty (if configured)

---

## 9. Post-Launch Monitoring

### 9.1 Daily Checks (First Week)

| Metric | Source | Target |
|--------|--------|--------|
| Crash-free rate | Crashlytics | > 99% |
| ANR rate | Play Console | < 0.5% |
| New installs | Play Console | Track trend |
| Uninstalls | Play Console | < 10% |
| Reviews | Play Console | Respond all |
| Support emails | Inbox | < 24h response |
| Server errors | Supabase logs | < 0.1% |

### 9.2 Weekly Metrics (Ongoing)

| Metric | Calculation | Target |
|--------|-------------|--------|
| Downloads | Play Console | Growth week-over-week |
| DAU | Firebase | Track trend |
| WAU | Firebase | Track trend |
| Retention D1 | Firebase | > 40% |
| Retention D7 | Firebase | > 25% |
| Retention D30 | Firebase | > 15% |
| Trial → Paid | Custom event | > 10% |
| Average rating | Play Console | > 4.5 |

### 9.3 Review Response Guidelines

**Response Timing**: Within 24 hours

**Response Templates**:

**5-star review**:
```
Thank you for the great review! We're glad Hoof Direct is helping 
with your business. If you have any suggestions, we'd love to hear 
them at feedback.hoofdirect.com!
```

**Constructive feedback (3-4 stars)**:
```
Thanks for your feedback! We appreciate you taking the time to 
share your experience. We're working on [specific improvement]. 
Please reach out to support@hoofdirect.com if you have more details 
to share.
```

**Negative review (1-2 stars)**:
```
We're sorry to hear about your experience. We'd like to help make 
this right. Please email support@hoofdirect.com with details about 
[specific issue] and we'll prioritize getting this resolved for you.
```

**Bug report**:
```
Thank you for reporting this issue! Our team is investigating and 
we'll have a fix out soon. We've noted your feedback and will 
update you when the fix is available. Sorry for the inconvenience!
```

---

## 10. Contingency Plans

### 10.1 Crash Rate Spike

**Trigger**: Crash-free rate < 98%

**Immediate Actions**:
1. Pause rollout in Play Console
2. Post in team Slack channel
3. Open Crashlytics, identify top crash
4. Create P0 bug ticket

**Investigation**:
1. Identify affected versions/devices
2. Review crash stack traces
3. Check recent code changes
4. Reproduce if possible

**Resolution**:
1. Develop hotfix
2. Internal testing
3. Resume staged rollout (start at 5%)
4. Monitor closely

### 10.2 Negative Review Pattern

**Trigger**: 3+ similar complaints in 24 hours

**Immediate Actions**:
1. Respond to each review individually
2. Create bug ticket for underlying issue
3. Prioritize in sprint

**Resolution**:
1. Fix issue
2. Release update
3. Follow up with reviewers
4. Consider reaching out via Play Console developer reply

### 10.3 Low Downloads

**Trigger**: < 100 downloads in first week

**Investigation**:
1. Check ASO ranking
2. Review competitors' positions
3. Analyze keyword performance
4. Review screenshot appeal

**Actions**:
1. Update keywords based on search data
2. A/B test screenshots
3. Improve short description
4. Consider paid acquisition test ($500 budget)
5. Activate social media marketing

### 10.4 Server Issues

**Trigger**: API errors > 1% or timeout > 5%

**Immediate Actions**:
1. Check Supabase status page
2. Review server logs
3. Identify problematic endpoint

**Resolution**:
1. Scale resources if capacity issue
2. Fix bug if code issue
3. Optimize queries if performance issue
4. Add caching if repeated requests

---

## 11. Data Safety Form

### 11.1 Data Collection Declaration

| Data Type | Collected | Shared | Purpose |
|-----------|-----------|--------|---------|
| Name | Yes | No | Account, invoices |
| Email | Yes | No | Account, communication |
| Phone | Yes | No | Profile display |
| Address | Yes | No | Route planning |
| Location | Yes | No | Routes, mileage |
| Photos | Yes | No | Horse records |
| Financial info | Yes | Stripe | Payments |
| App activity | Yes | No | Analytics |
| Crash logs | Yes | Firebase | Stability |

### 11.2 Data Security Practices

| Practice | Implemented |
|----------|-------------|
| Data encrypted in transit | Yes (HTTPS) |
| Data encrypted at rest | Yes (Supabase) |
| Users can request deletion | Yes |
| Data deletion process | Account deletion → 30 days |

---

## 12. Acceptance Criteria

### Pre-Launch
| ID | Criteria | Verification |
|----|----------|--------------|
| AC-021-01 | App icon 512×512 uploaded | Play Console |
| AC-021-02 | Feature graphic 1024×500 uploaded | Play Console |
| AC-021-03 | 8 phone screenshots uploaded | Play Console |
| AC-021-04 | Short description ≤80 chars | Play Console |
| AC-021-05 | Full description ≤4000 chars | Play Console |
| AC-021-06 | Privacy policy URL live and accessible | Browser test |
| AC-021-07 | Terms of service URL live and accessible | Browser test |
| AC-021-08 | Data safety form completed | Play Console |
| AC-021-09 | Content rating questionnaire completed | Play Console |
| AC-021-10 | Release APK/AAB signed with production key | Build verification |

### Beta Testing
| ID | Criteria | Verification |
|----|----------|--------------|
| AC-021-11 | Internal testing crash-free rate 100% | Crashlytics |
| AC-021-12 | Closed beta has 20+ testers | Play Console |
| AC-021-13 | Closed beta crash-free rate > 99% | Crashlytics |
| AC-021-14 | Closed beta average rating > 4.0 | Survey |
| AC-021-15 | All P0/P1 bugs from beta resolved | Bug tracker |
| AC-021-16 | Open beta no new critical issues | Bug tracker |

### Launch
| ID | Criteria | Verification |
|----|----------|--------------|
| AC-021-17 | 10% rollout stable for 3 days | Play Console |
| AC-021-18 | 25% rollout stable for 2 days | Play Console |
| AC-021-19 | 50% rollout stable for 2 days | Play Console |
| AC-021-20 | 100% rollout achieved | Play Console |
| AC-021-21 | Crash-free rate > 99% maintained | Crashlytics |
| AC-021-22 | ANR rate < 0.5% maintained | Play Console |

### Post-Launch
| ID | Criteria | Verification |
|----|----------|--------------|
| AC-021-23 | All reviews responded within 24h | Play Console |
| AC-021-24 | Week 1 downloads > 500 | Play Console |
| AC-021-25 | Play Store rating > 4.0 | Play Console |

---

## 13. File References

### Store Assets
```
store_assets/
├── icon_512.png
├── feature_graphic.png
├── screenshots/
│   ├── phone_01_route.png
│   ├── phone_02_calendar.png
│   ├── phone_03_clients.png
│   ├── phone_04_horse.png
│   ├── phone_05_invoice.png
│   ├── phone_06_mileage.png
│   ├── phone_07_offline.png
│   ├── phone_08_pricing.png
│   ├── tablet_01_calendar.png
│   └── tablet_02_route.png
├── store_listing.txt
└── release_notes/
    └── v1.0.0.txt
```

### Build Files
```
app/
├── build.gradle.kts
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml
    └── res/
        └── xml/
            └── network_security_config.xml
```

### Monitoring
```
monitoring/
├── crashlytics_config.md
├── analytics_events.md
└── alert_thresholds.md
```

---

## 14. Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-01-13 | Initial FRD creation |
