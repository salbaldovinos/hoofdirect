# Hoof Direct: Android App PRD Collection

**Version**: 1.0  
**Last Updated**: January 2026  
**Platform**: Android (Kotlin/Jetpack Compose)

---

## Overview

This document collection contains comprehensive Product Requirement Documents (PRDs) for the Hoof Direct Android application—a native mobile CRM for farriers featuring intelligent route optimization.

## Document Structure

Each PRD follows a consistent structure designed for AI-assisted development:

1. **Overview & Objectives** - Purpose, business value, success metrics
2. **User Stories** - Feature requirements from user perspective
3. **Functional Requirements** - Detailed specifications
4. **Non-Functional Requirements** - Performance, security, accessibility
5. **UI/UX Specifications** - Wireframes and interactions
6. **Technical Implementation** - Architecture, code patterns
7. **Data Model** - Database entities and relationships
8. **Security Considerations** - Threats and mitigations
9. **Testing Requirements** - Unit, integration, E2E tests
10. **AI-Assisted Development Guidelines** - Guardrails for LLM-generated code
11. **Acceptance Criteria** - Definition of done
12. **Dependencies & Out of Scope**

---

## Development Phases

### Phase 1: Foundation (Weeks 1-8)
| PRD | Title | Priority | Status |
|-----|-------|----------|--------|
| PRD-001 | Authentication & User Profiles | P0 | ✅ Complete |
| PRD-002 | Offline-First Data Architecture | P0 | ✅ Complete |
| PRD-003 | Client Management | P0 | ✅ Complete |
| PRD-004 | Horse Management | P0 | ✅ Complete |

### Phase 2: Scheduling Core (Weeks 9-14)
| PRD | Title | Priority | Status |
|-----|-------|----------|--------|
| PRD-005 | Appointment Creation & Management | P0 | ✅ Complete |
| PRD-006 | Calendar Views | P0 | ✅ Complete |
| PRD-007 | Device Calendar Sync | P1 | ✅ Complete |
| PRD-008 | Reminders & Confirmations | P1 | ✅ Complete |

### Phase 3: Route Intelligence (Weeks 15-20)
| PRD | Title | Priority | Status |
|-----|-------|----------|--------|
| PRD-009 | Maps Integration | P0 | ✅ Complete |
| PRD-010 | Route Optimization | P0 | ✅ Complete |
| PRD-011 | Mileage Tracking | P1 | ✅ Complete |

### Phase 4: Financial Tools (Weeks 21-26)
| PRD | Title | Priority | Status |
|-----|-------|----------|--------|
| PRD-012 | Service Price List | P1 | ✅ Complete |
| PRD-013 | Invoicing | P0 | ✅ Complete |
| PRD-014 | Payment Preferences | P1 | ✅ Complete |

### Phase 5: Monetization (Weeks 27-32)
| PRD | Title | Priority | Status |
|-----|-------|----------|--------|
| PRD-015 | Marketing Website | P1 | ✅ Complete |
| PRD-016 | Subscription Management | P0 | ✅ Complete |
| PRD-017 | Usage Limits Enforcement | P0 | ✅ Complete |

### Phase 6: Polish & Launch (Weeks 33-38)
| PRD | Title | Priority | Status |
|-----|-------|----------|--------|
| PRD-018 | Reporting & Analytics | P1 | ✅ Complete |
| PRD-019 | Settings & Preferences | P1 | ✅ Complete |
| PRD-020 | Onboarding Flow | P1 | ✅ Complete |
| PRD-021 | Play Store Launch | P0 | ✅ Complete |

---

## AI-Assisted Development Framework

### Security-First Code Generation

All AI-generated code MUST adhere to these principles:

1. **No Hardcoded Secrets**
   - API keys in BuildConfig or secrets manager
   - Never commit credentials to version control
   - Use environment-specific configurations

2. **Input Validation**
   - Validate all user inputs client-side AND server-side
   - Sanitize data before database operations
   - Use parameterized queries only

3. **Secure Storage**
   - EncryptedSharedPreferences for tokens
   - No PII in logs (even debug)
   - Clear sensitive data on logout

4. **Network Security**
   - HTTPS only (certificate pinning for Supabase)
   - No sensitive data in URLs
   - Proper error handling without data leaks

### Code Review Checklist for AI-Generated Code

```markdown
□ No hardcoded credentials or API keys
□ All user inputs validated
□ SQL injection prevention (Room handles this)
□ Proper exception handling with user-friendly messages
□ Sensitive data uses encrypted storage
□ Network calls use HTTPS only
□ Error messages don't expose internal details
□ Logging excludes PII
□ Authentication tokens handled securely
□ Offline functionality doesn't bypass security
```

### Testing Standards

| Test Type | Coverage Target | Responsibility |
|-----------|-----------------|----------------|
| Unit Tests | 80% business logic | AI-assisted |
| Integration Tests | All repository + DAO | AI-assisted |
| UI Tests | Critical user flows | Manual + AI |
| Security Tests | Auth, data access | Manual review |
| Performance Tests | Key metrics | Automated |

---

## Technical Stack Reference

| Layer | Technology |
|-------|------------|
| Language | Kotlin 1.9+ |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Local DB | Room |
| Networking | Retrofit + OkHttp |
| Backend | Supabase (Postgres + Auth + Storage) |
| Maps | Google Maps SDK |
| Routes | Google Routes API |
| Background | WorkManager |

---

## File Organization

```
hoof-direct-prds/
├── 00-PRD-INDEX.md (this file)
├── phase1/
│   ├── PRD-001-authentication.md
│   ├── PRD-002-offline-architecture.md
│   ├── PRD-003-client-management.md
│   └── PRD-004-horse-management.md
├── phase2/
│   ├── PRD-005-appointments.md
│   ├── PRD-006-calendar-views.md
│   ├── PRD-007-calendar-sync.md
│   └── PRD-008-reminders.md
├── phase3/
│   ├── PRD-009-maps.md
│   ├── PRD-010-route-optimization.md
│   └── PRD-011-mileage-tracking.md
├── phase4/
│   ├── PRD-012-service-prices.md
│   ├── PRD-013-invoicing.md
│   └── PRD-014-payment-preferences.md
├── phase5/
│   ├── PRD-015-marketing-site.md
│   ├── PRD-016-subscriptions.md
│   └── PRD-017-usage-limits.md
└── phase6/
    ├── PRD-018-reporting.md
    ├── PRD-019-settings.md
    ├── PRD-020-onboarding.md
    └── PRD-021-launch.md
```

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Jan 2026 | Sal | Initial PRD collection |
