# Hoof Direct

**Native Android CRM for Professional Farriers**

Hoof Direct is a mobile-first CRM application designed specifically for professional farriers (horseshoers) in the United States. The app combines client and horse management with intelligent route optimization—a capability no competitor currently offers.

---

## Overview

### The Problem

Farriers are mobile professionals covering 35-90 mile service radiuses, yet they plan routes manually, chase clients for confirmations, and juggle disconnected tools for scheduling, invoicing, and record-keeping. Existing solutions digitized paper workflows but missed the field service innovations proven in other industries.

### The Solution

Hoof Direct brings **field service intelligence** to farriers:

- **Route Optimization** — Reorders daily stops for minimum travel time, saving 5+ hours/week
- **Two-Way Calendar Sync** — Google Calendar and iCal integration
- **Offline-First Architecture** — Works in zero-connectivity barns
- **Client Confirmations** — Reduce no-shows with RSVP tracking
- **One-Tap Invoicing** — Flexible payment preferences

### Target Market

- ~25,000 full-time professional farriers in the US
- Service 100-200 clients annually
- Manage 40-60 horses per week
- Travel 10,000+ miles/year

---

## Technical Stack

### Android App

| Layer | Technology | Purpose |
|-------|------------|---------|
| Language | Kotlin 1.9+ | Modern, null-safe, coroutines |
| UI | Jetpack Compose | Declarative UI framework |
| Design System | Material 3 | Material You with dynamic color |
| Architecture | MVVM + Clean Architecture | Testable, maintainable |
| Dependency Injection | Hilt | Compile-time safe DI |
| Local Database | Room | SQLite abstraction, offline-first |
| Networking | Retrofit + OkHttp | REST API calls |
| Async | Kotlin Coroutines + Flow | Reactive data streams |
| Maps | Google Maps SDK | Map display and interaction |
| Routes | Google Routes API | Route optimization |
| Background Jobs | WorkManager | Reliable background sync |

### Backend Services

| Service | Technology | Purpose |
|---------|------------|---------|
| Database | Supabase (PostgreSQL) | Primary data storage |
| Authentication | Supabase Auth (GoTrue) | User authentication |
| Edge Functions | Supabase Edge Functions (Deno) | Serverless logic |
| SMS | Twilio | Client notifications |
| Payments | Stripe | Subscription billing |

### Marketing Website

| Technology | Purpose |
|------------|---------|
| Next.js | React framework |
| Tailwind CSS | Styling |
| Vercel | Hosting |

---

## Project Structure

```
com.hoofdirect.app/
├── app/                           # Application module
│   ├── src/main/
│   │   ├── java/com/hoofdirect/app/
│   │   │   ├── HoofDirectApp.kt   # Application class
│   │   │   ├── MainActivity.kt    # Single activity
│   │   │   └── navigation/        # Navigation graph
│   │   └── res/                   # Resources
│   └── build.gradle.kts
├── core/                          # Core modules
│   ├── database/                  # Room database, DAOs, entities
│   ├── network/                   # Supabase client, API services
│   ├── sync/                      # Offline sync engine
│   ├── designsystem/              # Theme, components
│   └── common/                    # Utilities, extensions
├── feature/                       # Feature modules
│   ├── auth/                      # Authentication
│   ├── clients/                   # Client management
│   ├── horses/                    # Horse management
│   ├── appointments/              # Appointments
│   ├── calendar/                  # Calendar views
│   ├── routes/                    # Maps & route optimization
│   ├── mileage/                   # Mileage tracking
│   ├── invoices/                  # Invoicing
│   ├── settings/                  # Settings
│   └── onboarding/                # Onboarding
└── build.gradle.kts
```

---

## Development Phases

### Phase 1: Foundation (Weeks 1-4)
- Authentication & User Profiles
- Offline-First Data Architecture
- Client Management
- Horse Management

### Phase 2: Scheduling Core (Weeks 5-8)
- Appointment Management
- Calendar Views (Day/Week/Agenda)
- Device Calendar Sync
- Reminders & Confirmations

### Phase 3: Route Intelligence (Weeks 9-11)
- Google Maps Integration
- Route Optimization
- Mileage Tracking

### Phase 4: Financial Tools (Weeks 12-14)
- Service Price List
- Invoicing
- Payment Preferences

### Phase 5: Monetization (Weeks 15-17)
- Marketing Website
- Subscription Management
- Usage Limits Enforcement

### Phase 6: Polish & Launch (Weeks 18-20)
- Reporting & Analytics
- Settings & Preferences
- Onboarding Flow
- Play Store Launch

---

## Subscription Tiers

| Tier | Price | Route Stops/Day | SMS/Month |
|------|-------|-----------------|-----------|
| Free | $0 | View only | 0 |
| Solo Farrier | $29/mo | 8 | 50 |
| Growing Practice | $79/mo | 15 | 200 |
| Multi-Farrier | $149/mo | Unlimited | 500 |

---

## Key Features

### Route Optimization (Core Differentiator)
- Optimizes daily appointment routes using Google Routes API
- Before/after comparison shows time and mileage savings
- Lock stops at specific positions
- Manual reorder with drag-and-drop
- Start navigation directly from app

### Offline-First Architecture
- All data stored locally in Room database
- Sync queue persists pending changes
- Background sync via WorkManager
- Last-write-wins conflict resolution
- Works fully offline for all core operations

### Calendar Sync
- Two-way sync with Google Calendar
- Export to device calendar
- Appointment changes reflect immediately
- Color-coded event types

---

## Design Principles

1. **Offline-first, always** — Every core feature works without connectivity
2. **Field-ready UI** — Large touch targets, high contrast, sunlight readable
3. **Native Android excellence** — Material 3 throughout
4. **Speed over features** — Target <100ms for common interactions
5. **Progressive disclosure** — Essential info first, details on demand
6. **Respect existing workflows** — Adapt to how farriers already work
7. **Smart defaults, flexible overrides** — Optimize for 80% case

---

## Documentation

- `00-PRD-INDEX.md` — Product Requirements Document index
- `00-FRD-INDEX.md` — Functional Requirements Document index
- `PRD-XXX-*.md` — Individual product requirement documents
- `FRD-XXX-*.md` — Individual functional requirement documents
- `hoof-direct-product-plan.md` — Comprehensive product strategy
- `hoof-direct-android-plan.md` — Android technical specification

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK 34

### Environment Setup

1. Clone the repository
2. Copy `local.properties.example` to `local.properties`
3. Add required API keys:
   - `SUPABASE_URL`
   - `SUPABASE_ANON_KEY`
   - `GOOGLE_MAPS_API_KEY`
   - `GOOGLE_ROUTES_API_KEY`

### Build & Run

```bash
# Debug build
./gradlew assembleDebug

# Run tests
./gradlew test

# Run connected tests
./gradlew connectedAndroidTest
```

---

## Testing

| Test Type | Target Coverage | Location |
|-----------|-----------------|----------|
| Unit Tests | ≥80% business logic | `src/test/` |
| Integration Tests | All repositories + DAOs | `src/androidTest/` |
| UI Tests | Critical user flows | `src/androidTest/` |
| E2E Tests | Happy paths | Maestro flows |

---

## Contributing

See `AGENTS.md` for AI-assisted development guidelines and `CLAUDE.md` for Claude-specific instructions.

---

## License

Proprietary — All rights reserved.

---

## Contact

- Website: [hoofdirect.com](https://hoofdirect.com)
- Support: support@hoofdirect.com
