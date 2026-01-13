# Hoof Direct: Product Plan for Native Mobile CRM

*Strategic plan for building a farrier-focused CRM that wins on route intelligence, reliability, and value*

---

## Executive Summary

Hoof Direct is a native mobile CRM for farriers that combines client/horse management with **intelligent route optimization**—a capability no competitor offers. By going native (Kotlin for Android, Swift for iOS), we gain superior GPS performance, reliable offline operation, and seamless calendar integration that cross-platform frameworks struggle to deliver.

**Market scope**: United States only at launch (~25,000 full-time farriers). International expansion (UK, Canada, Australia) deferred until US product-market fit achieved.

**Market opportunity**: The Farrier's App holds a weak position (3.1/5 rating, 870 downloads, $59/month) while Best Farrier App proves better execution wins (5.0/5 rating at $31/month). Neither offers route optimization—the feature farriers need most but don't know to ask for.

**Winning formula**:
1. Lead with route optimization (first-to-market, saves 5+ hours/week)
2. Price at $29/month (50% below The Farrier's App with more features)
3. Native apps that work flawlessly offline
4. Two-way calendar sync (Google Calendar, iCal)
5. Client portal for horse owners (future differentiator)

---

## 1. Product Vision

### Problem Statement

Farriers are mobile professionals covering 35-90 mile service radiuses, yet they plan routes manually, chase clients for confirmations, and juggle disconnected tools for scheduling, invoicing, and record-keeping. The result:

- **Wasted drive time**: No farrier app optimizes routes; farriers manually plot on Google Maps
- **No-shows and cancellations**: 29% of missed appointments happen because clients forgot
- **Payment friction**: Collecting payment at the truck while handling tools is awkward
- **Calendar chaos**: Appointments don't sync with personal/family calendars
- **Offline failures**: Rural barns have no cell service; apps that require connectivity fail

Existing solutions digitized paper workflows but missed the field service innovations proven in other industries.

### Solution

Hoof Direct is a native Android/iOS app that brings **field service intelligence** to farriers:

- **Route optimization** that reorders daily stops for minimum travel
- **Two-way calendar sync** with Google Calendar and iCal
- **Offline-first architecture** that works in zero-connectivity barns
- **Client confirmations** that reduce no-shows
- **One-tap invoicing** with flexible payment preferences

### Target User

**Primary**: Full-time professional farriers in the United States
- ~25,000 full-time farriers in US market
- Service 100-200 clients annually
- Manage 40-60 horses per week
- Travel 35-90 mile radius (10,000+ miles/year)
- Income range: $80,000-$150,000/year
- 60% already use some form of digital scheduling
- Frustrated by manual route planning

**Secondary (Future)**: Equine veterinarians, horse trainers, barn managers

**Geographic Scope**: United States only at launch
- Simplifies: currency (USD), SMS pricing, tax calculations, address formats
- International expansion (UK, Canada, Australia) evaluated post-product-market-fit
- UK market note: The Farrier's App is stronger there; US is the better beachhead

### Success Metrics

| Metric | Year 1 Target |
|--------|---------------|
| Registered users | 2,500 |
| Paid subscribers | 600 |
| Monthly churn | <4% |
| Play Store rating | 4.7+ stars |
| NPS score | 55+ |
| Avg. weekly time saved (reported) | 3+ hours |

---

## 2. Competitive Positioning

### Landscape Summary

| Competitor | Price | Rating | Strengths | Weaknesses |
|------------|-------|--------|-----------|------------|
| **The Farrier's App** | $59/mo | 3.1/5 | Multi-user, UK market | No routes, glitchy, expensive, poor support |
| **Best Farrier App** | $31/mo | 5.0/5 | UX, mileage tracking, support | No route optimization, single device |
| **EQUINET** | Free | 4.0/5 | Xero integration, free | US/AU/NZ only, limited features |
| **iForgeAhead** | $20+/yr | High | Invoicing depth | Dated, add-on pricing |

### Critical Gaps We Fill

| Gap | Current State | Hoof Direct Solution |
|-----|---------------|---------------------|
| Route optimization | **Zero competitors** | GPS-based daily routing, saves 20%+ drive time |
| Calendar sync | Export-only or none | Two-way Google Calendar + iCal |
| Confirmation tracking | One-way reminders | RSVP with easy rescheduling |
| Client portal | None | Horse owners book/pay online (Phase 2) |
| Batch scheduling | Horse-by-horse | Schedule entire barn at once |
| Reliable offline | Inconsistent | Native SQLite, guaranteed offline |

### Positioning Statement

> **For professional farriers** who waste hours planning routes and chasing confirmations, **Hoof Direct** is a native mobile CRM that **optimizes your daily route, syncs with your calendar, and works offline**—unlike The Farrier's App which costs twice as much without route intelligence, or generic scheduling apps that don't understand farrier workflows.

---

## 3. Design Principles

These principles guide all UI/UX decisions across PRDs:

### Core Principles

1. **Offline-first, always**: Every core feature must work without connectivity. Sync is invisible to the user.

2. **Field-ready UI**: Designed for use in barns, outdoors, and in bright sunlight with dirty or gloved hands.
   - Large touch targets (minimum 48dp, prefer 56dp for primary actions)
   - High contrast ratios (WCAG AA minimum, AAA preferred)
   - Readable in direct sunlight
   - Operable with work gloves

3. **Native Android excellence**: Material 3 (Material You) throughout. The app should feel like a first-class Android citizen, not a web wrapper or cross-platform port.

4. **Speed over features**: Target <100ms for all common interactions. A farrier checking their schedule at 6am shouldn't wait.

5. **Progressive disclosure**: Show essential info first, details on demand. Don't overwhelm with options.

6. **Respect existing workflows**: Adapt to how farriers already work. Don't force behavior change.

7. **Smart defaults, flexible overrides**: Optimize for the 80% case, but allow power users to customize.

### Material 3 Design System

| Element | Usage in Hoof Direct |
|---------|---------------------|
| **Dynamic Color** | Android 12+: app adapts to user's wallpaper. Older: branded teal/earth tone palette. |
| **NavigationBar** | Bottom nav: Schedule, Clients, Routes, Invoices, More |
| **LargeTopAppBar** | Detail screens (Client, Horse, Appointment) with collapsing behavior |
| **Extended FAB** | Primary action per screen ("New Appointment", "Optimize Route") |
| **Cards** | Client/horse list items, appointment summaries |
| **BottomSheet** | Quick actions, filters, appointment completion flow |
| **SegmentedButton** | View toggles (Day/Week/Agenda), service type selection |
| **SearchBar** | Client/horse search with recent + suggestions |
| **Snackbar** | Sync status, action confirmations, undo prompts |

### Color Semantics

| Color Role | Application |
|------------|-------------|
| Primary | Route actions, navigation, primary buttons |
| Secondary | Client/horse related elements |
| Tertiary | Financial (invoices, payments) |
| Error | Overdue invoices, validation errors, no-shows |
| Surface | Cards, sheets, backgrounds |

### Typography Scale

| Style | Usage |
|-------|-------|
| Display Large | Dashboard metrics, route summary |
| Headline | Screen titles, section headers |
| Title | Card titles, list item primary text |
| Body | Content, descriptions, notes |
| Label | Buttons, chips, metadata |

### Accessibility Requirements

- Minimum touch target: 48×48dp
- Color contrast: 4.5:1 for normal text, 3:1 for large text
- Support for TalkBack screen reader
- Support for system font scaling up to 200%
- No information conveyed by color alone

---

## 4. Technical Architecture

### Why Native Over Cross-Platform

| Requirement | Native Advantage |
|-------------|------------------|
| GPS/location accuracy | Direct access to platform APIs, better battery management |
| Offline reliability | Native SQLite, no JS bridge overhead |
| Calendar integration | Native CalendarContract (Android) / EventKit (iOS) |
| Background sync | WorkManager (Android) / BGTaskScheduler (iOS) |
| Map performance | Native Google Maps SDK, smoother rendering |
| App store optimization | Native apps rank higher, better reviews |

### Platform Strategy

**Phase 1 (Months 1-6)**: Android (Kotlin)
- 70%+ of farriers use Android (price-conscious demographic)
- Faster iteration on single platform
- Google Maps integration is more mature

**Phase 2 (Months 7-10)**: iOS (Swift)
- Port core logic via shared Kotlin Multiplatform (KMP) for business logic
- Native SwiftUI for iOS-specific UI
- Or pure Swift rewrite if team prefers

**Future consideration**: Kotlin Multiplatform Mobile (KMM) for shared business logic between Android and iOS while keeping native UI layers.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     HOOF DIRECT ARCHITECTURE                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌────────────────────────┐    ┌────────────────────────┐       │
│  │   ANDROID APP          │    │   iOS APP (Phase 2)    │       │
│  │   (Kotlin)             │    │   (Swift)              │       │
│  │                        │    │                        │       │
│  │  ┌──────────────────┐  │    │  ┌──────────────────┐  │       │
│  │  │  UI Layer        │  │    │  │  UI Layer        │  │       │
│  │  │  Jetpack Compose │  │    │  │  SwiftUI         │  │       │
│  │  │  + Material 3    │  │    │  │                  │  │       │
│  │  └────────┬─────────┘  │    │  └────────┬─────────┘  │       │
│  │           │            │    │           │            │       │
│  │  ┌────────▼─────────┐  │    │  ┌────────▼─────────┐  │       │
│  │  │  ViewModel       │  │    │  │  ViewModel       │  │       │
│  │  │  (MVVM)          │  │    │  │  (MVVM)          │  │       │
│  │  └────────┬─────────┘  │    │  └────────┬─────────┘  │       │
│  │           │            │    │           │            │       │
│  │  ┌────────▼─────────┐  │    │  ┌────────▼─────────┐  │       │
│  │  │  Repository      │  │    │  │  Repository      │  │       │
│  │  │  Layer           │  │    │  │  Layer           │  │       │
│  │  └────────┬─────────┘  │    │  └────────┬─────────┘  │       │
│  │           │            │    │           │            │       │
│  │  ┌────────▼─────────┐  │    │  ┌────────▼─────────┐  │       │
│  │  │  Local DB        │  │    │  │  Local DB        │  │       │
│  │  │  Room (SQLite)   │  │    │  │  Core Data       │  │       │
│  │  └────────┬─────────┘  │    │  └────────┬─────────┘  │       │
│  │           │            │    │           │            │       │
│  └───────────┼────────────┘    └───────────┼────────────┘       │
│              │                             │                    │
│              └──────────────┬──────────────┘                    │
│                             │                                   │
│                             ▼                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                      SUPABASE                            │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐         │   │
│  │  │  Postgres   │ │    Auth     │ │   Edge      │         │   │
│  │  │  Database   │ │   (GoTrue)  │ │  Functions  │         │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘         │   │
│  └──────────────────────────────────────────────────────────┘   │
│                             │                                   │
│              ┌──────────────┼──────────────┐                    │
│              │              │              │                    │
│              ▼              ▼              ▼                    │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐          │
│  │ Google Routes │ │    Twilio     │ │    Stripe     │          │
│  │ API           │ │    SMS        │ │    Billing    │          │
│  └───────────────┘ └───────────────┘ └───────────────┘          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Android Tech Stack

| Layer | Technology | Rationale |
|-------|------------|-----------|
| Language | Kotlin 1.9+ | Modern, null-safe, coroutines |
| UI | Jetpack Compose + **Material 3** | Declarative UI with Material You design system |
| Design System | Material Design 3 (Material You) | Dynamic color, modern components, Android 12+ theming |
| Architecture | MVVM + Clean Architecture | Testable, maintainable |
| DI | Hilt | Google-recommended, compile-time safe |
| Local DB | Room | SQLite abstraction, offline-first |
| Networking | Retrofit + OkHttp | Industry standard, interceptors |
| Async | Kotlin Coroutines + Flow | Native Kotlin concurrency |
| Maps | Google Maps SDK | Best Android integration |
| Routes | Google Routes API | Optimization endpoint |
| Calendar | CalendarContract API | Native Android calendar access |
| Background | WorkManager | Reliable background sync |
| Location | Fused Location Provider | Battery-efficient GPS |
| Image Loading | Coil | Kotlin-first, lightweight |
| Analytics | Firebase Analytics | Free, comprehensive |
| Crash Reporting | Firebase Crashlytics | Industry standard |

#### Material 3 Specifics

```
┌─────────────────────────────────────────────────────────────────┐
│                    MATERIAL 3 IMPLEMENTATION                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  DEPENDENCIES                                                   │
│  ├─ androidx.compose.material3:material3                        │
│  ├─ androidx.compose.material3:material3-window-size-class      │
│  └─ com.google.accompanist:accompanist-systemuicontroller       │
│                                                                 │
│  DYNAMIC COLOR (Android 12+)                                    │
│  ├─ dynamicDarkColorScheme() / dynamicLightColorScheme()        │
│  ├─ Fallback to branded theme on Android 11 and below           │
│  └─ User's wallpaper influences app accent colors               │
│                                                                 │
│  CORE COMPONENTS USED                                           │
│  ├─ TopAppBar (LargeTopAppBar for detail screens)               │
│  ├─ NavigationBar (bottom nav)                                  │
│  ├─ FloatingActionButton (extended FAB for primary actions)     │
│  ├─ Card (elevated and filled variants)                         │
│  ├─ ListItem (for client/horse lists)                           │
│  ├─ SearchBar (expandable search)                               │
│  ├─ DatePicker / TimePicker (Material 3 dialogs)                │
│  ├─ BottomSheet (for quick actions)                             │
│  ├─ Snackbar (feedback)                                         │
│  └─ SegmentedButton (view toggles: day/week/agenda)             │
│                                                                 │
│  TYPOGRAPHY                                                     │
│  ├─ Display: App title, large numbers                           │
│  ├─ Headline: Section headers                                   │
│  ├─ Title: Card titles, list item primary                       │
│  ├─ Body: Content text                                          │
│  └─ Label: Buttons, chips, captions                             │
│                                                                 │
│  COLOR SCHEME                                                   │
│  ├─ Primary: Route/navigation actions                           │
│  ├─ Secondary: Horse/client accents                             │
│  ├─ Tertiary: Financial/invoice elements                        │
│  ├─ Error: Validation, overdue invoices                         │
│  ├─ Surface variants: Cards, sheets                             │
│  └─ Dynamic color on Android 12+ with branded fallback          │
│                                                                 │
│  FIELD-READY ADAPTATIONS                                        │
│  ├─ Large touch targets (minimum 48dp)                          │
│  ├─ High contrast mode support                                  │
│  ├─ Bold text/large font support                                │
│  └─ Sunlight-readable color choices (sufficient contrast)       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### iOS Tech Stack (Phase 2)

| Layer | Technology |
|-------|------------|
| Language | Swift 5.9+ |
| UI | SwiftUI |
| Architecture | MVVM + Combine |
| Local DB | Core Data or SwiftData |
| Networking | URLSession + async/await |
| Maps | MapKit or Google Maps SDK |
| Calendar | EventKit |
| Background | BGTaskScheduler |
| Location | Core Location |

### Offline-First Data Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                    OFFLINE-FIRST SYNC FLOW                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  USER ACTION (e.g., create appointment)                         │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────┐                                            │
│  │  Write to Room  │  ← Always writes locally first             │
│  │  (SQLite)       │                                            │
│  └────────┬────────┘                                            │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────┐                                            │
│  │  Add to Sync    │  ← Queue for server sync                   │
│  │  Queue Table    │                                            │
│  └────────┬────────┘                                            │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────┐     ┌─────────────────┐                    │
│  │  Check Network  │────►│  OFFLINE        │                    │
│  │  Connectivity   │     │  Queue persists │                    │
│  └────────┬────────┘     └─────────────────┘                    │
│           │ ONLINE                                              │
│           ▼                                                     │
│  ┌─────────────────┐                                            │
│  │  WorkManager    │  ← Processes queue with retry              │
│  │  Background Job │                                            │
│  └────────┬────────┘                                            │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────┐                                            │
│  │  Supabase API   │  ← Upsert with conflict resolution         │
│  │  (Postgres)     │                                            │
│  └────────┬────────┘                                            │
│           │                                                     │
│           ▼                                                     │
│  ┌─────────────────┐                                            │
│  │  Mark Synced    │  ← Remove from queue                       │
│  │  in Local DB    │                                            │
│  └─────────────────┘                                            │
│                                                                 │
│  CONFLICT RESOLUTION: Last-write-wins with updated_at timestamp │
│  SYNC TRIGGERS: App foreground, network restore, manual pull    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Calendar Sync Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                   CALENDAR SYNC STRATEGY                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  HOOF DIRECT → DEVICE CALENDAR (Export)                         │
│  ─────────────────────────────────────                          │
│  • Use CalendarContract API to write events                     │
│  • Create dedicated "Hoof Direct" calendar                      │
│  • Sync on: appointment create/update/delete                    │
│  • Include: client name, address, horses, navigation link       │
│                                                                 │
│  DEVICE CALENDAR → HOOF DIRECT (Import as Blocked Time)         │
│  ─────────────────────────────────────────────────────          │
│  • Read events from selected calendars (user chooses)           │
│  • Import as "blocked" time slots (not full appointments)       │
│  • Show in scheduling UI to prevent double-booking              │
│  • Sync on: app open, background refresh (WorkManager)          │
│                                                                 │
│  GOOGLE CALENDAR REAL-TIME SYNC (Optional Enhancement)          │
│  ─────────────────────────────────────────────────────          │
│  • Google Calendar API with push notifications                  │
│  • Webhook to Edge Function on calendar change                  │
│  • Near-instant sync vs. polling                                │
│  • Requires user OAuth consent                                  │
│                                                                 │
│  CONFLICT HANDLING                                              │
│  ─────────────────                                              │
│  • Hoof Direct appointments are source of truth                 │
│  • External calendar events block time but don't create appts   │
│  • If user edits HD event in Google Calendar → pull changes     │
│  • Clear visual distinction: HD events vs blocked time          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. Data Model

### Core Entities

```
┌─────────────────────────────────────────────────────────────────┐
│                      CORE DATA MODEL                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  users (Supabase auth.users extension)                          │
│  ├─ id: UUID (PK, FK to auth.users)                             │
│  ├─ email: String                                               │
│  ├─ business_name: String                                       │
│  ├─ phone: String                                               │
│  ├─ address: String (home base for route calculations)          │
│  ├─ home_latitude: Double                                       │
│  ├─ home_longitude: Double                                      │
│  ├─ service_radius_miles: Int (default: 50)                     │
│  ├─ default_appointment_duration_minutes: Int (default: 45)     │
│  ├─ default_shoeing_cycle_weeks: Int (default: 6)               │
│  ├─ working_hours: JSON {mon: {start, end}, tue: {...}, ...}    │
│  ├─ payment_preferences: JSON (see below)                       │
│  ├─ subscription_tier: Enum (free, solo, growing, multi)        │
│  ├─ stripe_customer_id: String?                                 │
│  ├─ subscription_status: Enum (active, past_due, canceled)      │
│  ├─ current_period_end: Timestamp?                              │
│  ├─ created_at: Timestamp                                       │
│  └─ updated_at: Timestamp                                       │
│                                                                 │
│  payment_preferences JSON structure:                            │
│  {                                                              │
│    "venmo": { "handle": "@username", "enabled": true },         │
│    "cashapp": { "cashtag": "$username", "enabled": true },      │
│    "zelle": { "email_or_phone": "...", "enabled": false },      │
│    "square": { "link": "https://...", "enabled": false },       │
│    "check_payable_to": "Business Name",                         │
│    "cash_accepted": true,                                       │
│    "preferred_method": "venmo"                                  │
│  }                                                              │
│                                                                 │
│  clients                                                        │
│  ├─ id: UUID (PK)                                               │
│  ├─ user_id: UUID (FK, RLS enforced)                            │
│  ├─ name: String                                                │
│  ├─ email: String?                                              │
│  ├─ phone: String (US format: +1XXXXXXXXXX)                     │
│  ├─ address: String (street address)                            │
│  ├─ city: String                                                │
│  ├─ state: String (2-letter US state code)                      │
│  ├─ zip: String (5-digit or ZIP+4)                              │
│  ├─ latitude: Double?                                           │
│  ├─ longitude: Double?                                          │
│  ├─ access_notes: String? (gate codes, directions, etc.)        │
│  ├─ general_notes: String?                                      │
│  ├─ custom_pricing: JSON? (overrides default service prices)    │
│  ├─ reminder_preference: Enum (sms, email, both, none)          │
│  ├─ reminder_hours_before: Int (default: 24)                    │
│  ├─ requires_confirmation: Boolean (default: false)             │
│  ├─ preferred_days: JSON? [mon, tue, wed...]                    │
│  ├─ preferred_time_range: JSON? {start: "08:00", end: "12:00"}  │
│  ├─ is_active: Boolean (default: true)                          │
│  ├─ created_at: Timestamp                                       │
│  └─ updated_at: Timestamp                                       │
│                                                                 │
│  horses                                                         │
│  ├─ id: UUID (PK)                                               │
│  ├─ user_id: UUID (FK, RLS enforced)                            │
│  ├─ client_id: UUID (FK)                                        │
│  ├─ name: String                                                │
│  ├─ breed: String?                                              │
│  ├─ color: String?                                              │
│  ├─ age_years: Int?                                             │
│  ├─ temperament_notes: String? (behavior, handling tips)        │
│  ├─ medical_notes: String? (lameness, allergies, conditions)    │
│  ├─ default_service_type: Enum (trim, front, full, corrective)  │
│  ├─ shoeing_cycle_weeks: Int? (null = use user default)         │
│  ├─ last_service_date: Date?                                    │
│  ├─ next_due_date: Date? (calculated)                           │
│  ├─ photos: JSON? [{ url, caption, date }]                      │
│  ├─ is_active: Boolean (default: true)                          │
│  ├─ created_at: Timestamp                                       │
│  └─ updated_at: Timestamp                                       │
│                                                                 │
│  appointments                                                   │
│  ├─ id: UUID (PK)                                               │
│  ├─ user_id: UUID (FK, RLS enforced)                            │
│  ├─ client_id: UUID (FK)                                        │
│  ├─ date: Date                                                  │
│  ├─ time: Time                                                  │
│  ├─ duration_minutes: Int                                       │
│  ├─ status: Enum (scheduled, confirmed, completed,              │
│  │               cancelled, no_show)                            │
│  ├─ location_override: String? (if not at client address)       │
│  ├─ latitude_override: Double?                                  │
│  ├─ longitude_override: Double?                                 │
│  ├─ notes: String?                                              │
│  ├─ reminder_sent_at: Timestamp?                                │
│  ├─ confirmation_received_at: Timestamp?                        │
│  ├─ route_order: Int? (position in day's optimized route)       │
│  ├─ estimated_arrival: Time? (from route calculation)           │
│  ├─ actual_arrival: Time?                                       │
│  ├─ completed_at: Timestamp?                                    │
│  ├─ calendar_event_id: String? (device calendar reference)      │
│  ├─ created_at: Timestamp                                       │
│  └─ updated_at: Timestamp                                       │
│                                                                 │
│  appointment_horses (junction table)                            │
│  ├─ id: UUID (PK)                                               │
│  ├─ appointment_id: UUID (FK)                                   │
│  ├─ horse_id: UUID (FK)                                         │
│  ├─ service_type: Enum (trim, front_shoes, full_set,            │
│  │                      corrective, other)                      │
│  ├─ service_description: String? (for "other" type)             │
│  ├─ price: Decimal                                              │
│  ├─ notes: String?                                              │
│  ├─ photos_before: JSON? [{ url, caption }]                     │
│  ├─ photos_after: JSON? [{ url, caption }]                      │
│  └─ created_at: Timestamp                                       │
│                                                                 │
│  invoices                                                       │
│  ├─ id: UUID (PK)                                               │
│  ├─ user_id: UUID (FK, RLS enforced)                            │
│  ├─ client_id: UUID (FK)                                        │
│  ├─ appointment_id: UUID? (FK, nullable for manual invoices)    │
│  ├─ invoice_number: String (auto-generated, unique per user)    │
│  ├─ line_items: JSON [{ description, quantity, unit_price }]    │
│  ├─ subtotal: Decimal                                           │
│  ├─ tax_rate: Decimal?                                          │
│  ├─ tax_amount: Decimal?                                        │
│  ├─ total: Decimal                                              │
│  ├─ status: Enum (draft, sent, viewed, paid, overdue, void)     │
│  ├─ due_date: Date?                                             │
│  ├─ sent_at: Timestamp?                                         │
│  ├─ viewed_at: Timestamp?                                       │
│  ├─ paid_at: Timestamp?                                         │
│  ├─ payment_method: String? (how client paid)                   │
│  ├─ notes: String?                                              │
│  ├─ created_at: Timestamp                                       │
│  └─ updated_at: Timestamp                                       │
│                                                                 │
│  service_prices (farrier's price list)                          │
│  ├─ id: UUID (PK)                                               │
│  ├─ user_id: UUID (FK, RLS enforced)                            │
│  ├─ service_type: Enum                                          │
│  ├─ name: String (display name)                                 │
│  ├─ default_price: Decimal                                      │
│  ├─ estimated_duration_minutes: Int                             │
│  ├─ description: String?                                        │
│  ├─ is_active: Boolean                                          │
│  └─ sort_order: Int                                             │
│                                                                 │
│  mileage_logs                                                   │
│  ├─ id: UUID (PK)                                               │
│  ├─ user_id: UUID (FK, RLS enforced)                            │
│  ├─ date: Date                                                  │
│  ├─ start_address: String?                                      │
│  ├─ end_address: String?                                        │
│  ├─ miles: Decimal                                              │
│  ├─ purpose: Enum (client_visit, supply_run, training, other)   │
│  ├─ appointment_id: UUID? (FK, if linked to appointment)        │
│  ├─ notes: String?                                              │
│  ├─ auto_tracked: Boolean (GPS vs manual entry)                 │
│  └─ created_at: Timestamp                                       │
│                                                                 │
│  route_plans (cached optimized routes)                          │
│  ├─ id: UUID (PK)                                               │
│  ├─ user_id: UUID (FK, RLS enforced)                            │
│  ├─ date: Date                                                  │
│  ├─ start_location: JSON { address, lat, lng }                  │
│  ├─ end_location: JSON { address, lat, lng }                    │
│  ├─ stops: JSON [{ appointment_id, order, lat, lng,             │
│  │                 estimated_arrival, drive_minutes }]          │
│  ├─ total_distance_miles: Decimal                               │
│  ├─ total_drive_minutes: Int                                    │
│  ├─ optimized_at: Timestamp                                     │
│  ├─ optimization_source: Enum (google, manual)                  │
│  └─ created_at: Timestamp                                       │
│                                                                 │
│  sync_queue (local only, not synced to server)                  │
│  ├─ id: Int (PK, auto-increment)                                │
│  ├─ entity_type: String (clients, horses, appointments, etc.)   │
│  ├─ entity_id: UUID                                             │
│  ├─ operation: Enum (insert, update, delete)                    │
│  ├─ payload: JSON                                               │
│  ├─ created_at: Timestamp                                       │
│  ├─ retry_count: Int                                            │
│  └─ last_error: String?                                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. Subscription Tiers & Pricing

### Route-Based Pricing Model

The paid differentiator is **route optimization**, not client counts. This aligns with our cost structure (Google Routes API) and value proposition.

| Tier | Price | Target User | Route Features | Other Features |
|------|-------|-------------|----------------|----------------|
| **Free** | $0 | Evaluation | Manual route view (no optimization) | 10 clients, 30 horses, 50 photos, 1 user, basic CRM |
| **Solo Farrier** | $29/mo | Solo operator, <100 clients | 8 stops/day optimization | Unlimited clients/horses/photos, 1 user, calendar sync, SMS reminders (50/mo), mileage tracking |
| **Growing Practice** | $79/mo | Growing business, 100-200 clients | 15 stops/day, multi-day planning | 2 users, SMS (200/mo), batch scheduling, advanced reports |
| **Multi-Farrier** | $149/mo | Teams, 200+ clients | Unlimited stops, fleet view | 5 users, SMS (500/mo), priority support, custom branding on invoices |

**Annual discount**: 20% (effectively 2.4 months free)

**Trial period**: 30 days on any paid tier, no credit card required to start

**Refund policy**: Full month refund, no questions asked

**Multi-device**: All tiers allow login on multiple devices simultaneously

### Why This Pricing Works

1. **$29 undercuts The Farrier's App by 50%** while offering route optimization they don't have
2. **Route limits align with API costs** (Google Routes charges per request)
3. **No client count limits on paid tiers** removes friction and anxiety
4. **SMS limits are transparent** (main variable cost)
5. **Upgrade path is clear**: more routes, more users, more SMS

### Feature Comparison vs. Competitors

| Feature | Hoof Direct Solo ($29) | The Farrier's App ($59) | Best Farrier ($31) |
|---------|------------------------|-------------------------|-------------------|
| Route optimization | ✅ 8 stops/day | ❌ None | ❌ None |
| Calendar sync | ✅ Two-way | ❌ None | ⚠️ Export only |
| Clients | ✅ Unlimited | ✅ Unlimited | ✅ Unlimited |
| Offline mode | ✅ Native SQLite | ⚠️ Reportedly glitchy | ✅ Yes |
| SMS reminders | ✅ 50/month included | ⚠️ Extra cost? | ⚠️ Limited |
| Mileage tracking | ✅ Included | ❌ No | ✅ Yes |
| Play Store rating | 🎯 Target: 4.7+ | 3.1/5 | 4.6/5 |

---

## 7. Feature Specifications by PRD

### Phase 1: Foundation (Weeks 1-8)

#### PRD-001: Authentication & User Profiles

**Scope**: Account creation, login, password reset, business profile setup

**Key Features**:
- Email/password authentication via Supabase Auth
- Business profile: name, phone, address (home base), service radius
- Payment preferences configuration (Venmo, Cash App, Square, Zelle, etc.)
- Working hours setup
- Profile photo upload

**Acceptance Criteria**:
- Account creation with email verification
- Secure login with session persistence
- Password reset flow
- Profile editable at any time
- Home address geocoded for route calculations

---

#### PRD-002: Offline-First Data Architecture

**Scope**: Room database, sync queue, background sync with WorkManager

**Key Features**:
- All data stored locally in Room (SQLite)
- Sync queue for pending server operations
- WorkManager jobs for background sync
- Network state detection with graceful degradation
- Conflict resolution (last-write-wins)
- Sync status indicator in UI

**Acceptance Criteria**:
- App fully functional in airplane mode
- Data syncs within 30 seconds of connectivity restoration
- No data loss in any connectivity scenario
- Clear visual indication of sync status
- Manual "sync now" option

---

#### PRD-003: Client Management

**Scope**: CRUD operations for clients, search, geocoding

**Key Features**:
- Create client: name (required), phone (required), email, address
- Address autocomplete via Google Places API
- Automatic geocoding to lat/long
- Access notes field (gate codes, parking, directions)
- Client preferences (preferred days, time ranges)
- Reminder preferences per client
- Custom pricing overrides
- Soft delete (archive, not destroy)

**Acceptance Criteria**:
- Client list with search and filter
- Client detail view with all horses and upcoming appointments
- Address geocoded on save (when online) or queued for later
- Archived clients hidden but recoverable

---

#### PRD-004: Horse Management

**Scope**: Horse records linked to clients, service history

**Key Features**:
- Create horse linked to client
- Fields: name, breed, color, age, temperament notes, medical notes
- Default service type per horse
- Custom shoeing cycle (override user default)
- Photo gallery per horse
- Service history view (past appointments)
- "Due soon" calculation based on last service + cycle

**Acceptance Criteria**:
- Add/edit/archive horses
- View all horses for a client
- See complete service history
- "Due soon" list across all horses
- Photos stored locally, synced to cloud storage

---

### Phase 2: Scheduling Core (Weeks 9-14)

#### PRD-005: Appointment Creation & Management

**Scope**: Create, edit, cancel appointments with horse selection

**Key Features**:
- Create appointment for client + date/time
- Select horses from client's horses
- Set service type and price per horse (pre-filled from defaults)
- Appointment statuses: scheduled, confirmed, completed, cancelled, no_show
- Recurring appointment creation (every X weeks)
- Location override (if not at client's address)
- Notes field
- Quick actions: complete, cancel, reschedule

**Acceptance Criteria**:
- Create appointment in <30 seconds
- Prices auto-populate from service prices (client custom or default)
- Recurring creates individual appointment records
- Status changes logged with timestamp
- Completing appointment prompts invoice creation

---

#### PRD-006: Calendar Views

**Scope**: Day, week, agenda views with navigation

**Key Features**:
- Agenda view (default): chronological list of upcoming appointments
- Day view: time-blocked schedule for selected day
- Week view: overview of week with appointment counts per day
- Color coding by status (scheduled=blue, confirmed=green, etc.)
- Quick navigation: today, +/- day/week
- Pull-to-refresh for sync
- Blocked time from external calendars shown (grayed out)

**Acceptance Criteria**:
- All views render in <100ms
- Smooth scrolling and navigation
- Tap appointment to view/edit
- External blocked time clearly distinguished
- Empty states with helpful prompts

---

#### PRD-007: Device Calendar Sync

**Scope**: Two-way sync with Google Calendar and device calendars

**Key Features**:
- Create "Hoof Direct" calendar on device
- Export appointments to device calendar automatically
- Import events from selected calendars as blocked time
- Event details: client name, address, horses, tap-to-navigate
- Sync triggers: appointment change, app open, background refresh
- User controls which calendars to read from

**Technical Implementation**:
- Android: CalendarContract API
- Background sync: WorkManager (every 15 minutes when app not in use)
- Real-time sync when app is open

**Acceptance Criteria**:
- New appointment appears in device calendar within 1 minute
- Appointment edits sync to device calendar
- External calendar events show as blocked time
- Delete in Hoof Direct removes from device calendar
- User can disable sync if desired

---

#### PRD-008: Reminders & Confirmations

**Scope**: SMS/email reminders with confirmation tracking

**Key Features**:
- Default reminder: 24 hours before appointment (global setting)
- Per-client override: configurable reminder timing (12hr, 24hr, 48hr, 72hr, none)
- Reminder content: date, time, address, farrier contact
- SMS via Twilio (branded sender ID "Hoof Direct"), email via SendGrid/Supabase
- Confirmation request option (client can confirm via link)
- Confirmation status tracked per appointment
- Farrier daily digest: morning summary of day's appointments (push notification)
- No-show follow-up automation (optional)
- SMS hard stop at tier limit with upgrade prompt

**Acceptance Criteria**:
- Reminders sent at scheduled time (within 5 minute window)
- SMS count tracked against tier limit
- Hard stop when limit reached (clear upgrade CTA, no overage)
- Confirmation link works on mobile
- Failed sends logged and visible to user
- Unsubscribe link in SMS (CAN-SPAM compliance)

---

### Phase 3: Route Intelligence (Weeks 15-20)

#### PRD-009: Maps Integration

**Scope**: Map view of clients and appointments, navigation handoff

**Key Features**:
- Map view showing all client locations as pins
- Filter: all clients, today's appointments, this week's appointments
- Pin colors: upcoming appointment, past due, no upcoming
- Tap pin to see client info
- "Navigate" button opens Google Maps/Waze with address
- Current location indicator
- Service radius circle visualization

**Acceptance Criteria**:
- Map loads in <2 seconds
- Smooth pan/zoom
- Pins clustered when zoomed out
- Navigation handoff works to Google Maps and Waze
- Works offline with cached map tiles (limited area)

---

#### PRD-010: Route Optimization

**Scope**: Optimize daily appointment order for minimum travel time

**Key Features**:
- "Optimize route" button on day view
- Input: day's appointments + start location (home or current) + end location
- Output: optimized stop order with estimated drive times
- Total route stats: miles, drive time, time at stops
- Manual reorder via drag-and-drop
- Save optimized route to appointments (route_order field)
- "Start route" mode: step-by-step navigation through stops

**Technical Implementation**:
- Google Routes API (Directions API with waypoint optimization)
- Cache route plans (route_plans table)
- Re-optimize if appointments change

**Tier Limits**:
- Free: View route (not optimized)
- Solo: 8 stops/day
- Growing: 15 stops/day
- Multi: Unlimited

**Acceptance Criteria**:
- Optimization completes in <5 seconds
- User can accept or modify suggested order
- Drive time estimates within 10% of actual
- Works with 1-15 stops (more on higher tiers)
- Graceful handling when offline (show last cached or disable)

---

#### PRD-011: Mileage Tracking

**Scope**: Manual and automatic mileage logging for US tax deduction

**Key Features**:
- Manual entry: date, start, end, miles, purpose
- Auto-track option: background GPS logging during work hours
- Link mileage to specific appointment
- Daily mileage totals
- IRS standard mileage rate (2024: $0.67/mile, updated annually)
- Business vs. personal trip categorization
- Export to CSV for tax filing (Schedule C compatible)
- Annual summary report with estimated deduction

**US Tax Specifics**:
- IRS standard rate stored as app config (updated with app releases)
- Supports both standard mileage and actual expense methods (tracking only)
- Disclaimer: "Consult a tax professional for advice"

**Technical Implementation**:
- Fused Location Provider for battery-efficient GPS
- Geofencing to detect arrival/departure at client locations
- User opt-in required for auto-tracking

**Acceptance Criteria**:
- Manual entry takes <15 seconds
- Auto-tracking respects battery (not continuous GPS)
- Accurate within 5% of actual miles
- Annual report calculates total miles × IRS rate
- Export works offline (exports local data)
- Clear IRS disclaimer on reports

---

### Phase 4: Financial Tools (Weeks 21-26)

#### PRD-012: Service Price List

**Scope**: Configure default service types and prices

**Key Features**:
- Pre-populated common services: trim, front shoes, full set, corrective
- Add custom service types
- Set price and estimated duration per service
- Reorder services (affects display order in appointment)
- Mark services as inactive (hidden but preserved)

**Acceptance Criteria**:
- New users see sensible defaults
- Prices used as defaults when adding horses to appointments
- Custom services fully supported
- Changes don't affect past appointments

---

#### PRD-013: Invoicing

**Scope**: Generate, send, and track invoices

**Key Features**:
- Auto-generate invoice when appointment marked complete
- Manual invoice creation (without appointment)
- Line items from appointment services
- Add additional line items (trip fee, supplies, etc.)
- Tax rate configuration (optional)
- Invoice statuses: draft, sent, viewed, paid, overdue, void
- Send via email (PDF attachment) or SMS (link to web view)
- Payment preferences displayed on invoice
- Mark as paid with payment method notation
- Outstanding invoice list with aging

**Technical Note**: PDF generation is client-side (in-app) for offline capability. Farrier can generate PDF while offline, queue for send when online.

**Acceptance Criteria**:
- Invoice generated in <3 seconds from completed appointment
- PDF generation works offline (stored locally until sent)
- Professional PDF design with business logo
- Email delivery confirmation
- "Mark paid" updates status immediately
- Overdue detection (configurable days after due date)

---

#### PRD-014: Payment Preferences Display

**Scope**: Configure and display farrier's accepted payment methods

**Key Features**:
- Supported methods: Venmo, Cash App, Zelle, Square, check, cash
- Each method: handle/link + enabled toggle
- Set preferred/primary method
- Display on invoices
- QR code generation for Venmo/Cash App (stretch goal)

**Acceptance Criteria**:
- Easy setup in profile
- Selected methods appear on all invoices
- Clear instructions for each payment type
- QR codes scannable by payment apps

---

### Phase 5: Monetization (Weeks 27-32)

#### PRD-015: Web Marketing Site

**Scope**: Landing page, features, pricing, download CTA

**Key Features**:
- Hero section with value proposition
- Feature highlights with screenshots
- Pricing table with tier comparison
- Testimonials (post-launch)
- "Download on Google Play" primary CTA
- FAQ section
- Contact/support link

**Tech**: Next.js static site, deployed on Vercel

**Acceptance Criteria**:
- Mobile-responsive
- Page load <2 seconds
- Clear download path
- SEO optimized (meta tags, sitemap, structured data)

---

#### PRD-016: Subscription Management

**Scope**: Stripe integration, web checkout, tier enforcement

**Key Features**:
- Free tier with limits (10 clients, 30 horses, no route optimization)
- "Upgrade" button in app opens web checkout
- Stripe Checkout for payment
- Stripe Customer Portal for subscription management
- Webhook handler updates user tier in Supabase
- Grace period for failed payments (7 days)
- Downgrade to free preserves data (just limits features)

**App-to-Web Flow**:
1. User taps "Upgrade" in app
2. App generates one-time token, opens browser with token
3. Web validates token, creates Stripe Checkout session
4. User completes payment
5. Webhook fires, updates user record
6. App detects tier change on next sync (or push notification)

**Acceptance Criteria**:
- Upgrade flow completes in <60 seconds
- Tier change reflected in app within 1 minute
- Customer Portal accessible from app
- Failed payment notifications sent
- Downgrade applies at period end, not immediately

---

#### PRD-017: Usage Limits Enforcement

**Scope**: Enforce tier limits in app

**Key Features**:
- Track counts: clients, horses, route optimizations, SMS sent
- Soft warnings at 80% of limit
- Hard blocks at 100% with upgrade prompt
- Limits checked locally (offline-compatible)
- Cached tier info, refreshed on sync
- Admin override capability (support can grant exceptions)

**Limits by Tier**:

| Resource | Free | Solo | Growing | Multi |
|----------|------|------|---------|-------|
| Clients | 10 | ∞ | ∞ | ∞ |
| Horses | 30 | ∞ | ∞ | ∞ |
| Photos | 50 | ∞ | ∞ | ∞ |
| Route stops/day | 0 (view only) | 8 | 15 | ∞ |
| SMS/month | 0 | 50 | 200 | 500 |
| Users | 1 | 1 | 2 | 5 |
| Devices per user | ∞ | ∞ | ∞ | ∞ |

**Acceptance Criteria**:
- Limits enforced consistently
- Clear messaging when limit reached
- Upgrade CTA prominent but not annoying
- Limits update immediately on tier change

---

### Phase 6: Polish & Launch (Weeks 33-38)

#### PRD-018: Reporting & Analytics

**Scope**: Business reports for farrier insights

**Key Features**:
- Dashboard: key metrics at a glance (appointments, revenue, miles)
- Revenue report: by period, by client, by service type
- Appointment report: completed, cancelled, no-show rates
- Mileage report: total miles, estimated deduction
- Client report: appointment frequency, revenue per client
- "Due soon" report: horses approaching service date
- Date range filters on all reports
- Export to CSV

**Acceptance Criteria**:
- Dashboard loads in <1 second
- Reports calculate from local data (offline-capable)
- Charts for visual representation
- Export works offline

---

#### PRD-019: Settings & Preferences

**Scope**: App settings, defaults, data management

**Key Features**:
- Profile editing
- Default appointment duration
- Default shoeing cycle
- Default reminder timing (24hr default)
- Working hours
- Notification preferences (push, SMS, email)
- Calendar sync settings
- Data export (JSON format, complete account backup)
- Account deletion request (30-day soft delete, then hard delete)
- Feedback/feature requests link (Canny board)
- App version and support email link

**Acceptance Criteria**:
- All settings persist across app restarts
- Settings sync to server
- Data export generates downloadable JSON file
- Clear path to contact support (email)
- Feedback link opens Canny in browser
- Account deletion clearly explains 30-day recovery window

---

#### PRD-020: Onboarding Flow

**Scope**: First-time user experience

**Key Features**:
- Welcome screen with value props
- Guided setup: profile → first client → first horse → first appointment
- Permission requests with context (location, calendar, notifications)
- Skip option for experienced users
- Tooltips on first use of key features
- "Sample data" option for exploration (clearly deletable)

**Acceptance Criteria**:
- Onboarding completable in <3 minutes
- Each step clearly explained
- Permissions requested with clear rationale
- Skip doesn't penalize (can access full app)
- Onboarding replayable from settings

---

#### PRD-021: Play Store Launch Preparation

**Scope**: Store assets, beta testing, launch checklist

**Key Features**:
- App icon (512x512) and feature graphic (1024x500)
- Screenshots (phone and tablet) for all key features
- Short description (<80 chars) and full description (<4000 chars)
- Privacy policy URL
- App category: Business
- Content rating questionnaire
- Beta testing via Play Console (internal → closed → open)
- Staged rollout (10% → 50% → 100%)

**Acceptance Criteria**:
- All store assets meet Google's specifications
- Privacy policy live and linked
- 20+ beta testers provide feedback
- No crash rate above 1%
- 4.5+ star rating from beta testers
- Staged rollout completed without major issues

---

## 8. Development Timeline

```
┌─────────────────────────────────────────────────────────────────┐
│                    DEVELOPMENT TIMELINE                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  PHASE 1: FOUNDATION (Weeks 1-8)                                │
│  ├─ Week 1-2: Project setup, architecture, Supabase config      │
│  ├─ Week 3-4: Auth + user profiles (PRD-001)                    │
│  ├─ Week 5-6: Offline architecture + Room setup (PRD-002)       │
│  ├─ Week 7: Client management (PRD-003)                         │
│  └─ Week 8: Horse management (PRD-004)                          │
│                                                                 │
│  PHASE 2: SCHEDULING (Weeks 9-14)                               │
│  ├─ Week 9-10: Appointment management (PRD-005)                 │
│  ├─ Week 11: Calendar views (PRD-006)                           │
│  ├─ Week 12-13: Device calendar sync (PRD-007)                  │
│  └─ Week 14: Reminders & confirmations (PRD-008)                │
│                                                                 │
│  PHASE 3: ROUTE INTELLIGENCE (Weeks 15-20)                      │
│  ├─ Week 15-16: Maps integration (PRD-009)                      │
│  ├─ Week 17-18: Route optimization (PRD-010)                    │
│  └─ Week 19-20: Mileage tracking (PRD-011)                      │
│                                                                 │
│  PHASE 4: FINANCIAL TOOLS (Weeks 21-26)                         │
│  ├─ Week 21: Service price list (PRD-012)                       │
│  ├─ Week 22-24: Invoicing (PRD-013)                             │
│  └─ Week 25-26: Payment preferences (PRD-014)                   │
│                                                                 │
│  PHASE 5: MONETIZATION (Weeks 27-32)                            │
│  ├─ Week 27-28: Marketing site (PRD-015)                        │
│  ├─ Week 29-30: Subscription management (PRD-016)               │
│  └─ Week 31-32: Usage limits (PRD-017)                          │
│                                                                 │
│  PHASE 6: POLISH & LAUNCH (Weeks 33-38)                         │
│  ├─ Week 33-34: Reporting (PRD-018), Settings (PRD-019)         │
│  ├─ Week 35: Onboarding (PRD-020)                               │
│  ├─ Week 36: Beta testing (closed)                              │
│  ├─ Week 37: Beta testing (open) + bug fixes                    │
│  └─ Week 38: Play Store launch (PRD-021)                        │
│                                                                 │
│  POST-LAUNCH                                                    │
│  ├─ Months 9-10: iOS app (Swift)                                │
│  ├─ Month 11: Client portal MVP                                 │
│  ├─ Month 12: QuickBooks integration                            │
│  └─ Month 12+: Advanced features, evaluate international        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

Total Android MVP: ~38 weeks (~9 months)
iOS port: +8-10 weeks
International expansion: Evaluate after US PMF achieved (Year 2+)
```

---

## 9. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Offline sync edge cases cause data loss | Medium | Critical | Extensive testing, sync logging, rollback capability |
| Google Routes API cost overruns | Medium | High | Tier limits, caching, consider GraphHopper fallback |
| Calendar sync conflicts confuse users | Medium | Medium | Clear UX for conflicts, "Hoof Direct is source of truth" |
| Native development slower than cross-platform | Medium | Medium | Focus on MVP features, defer nice-to-haves |
| Low Play Store discoverability | High | Medium | Focus on farrier-specific channels, not organic search |
| Stripe regulatory changes (Google Play) | Low | High | Monitor, have Google Play Billing as fallback |
| Competition copies route optimization | Medium | Low | First-mover advantage, build brand loyalty |
| GPS battery drain complaints | Medium | Medium | Optimize location tracking, clear battery usage info |

---

## 10. Open Questions

1. **App name confirmation**: "Hoof Direct" — ✅ Domain available (hoofdirect.com), trademark search pending
2. **Pricing validation**: $29/79/149 tiers — need user research to confirm willingness to pay
3. **iOS timeline**: Parallel development with KMM, or sequential Swift rewrite?
4. **Spanish language**: Support for US Southwest market (large Hispanic farrier population) — Phase 1 or defer?
5. **Web app**: Should farriers be able to access on desktop? (Best Farrier App doesn't offer this)
6. **Client self-booking**: Phase 2 priority? How complex is the portal?
7. **Vet collaboration**: Is this a real need or nice-to-have? Needs user research.
8. **State-specific requirements**: Any states with specific licensing/record-keeping requirements?

**Deferred (International Expansion)**:
- Multi-currency support
- GDPR compliance (EU)
- International SMS rates
- UK/AU/CA market entry timing
- Localized terminology

---

## 11. Key Decisions Log

*Decisions made during planning that inform all PRDs:*

### Business & Legal

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Business entity | Separate LLC | Clean separation from Ariel Digital Marketing |
| Domain | hoofdirect.com | Available, matches app name |
| Terms & Privacy | Template/AI-assisted | Cost-effective for MVP; legal review before scale |

### Technical Architecture

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Photo storage | Supabase Storage with free tier limits | Cost-effective; 50 photos on free tier, unlimited on paid |
| SMS sender ID | Branded "Hoof Direct" | $3/month, increases trust and open rates |
| Invoice PDF | Client-side generation | Works offline; user can generate/send when back online |
| Multi-device login | Allowed on all tiers | Better than Best Farrier App (single device); no artificial restriction |
| Data export format | JSON | Easiest to implement; complete data structure |

### Monetization

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Free trial | 30 days | Farrier shoeing cycle is 6-8 weeks; need time to see value |
| Refund policy | Full month refund | Simple, builds trust, reduces support burden |
| SMS overage | Hard stop at limit | Until demand validated; revisit based on user feedback |
| Seat-based pricing | Users included per tier | Solo=1, Growing=2, Multi=5; clear upgrade path |

### User Experience

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Competitor import | Manual only (v1) | Low priority; focus on core value |
| Default reminder | 24 hours before | Industry standard; configurable per client if user wants |
| Reminder configurability | Per-client override available | Power users can customize |

### Operations

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Support channel | Email only | Manageable for small team; upgrade path to chat later |
| Feedback collection | Canny (free tier) | Public roadmap, vote on features, free to start |
| Account deletion | 30-day soft delete → hard delete | CCPA compliant; allows recovery from mistakes |

### Go-to-Market

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Target launch date | Flexible (no event tie-in) | Quality over speed |
| Beta recruitment | Owner's farrier contacts | 2+ farriers available for initial testing |

---

## 12. Success Criteria by Phase

| Phase | Milestone | Success Criteria |
|-------|-----------|------------------|
| Phase 1 | Internal alpha | CRUD operations work offline, data syncs reliably |
| Phase 2 | Closed beta | 20 farriers using daily, <5 bugs/week reported |
| Phase 3 | Route beta | Users report time savings, route optimization works for 8+ stops |
| Phase 4 | Invoice beta | 80%+ of users generating invoices, payments tracked |
| Phase 5 | Paid launch | 10%+ free→paid conversion, Stripe integration stable |
| Phase 6 | Public launch | 4.5+ Play Store rating, <1% crash rate, 500 downloads week 1 |

---

## 13. Appendix: Competitive Feature Matrix

| Feature | Hoof Direct | The Farrier's App | Best Farrier | EQUINET |
|---------|-------------|-------------------|--------------|---------|
| **Price** | $29-149/mo | $59/mo | $31/mo | Free |
| **Route optimization** | ✅ Core feature | ❌ | ❌ | ❌ |
| **Calendar sync** | ✅ Two-way | ❌ | ⚠️ Export | ❌ |
| **Offline mode** | ✅ Native | ⚠️ Glitchy | ✅ | ✅ |
| **Mileage tracking** | ✅ | ❌ | ✅ | ❌ |
| **SMS reminders** | ✅ Included | ⚠️ Extra? | ⚠️ Limited | ✅ |
| **Invoicing** | ✅ | ✅ | ✅ | ✅ |
| **Multi-user** | ✅ $79+ tier | $25/user | ❌ | ✅ Free |
| **QuickBooks/Xero** | 🔜 Phase 2 | ❌ | ❌ | ✅ Xero |
| **Client portal** | 🔜 Phase 2 | ❌ | ❌ | ❌ |
| **Platform** | Android (iOS coming) | iOS, Android, Web | iOS, Android | iOS, Android, Web |
| **Play Store rating** | 🎯 4.7+ | 3.1/5 | 4.6/5 | ~4.0/5 |

---

*Document version: 2.0*
*Last updated: January 2026*
*Author: Sal / Ariel Digital Marketing*
