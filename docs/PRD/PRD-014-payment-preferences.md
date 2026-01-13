# PRD-014: Payment Preferences

**Priority**: P1  
**Phase**: 4 - Financial Tools  
**Estimated Duration**: 3 days

---

## Overview

### Purpose
Configure accepted payment methods that display on invoices, making it easy for clients to pay.

### Business Value
- Clear payment instructions reduce friction
- Supports common payment apps (Venmo, Cash App, Zelle)
- Professional invoice appearance
- Future: QR codes for quick payment

### Success Metrics
| Metric | Target |
|--------|--------|
| Setup completion | > 90% |
| Invoice payment rate | Improve by 10% |

---

## Functional Requirements

### FR-014-01: Supported Payment Methods
```kotlin
data class PaymentPreferences(
    val venmo: VenmoConfig?,        // @username
    val cashApp: CashAppConfig?,    // $cashtag
    val zelle: ZelleConfig?,        // email or phone
    val square: SquareConfig?,      // payment link
    val checkPayableTo: String?,    // business name
    val cashAccepted: Boolean,
    val preferredMethod: String?
)

data class VenmoConfig(val handle: String, val enabled: Boolean)
data class CashAppConfig(val cashtag: String, val enabled: Boolean)
data class ZelleConfig(val emailOrPhone: String, val enabled: Boolean)
data class SquareConfig(val link: String, val enabled: Boolean)
```

### FR-014-02: Invoice Display
- Show enabled methods only
- Preferred method highlighted
- QR code for Venmo/Cash App (future)

---

## UI Specification

```
┌─────────────────────────────────────────┐
│ [←] Payment Methods                     │
├─────────────────────────────────────────┤
│                                         │
│  ☑ Venmo                   ⭐ Preferred │
│    Handle: @SmithFarrier                │
│                                         │
│  ☑ Cash App                             │
│    Cashtag: $SmithFarrier               │
│                                         │
│  ☐ Zelle                                │
│    Email or Phone: ____________         │
│                                         │
│  ☐ Square                               │
│    Payment Link: ____________           │
│                                         │
│  ☑ Check                                │
│    Payable to: Smith Farrier Services   │
│                                         │
│  ☑ Cash                                 │
│                                         │
│  Preview on Invoice                     │
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐   │
│  │ Payment Options:                │   │
│  │ ⭐ Venmo: @SmithFarrier         │   │
│  │    Cash App: $SmithFarrier      │   │
│  │    Check payable to:            │   │
│  │    Smith Farrier Services       │   │
│  │    Cash accepted                │   │
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-014-01 | Preferences save correctly | Integration test |
| AC-014-02 | Invoice shows enabled methods | UI test |
| AC-014-03 | Preferred method highlighted | UI test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-001 (Auth/Profile) | Internal | Required |
