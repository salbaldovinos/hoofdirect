# FRD-014: Payment Preferences

**Version:** 1.0  
**Last Updated:** January 2026  
**PRD Reference:** PRD-014-payment-preferences.md  
**Priority:** P1  
**Phase:** 4 - Financial Tools

---

## 1. Overview

### 1.1 Purpose

Configure accepted payment methods that display on invoices, enabling clients to see clear payment instructions and reducing friction in the payment collection process.

### 1.2 Scope

This FRD covers:
- Payment method configuration screen
- Individual payment method setup (Venmo, Cash App, Zelle, Square, Check, Cash)
- Preferred method designation
- Invoice integration for payment display
- Future QR code support

### 1.3 Dependencies

| Dependency | Type | FRD Reference |
|------------|------|---------------|
| User Profile | Internal | FRD-001 |
| Invoicing | Internal | FRD-013 |

---

## 2. Payment Methods Configuration

### 2.1 Supported Payment Methods

The system supports six payment method types:

| Method | Configuration Field | Validation | Example |
|--------|-------------------|------------|---------|
| Venmo | Handle (username) | `@` prefix, 5-30 chars alphanumeric/underscore | @SmithFarrier |
| Cash App | Cashtag | `$` prefix, 1-20 chars alphanumeric | $SmithFarrier |
| Zelle | Email or Phone | Valid email or 10-digit US phone | john@email.com or 5125551234 |
| Square | Payment Link | Valid URL starting with https://squareup.com or https://square.link | https://square.link/u/abc123 |
| Check | Payable To | 1-100 chars, business/personal name | Smith Farrier Services LLC |
| Cash | (none) | Boolean toggle only | true/false |

### 2.2 Data Model

```kotlin
// Location: core/domain/model/PaymentPreferences.kt
data class PaymentPreferences(
    val venmo: VenmoConfig?,
    val cashApp: CashAppConfig?,
    val zelle: ZelleConfig?,
    val square: SquareConfig?,
    val checkPayableTo: String?,
    val cashAccepted: Boolean,
    val preferredMethod: PaymentMethodType?
)

data class VenmoConfig(
    val handle: String,  // Stored without @ prefix, displayed with @
    val enabled: Boolean
)

data class CashAppConfig(
    val cashtag: String,  // Stored without $ prefix, displayed with $
    val enabled: Boolean
)

data class ZelleConfig(
    val identifier: String,  // Email or phone
    val identifierType: ZelleIdentifierType,  // EMAIL or PHONE
    val enabled: Boolean
)

enum class ZelleIdentifierType { EMAIL, PHONE }

data class SquareConfig(
    val paymentLink: String,
    val enabled: Boolean
)

enum class PaymentMethodType {
    VENMO, CASH_APP, ZELLE, SQUARE, CHECK, CASH
}
```

### 2.3 Default State

New users start with all payment methods disabled and no preferred method set:

```kotlin
val defaultPaymentPreferences = PaymentPreferences(
    venmo = null,
    cashApp = null,
    zelle = null,
    square = null,
    checkPayableTo = null,
    cashAccepted = false,
    preferredMethod = null
)
```

---

## 3. Payment Preferences Screen

### 3.1 Screen Location

**Route:** `/settings/payment-methods`  
**Navigation:** Settings → Payment Methods

### 3.2 Screen Layout

```
┌─────────────────────────────────────────┐
│ [←] Payment Methods                     │
├─────────────────────────────────────────┤
│                                         │
│  ┌─────────────────────────────────┐   │
│  │ Venmo                    [○─●]  │   │
│  │ @SmithFarrier                   │   │
│  │ ★ Preferred                     │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │ Cash App                 [○─●]  │   │
│  │ $SmithFarrier                   │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │ Zelle                    [●─○]  │   │
│  │ Not configured                  │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │ Square                   [●─○]  │   │
│  │ Not configured                  │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │ Check                    [○─●]  │   │
│  │ Payable to:                     │   │
│  │ Smith Farrier Services          │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │ Cash                     [○─●]  │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  Invoice Preview                        │
│  ┌─────────────────────────────────┐   │
│  │ Payment Options:                │   │
│  │ ★ Venmo: @SmithFarrier         │   │
│  │   Cash App: $SmithFarrier       │   │
│  │   Check payable to:             │   │
│  │   Smith Farrier Services        │   │
│  │   Cash accepted                 │   │
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

### 3.3 Payment Method Card Behavior

Each payment method displays as a card with these behaviors:

**Card States:**
1. **Disabled, Not Configured:** Toggle OFF, "Not configured" subtitle, tap to expand and configure
2. **Disabled, Configured:** Toggle OFF, shows saved value grayed out, tap to edit
3. **Enabled, Configured:** Toggle ON, shows saved value, tap to edit
4. **Enabled, Preferred:** Toggle ON, shows saved value, "★ Preferred" label displayed

**Interactions:**
- **Toggle:** Enables/disables the payment method for invoice display
- **Tap Card:** Opens edit sheet for that payment method
- **Long Press:** Shows "Set as Preferred" option (if enabled)

### 3.4 Payment Method Edit Sheets

Each payment method has a configuration sheet:

#### Venmo Edit Sheet
```
┌─────────────────────────────────────────┐
│ Configure Venmo                    [✕]  │
├─────────────────────────────────────────┤
│                                         │
│  Venmo Handle                           │
│  ┌─────────────────────────────────┐   │
│  │ @ │ SmithFarrier              │   │
│  └─────────────────────────────────┘   │
│  Enter your Venmo username              │
│                                         │
│  [────────────────────────────────────] │
│  [          Save Changes              ] │
│                                         │
└─────────────────────────────────────────┘
```

**Fields:**
- Handle: Text input with @ prefix display
- Validation: 5-30 characters, alphanumeric and underscores only
- Error: "Venmo handle must be 5-30 characters, letters, numbers, and underscores only"

#### Cash App Edit Sheet
```
┌─────────────────────────────────────────┐
│ Configure Cash App                 [✕]  │
├─────────────────────────────────────────┤
│                                         │
│  Cashtag                                │
│  ┌─────────────────────────────────┐   │
│  │ $ │ SmithFarrier              │   │
│  └─────────────────────────────────┘   │
│  Enter your Cash App $cashtag           │
│                                         │
│  [────────────────────────────────────] │
│  [          Save Changes              ] │
│                                         │
└─────────────────────────────────────────┘
```

**Fields:**
- Cashtag: Text input with $ prefix display
- Validation: 1-20 characters, alphanumeric only
- Error: "Cashtag must be 1-20 characters, letters and numbers only"

#### Zelle Edit Sheet
```
┌─────────────────────────────────────────┐
│ Configure Zelle                    [✕]  │
├─────────────────────────────────────────┤
│                                         │
│  Receive payments via:                  │
│  [● Email    ○ Phone Number]            │
│                                         │
│  Email Address                          │
│  ┌─────────────────────────────────┐   │
│  │ john@smithfarrier.com         │   │
│  └─────────────────────────────────┘   │
│  Enter the email linked to your Zelle   │
│                                         │
│  [────────────────────────────────────] │
│  [          Save Changes              ] │
│                                         │
└─────────────────────────────────────────┘
```

**Fields:**
- Type selector: Email or Phone radio buttons
- Email: Valid email format, max 254 chars
- Phone: 10-digit US phone number, formatted as (XXX) XXX-XXXX
- Error (email): "Please enter a valid email address"
- Error (phone): "Please enter a 10-digit phone number"

#### Square Edit Sheet
```
┌─────────────────────────────────────────┐
│ Configure Square                   [✕]  │
├─────────────────────────────────────────┤
│                                         │
│  Payment Link                           │
│  ┌─────────────────────────────────┐   │
│  │ https://square.link/u/abc123  │   │
│  └─────────────────────────────────┘   │
│  Paste your Square payment link         │
│                                         │
│  ⓘ Create a link at squareup.com/us/en │
│                                         │
│  [────────────────────────────────────] │
│  [          Save Changes              ] │
│                                         │
└─────────────────────────────────────────┘
```

**Fields:**
- Payment Link: URL input
- Validation: Must start with https://squareup.com or https://square.link
- Error: "Please enter a valid Square payment link"

#### Check Edit Sheet
```
┌─────────────────────────────────────────┐
│ Configure Check                    [✕]  │
├─────────────────────────────────────────┤
│                                         │
│  Make Checks Payable To                 │
│  ┌─────────────────────────────────┐   │
│  │ Smith Farrier Services LLC    │   │
│  └─────────────────────────────────┘   │
│  Your business or personal name         │
│                                         │
│  [────────────────────────────────────] │
│  [          Save Changes              ] │
│                                         │
└─────────────────────────────────────────┘
```

**Fields:**
- Payable To: Text input, 1-100 characters
- Pre-filled with business name from profile if available
- Error: "Please enter a name (up to 100 characters)"

#### Cash Toggle

Cash has no configuration sheet—it's a simple toggle. When enabled, "Cash accepted" appears in payment options.

---

## 4. Preferred Method

### 4.1 Setting Preferred Method

**Behavior:**
1. Only enabled payment methods can be set as preferred
2. Only one method can be preferred at a time
3. Setting a new preferred method clears the previous one
4. Disabling a preferred method clears the preference

**Interaction Options:**
- Long press on enabled method card → "Set as Preferred" menu item
- Or: Tap card → Edit sheet has "Set as preferred" checkbox

### 4.2 Preferred Method Display

On the preferences screen:
- Preferred method card shows "★ Preferred" label with accent color
- Cards are sorted: Preferred first, then enabled methods, then disabled

On invoices:
- Preferred method listed first with ★ indicator
- Other enabled methods listed below without indicator

---

## 5. Invoice Integration

### 5.1 Payment Options Display

The invoice PDF and invoice detail screen display payment options based on preferences:

```kotlin
// Location: core/invoice/PaymentOptionsFormatter.kt
class PaymentOptionsFormatter {
    
    fun formatForInvoice(preferences: PaymentPreferences): List<PaymentOptionDisplay> {
        val options = mutableListOf<PaymentOptionDisplay>()
        
        // Add enabled methods in order, preferred first
        preferences.venmo?.takeIf { it.enabled }?.let {
            options.add(PaymentOptionDisplay(
                method = PaymentMethodType.VENMO,
                label = "Venmo",
                value = "@${it.handle}",
                isPreferred = preferences.preferredMethod == PaymentMethodType.VENMO
            ))
        }
        
        preferences.cashApp?.takeIf { it.enabled }?.let {
            options.add(PaymentOptionDisplay(
                method = PaymentMethodType.CASH_APP,
                label = "Cash App",
                value = "$${it.cashtag}",
                isPreferred = preferences.preferredMethod == PaymentMethodType.CASH_APP
            ))
        }
        
        preferences.zelle?.takeIf { it.enabled }?.let {
            options.add(PaymentOptionDisplay(
                method = PaymentMethodType.ZELLE,
                label = "Zelle",
                value = it.identifier,
                isPreferred = preferences.preferredMethod == PaymentMethodType.ZELLE
            ))
        }
        
        preferences.square?.takeIf { it.enabled }?.let {
            options.add(PaymentOptionDisplay(
                method = PaymentMethodType.SQUARE,
                label = "Pay Online",
                value = it.paymentLink,
                isPreferred = preferences.preferredMethod == PaymentMethodType.SQUARE
            ))
        }
        
        preferences.checkPayableTo?.takeIf { it.isNotBlank() }?.let {
            options.add(PaymentOptionDisplay(
                method = PaymentMethodType.CHECK,
                label = "Check payable to",
                value = it,
                isPreferred = preferences.preferredMethod == PaymentMethodType.CHECK
            ))
        }
        
        if (preferences.cashAccepted) {
            options.add(PaymentOptionDisplay(
                method = PaymentMethodType.CASH,
                label = "Cash",
                value = "accepted",
                isPreferred = preferences.preferredMethod == PaymentMethodType.CASH
            ))
        }
        
        // Sort: preferred first, then maintain order
        return options.sortedByDescending { it.isPreferred }
    }
}

data class PaymentOptionDisplay(
    val method: PaymentMethodType,
    val label: String,
    val value: String,
    val isPreferred: Boolean
)
```

### 5.2 Invoice PDF Layout

Payment options appear in the invoice PDF footer section:

```
┌─────────────────────────────────────────┐
│ Payment Options                         │
├─────────────────────────────────────────┤
│ ★ Venmo: @SmithFarrier                  │
│   Cash App: $SmithFarrier               │
│   Check payable to: Smith Farrier LLC   │
│   Cash accepted                         │
└─────────────────────────────────────────┘
```

**Formatting Rules:**
- "Payment Options" header in bold
- Preferred method first with ★ indicator
- Each method on separate line
- Methods without meaningful display values (Cash) show only label
- URLs (Square) are clickable links in digital PDFs

### 5.3 No Payment Methods Configured

If no payment methods are enabled:
- Invoice PDF shows: "Contact for payment arrangements"
- Invoice preview in preferences shows: "No payment methods enabled"

---

## 6. Data Persistence

### 6.1 Storage

Payment preferences are stored as part of the user profile in the users table:

```kotlin
// Location: core/database/entity/UserEntity.kt
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val businessName: String?,
    // ... other fields
    
    // Payment preferences stored as JSON
    @ColumnInfo(name = "payment_preferences_json")
    val paymentPreferencesJson: String?,
    
    // Sync tracking
    val syncStatus: String,
    val updatedAt: Long
)
```

### 6.2 JSON Serialization

```kotlin
// Location: core/database/converter/PaymentPreferencesConverter.kt
class PaymentPreferencesConverter {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    @TypeConverter
    fun fromPaymentPreferences(preferences: PaymentPreferences?): String? {
        return preferences?.let { json.encodeToString(it) }
    }
    
    @TypeConverter
    fun toPaymentPreferences(value: String?): PaymentPreferences? {
        return value?.let { json.decodeFromString(it) }
    }
}
```

### 6.3 Sync Behavior

Payment preferences sync with the user profile:
- Changes mark user profile as PENDING_UPDATE
- Sync includes payment_preferences_json field
- Server stores preferences as JSONB column

---

## 7. Validation Rules

### 7.1 Field Validation

| Field | Rule | Error Message |
|-------|------|---------------|
| Venmo Handle | 5-30 chars, `[a-zA-Z0-9_]+` | "Venmo handle must be 5-30 characters, letters, numbers, and underscores only" |
| Cash App Cashtag | 1-20 chars, `[a-zA-Z0-9]+` | "Cashtag must be 1-20 characters, letters and numbers only" |
| Zelle Email | Valid email format | "Please enter a valid email address" |
| Zelle Phone | 10 digits | "Please enter a 10-digit phone number" |
| Square Link | URL starting with https://squareup.com or https://square.link | "Please enter a valid Square payment link" |
| Check Payable To | 1-100 chars | "Please enter a name (up to 100 characters)" |

### 7.2 Validation Implementation

```kotlin
// Location: feature/settings/validation/PaymentPreferencesValidator.kt
class PaymentPreferencesValidator {
    
    fun validateVenmoHandle(handle: String): ValidationResult {
        val trimmed = handle.removePrefix("@").trim()
        return when {
            trimmed.length < 5 -> ValidationResult.Invalid("Venmo handle must be at least 5 characters")
            trimmed.length > 30 -> ValidationResult.Invalid("Venmo handle must be 30 characters or less")
            !trimmed.matches(Regex("^[a-zA-Z0-9_]+$")) -> 
                ValidationResult.Invalid("Venmo handle can only contain letters, numbers, and underscores")
            else -> ValidationResult.Valid
        }
    }
    
    fun validateCashAppTag(tag: String): ValidationResult {
        val trimmed = tag.removePrefix("$").trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.Invalid("Cashtag is required")
            trimmed.length > 20 -> ValidationResult.Invalid("Cashtag must be 20 characters or less")
            !trimmed.matches(Regex("^[a-zA-Z0-9]+$")) -> 
                ValidationResult.Invalid("Cashtag can only contain letters and numbers")
            else -> ValidationResult.Valid
        }
    }
    
    fun validateZelleEmail(email: String): ValidationResult {
        return if (Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Please enter a valid email address")
        }
    }
    
    fun validateZellePhone(phone: String): ValidationResult {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.length != 10 -> ValidationResult.Invalid("Please enter a 10-digit phone number")
            else -> ValidationResult.Valid
        }
    }
    
    fun validateSquareLink(url: String): ValidationResult {
        val trimmed = url.trim().lowercase()
        return when {
            !trimmed.startsWith("https://squareup.com") && 
            !trimmed.startsWith("https://square.link") -> 
                ValidationResult.Invalid("Please enter a valid Square payment link")
            else -> ValidationResult.Valid
        }
    }
    
    fun validateCheckPayableTo(name: String): ValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.Invalid("Please enter a name")
            trimmed.length > 100 -> ValidationResult.Invalid("Name must be 100 characters or less")
            else -> ValidationResult.Valid
        }
    }
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
}
```

---

## 8. ViewModel Implementation

### 8.1 State Management

```kotlin
// Location: feature/settings/ui/PaymentPreferencesViewModel.kt
@HiltViewModel
class PaymentPreferencesViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val validator: PaymentPreferencesValidator
) : ViewModel() {
    
    data class UiState(
        val isLoading: Boolean = false,
        val preferences: PaymentPreferences = PaymentPreferences(
            venmo = null,
            cashApp = null,
            zelle = null,
            square = null,
            checkPayableTo = null,
            cashAccepted = false,
            preferredMethod = null
        ),
        val editingMethod: PaymentMethodType? = null,
        val validationError: String? = null,
        val saveSuccess: Boolean = false
    )
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    init {
        loadPreferences()
    }
    
    private fun loadPreferences() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            userRepository.getCurrentUser()
                .map { it?.paymentPreferences }
                .collect { prefs ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            preferences = prefs ?: PaymentPreferences(
                                venmo = null,
                                cashApp = null,
                                zelle = null,
                                square = null,
                                checkPayableTo = null,
                                cashAccepted = false,
                                preferredMethod = null
                            )
                        )
                    }
                }
        }
    }
    
    fun toggleMethod(method: PaymentMethodType, enabled: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.preferences
            val updated = when (method) {
                PaymentMethodType.VENMO -> current.copy(
                    venmo = current.venmo?.copy(enabled = enabled)
                )
                PaymentMethodType.CASH_APP -> current.copy(
                    cashApp = current.cashApp?.copy(enabled = enabled)
                )
                PaymentMethodType.ZELLE -> current.copy(
                    zelle = current.zelle?.copy(enabled = enabled)
                )
                PaymentMethodType.SQUARE -> current.copy(
                    square = current.square?.copy(enabled = enabled)
                )
                PaymentMethodType.CASH -> current.copy(cashAccepted = enabled)
                PaymentMethodType.CHECK -> current // Check enabled determined by payableTo presence
            }
            
            // Clear preferred if disabling preferred method
            val finalUpdated = if (!enabled && current.preferredMethod == method) {
                updated.copy(preferredMethod = null)
            } else {
                updated
            }
            
            savePreferences(finalUpdated)
        }
    }
    
    fun setPreferredMethod(method: PaymentMethodType?) {
        viewModelScope.launch {
            val updated = _uiState.value.preferences.copy(preferredMethod = method)
            savePreferences(updated)
        }
    }
    
    fun saveVenmo(handle: String) {
        val result = validator.validateVenmoHandle(handle)
        if (result is ValidationResult.Invalid) {
            _uiState.update { it.copy(validationError = result.message) }
            return
        }
        
        viewModelScope.launch {
            val cleanHandle = handle.removePrefix("@").trim()
            val updated = _uiState.value.preferences.copy(
                venmo = VenmoConfig(handle = cleanHandle, enabled = true)
            )
            savePreferences(updated)
            _uiState.update { it.copy(editingMethod = null) }
        }
    }
    
    // Similar save methods for other payment types...
    
    private suspend fun savePreferences(preferences: PaymentPreferences) {
        userRepository.updatePaymentPreferences(preferences)
        _uiState.update { 
            it.copy(
                preferences = preferences, 
                saveSuccess = true,
                validationError = null
            ) 
        }
    }
    
    fun openEditSheet(method: PaymentMethodType) {
        _uiState.update { it.copy(editingMethod = method, validationError = null) }
    }
    
    fun closeEditSheet() {
        _uiState.update { it.copy(editingMethod = null, validationError = null) }
    }
    
    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}
```

---

## 9. Invoice Preview Component

### 9.1 Live Preview Widget

The payment preferences screen includes a live preview of how payment options will appear on invoices:

```kotlin
// Location: feature/settings/ui/components/PaymentOptionsPreview.kt
@Composable
fun PaymentOptionsPreview(
    preferences: PaymentPreferences,
    modifier: Modifier = Modifier
) {
    val formatter = remember { PaymentOptionsFormatter() }
    val options = formatter.formatForInvoice(preferences)
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Invoice Preview",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Payment Options:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    if (options.isEmpty()) {
                        Text(
                            text = "No payment methods enabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        )
                    } else {
                        options.forEach { option ->
                            PaymentOptionRow(option)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentOptionRow(option: PaymentOptionDisplay) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        if (option.isPreferred) {
            Text(
                text = "★ ",
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Spacer(modifier = Modifier.width(20.dp))
        }
        
        Text(
            text = "${option.label}: ${option.value}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
```

---

## 10. Future: QR Code Support

### 10.1 Planned Feature

Future versions will support QR codes for payment methods that support them:

| Method | QR Code Type | Contains |
|--------|--------------|----------|
| Venmo | Deep link | venmo://paycharge?txn=pay&recipients=@handle |
| Cash App | Deep link | cashapp://pay/$cashtag |
| Square | Payment link | https://square.link/u/abc123 |

### 10.2 Implementation Notes

- QR codes generated using ZXing library
- Displayed on invoice PDF in payment section
- Size: 100x100 points
- Error correction: Level M
- Feature flag: `feature_payment_qr_codes` (default: false)

---

## 11. File Reference Summary

### 11.1 New Files to Create

| File Path | Purpose |
|-----------|---------|
| `feature/settings/ui/PaymentPreferencesScreen.kt` | Main preferences screen |
| `feature/settings/ui/PaymentPreferencesViewModel.kt` | State management |
| `feature/settings/ui/components/PaymentMethodCard.kt` | Individual method card |
| `feature/settings/ui/components/PaymentOptionsPreview.kt` | Invoice preview widget |
| `feature/settings/ui/components/VenmoEditSheet.kt` | Venmo configuration sheet |
| `feature/settings/ui/components/CashAppEditSheet.kt` | Cash App configuration sheet |
| `feature/settings/ui/components/ZelleEditSheet.kt` | Zelle configuration sheet |
| `feature/settings/ui/components/SquareEditSheet.kt` | Square configuration sheet |
| `feature/settings/ui/components/CheckEditSheet.kt` | Check configuration sheet |
| `feature/settings/validation/PaymentPreferencesValidator.kt` | Input validation |
| `core/domain/model/PaymentPreferences.kt` | Domain model |
| `core/domain/model/PaymentMethodType.kt` | Enum for method types |
| `core/invoice/PaymentOptionsFormatter.kt` | Format options for invoice display |
| `core/database/converter/PaymentPreferencesConverter.kt` | JSON type converter |

### 11.2 Files to Modify

| File Path | Changes |
|-----------|---------|
| `core/database/entity/UserEntity.kt` | Add `payment_preferences_json` column |
| `core/domain/model/User.kt` | Add `paymentPreferences` field |
| `core/database/HoofDirectDatabase.kt` | Register type converter |
| `feature/invoice/InvoicePdfGenerator.kt` | Add payment options section |
| `feature/settings/navigation/SettingsNavigation.kt` | Add payment preferences route |

---

## 12. Acceptance Criteria

### Configuration

| ID | Scenario | Given | When | Then | Verification |
|----|----------|-------|------|------|--------------|
| AC-014-01 | Save Venmo handle | User on Payment Methods screen | User enters "@SmithFarrier" and saves | Handle saved as "SmithFarrier", displayed as "@SmithFarrier" | Integration test |
| AC-014-02 | Invalid Venmo handle | User configuring Venmo | User enters "abc" (too short) | Error "Venmo handle must be at least 5 characters" | Unit test |
| AC-014-03 | Save Cash App cashtag | User on Payment Methods screen | User enters "$FarrierServices" and saves | Cashtag saved as "FarrierServices", displayed as "$FarrierServices" | Integration test |
| AC-014-04 | Invalid cashtag characters | User configuring Cash App | User enters "smith-farrier" | Error "Cashtag can only contain letters and numbers" | Unit test |
| AC-014-05 | Save Zelle email | User on Payment Methods screen | User enters "john@smith.com" and saves | Email saved and displayed | Integration test |
| AC-014-06 | Save Zelle phone | User on Payment Methods screen | User selects Phone, enters "5125551234" | Phone saved, displayed as "(512) 555-1234" | Integration test |
| AC-014-07 | Invalid Zelle phone | User configuring Zelle | User enters "123456" (too short) | Error "Please enter a 10-digit phone number" | Unit test |
| AC-014-08 | Save Square link | User on Payment Methods screen | User enters "https://square.link/u/abc123" | Link saved and displayed | Integration test |
| AC-014-09 | Invalid Square link | User configuring Square | User enters "http://paypal.com/abc" | Error "Please enter a valid Square payment link" | Unit test |
| AC-014-10 | Save check payable to | User on Payment Methods screen | User enters "Smith Farrier LLC" | Name saved, check enabled | Integration test |
| AC-014-11 | Enable cash accepted | User on Payment Methods screen | User toggles Cash ON | Cash accepted flag set to true | Integration test |

### Preferred Method

| ID | Scenario | Given | When | Then | Verification |
|----|----------|-------|------|------|--------------|
| AC-014-12 | Set preferred method | Venmo enabled | User long presses Venmo, selects "Set as Preferred" | Venmo shows "★ Preferred", other methods cleared | UI test |
| AC-014-13 | Change preferred method | Venmo is preferred | User sets Cash App as preferred | Cash App shows "★ Preferred", Venmo no longer preferred | UI test |
| AC-014-14 | Disable preferred method | Venmo is preferred | User toggles Venmo OFF | Preferred method cleared, Venmo shows "Not configured" | UI test |
| AC-014-15 | Cannot prefer disabled | Cash App disabled | User tries to set Cash App as preferred | Action not available | UI test |

### Invoice Display

| ID | Scenario | Given | When | Then | Verification |
|----|----------|-------|------|------|--------------|
| AC-014-16 | Invoice shows enabled methods | Venmo ($SmithFarrier), Check (Smith LLC) enabled, Zelle disabled | Invoice generated | Invoice shows Venmo and Check, no Zelle | PDF verification |
| AC-014-17 | Preferred highlighted | Venmo preferred, Cash App also enabled | Invoice generated | Invoice shows "★ Venmo: @SmithFarrier" first | PDF verification |
| AC-014-18 | No methods enabled | All payment methods disabled | Invoice generated | Invoice shows "Contact for payment arrangements" | PDF verification |
| AC-014-19 | Square link clickable | Square enabled with link | Invoice PDF viewed | Square link is tappable/clickable | Manual test |

### Preview Widget

| ID | Scenario | Given | When | Then | Verification |
|----|----------|-------|------|------|--------------|
| AC-014-20 | Preview updates live | User viewing preferences | User enables Cash App | Preview immediately shows Cash App option | UI test |
| AC-014-21 | Preview shows preferred first | Venmo, Cash App enabled, Cash App preferred | User viewing preview | Preview shows Cash App with ★ first, then Venmo | UI test |
| AC-014-22 | Empty preview state | No methods enabled | User viewing preview | Preview shows "No payment methods enabled" | UI test |

### Persistence

| ID | Scenario | Given | When | Then | Verification |
|----|----------|-------|------|------|--------------|
| AC-014-23 | Preferences persist | User configures Venmo and Cash App | App closed and reopened | Both methods still configured | Integration test |
| AC-014-24 | Preferences sync | User configures methods offline | App goes online | Preferences synced to server | Sync test |
| AC-014-25 | Preferences load from server | User logs in on new device | App opens | Previously saved preferences loaded | Integration test |

---

## 13. Performance Requirements

| Metric | Target | Measurement |
|--------|--------|-------------|
| Screen load time | < 200ms | Time from navigation to content visible |
| Toggle response | < 100ms | Time from tap to visual feedback |
| Save operation | < 500ms | Time from save tap to confirmation |
| Preview update | < 50ms | Time from change to preview update |
| Invoice render with payments | +50ms max | Additional time for payment section |

---

## 14. Error Handling

### 14.1 Validation Errors

Display inline below the field with red color:
- Field highlighted with error border
- Error message appears below field
- Save button disabled until error resolved

### 14.2 Save Errors

If save fails (network/database):
- Show snackbar: "Failed to save. Changes will sync when online."
- Persist locally with PENDING_UPDATE status
- Auto-retry on connectivity

### 14.3 Load Errors

If preferences fail to load:
- Show error state: "Unable to load payment settings"
- Retry button
- Continue showing cached data if available

---

## 15. Accessibility

| Requirement | Implementation |
|-------------|----------------|
| Toggle labels | Each toggle has contentDescription: "{Method} payment {enabled/disabled}" |
| Card navigation | Cards focusable, announce method name and status |
| Edit sheets | Focus moves to first field when opened |
| Validation errors | Announced immediately via LiveRegion |
| Preview | Labeled as "Invoice preview showing {n} payment options" |
| Star indicator | Announced as "preferred payment method" |

---

## 16. Analytics Events

| Event | Trigger | Properties |
|-------|---------|------------|
| `payment_method_enabled` | Method toggle ON | method, has_value |
| `payment_method_disabled` | Method toggle OFF | method |
| `payment_method_configured` | Save successful | method |
| `preferred_method_set` | Preferred selection | method, previous_method |
| `payment_preferences_screen_viewed` | Screen opened | configured_count, preferred_method |
