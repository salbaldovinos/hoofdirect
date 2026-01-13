# FRD-013: Invoicing

**PRD Reference**: PRD-013-invoicing.md  
**Priority**: P0  
**Phase**: 4 - Financial Tools  
**Estimated Duration**: 2 weeks

---

## 1. Overview

### 1.1 Purpose

This document specifies the complete functional requirements for invoicing—enabling farriers to generate, send, and track professional invoices for completed appointments with PDF generation, email delivery, and payment status tracking.

### 1.2 Scope

This FRD covers:
- Invoice generation from completed appointments
- Manual invoice creation
- Invoice numbering system
- Line item management
- PDF generation (works offline)
- Email and SMS delivery
- Invoice status workflow
- Payment tracking and recording
- Outstanding invoice management
- Invoice list with filtering and aging summary

### 1.3 Dependencies

| Dependency | FRD | Description |
|------------|-----|-------------|
| Appointments | FRD-005 | Source for invoice generation |
| Service & Pricing | FRD-012 | Service prices for line items |
| Client Management | FRD-003 | Client billing info |
| Payment Preferences | FRD-014 | Payment instructions |
| Reminders | FRD-008 | Email/SMS delivery |
| Offline Architecture | FRD-002 | Local PDF generation and sync |

---

## 2. Invoice Status Workflow

### 2.1 Status State Machine

```
┌─────────────────────────────────────────────────────────────┐
│                  INVOICE STATUS WORKFLOW                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────┐                                                │
│  │  DRAFT  │ ← Invoice created, not yet sent                │
│  └────┬────┘                                                │
│       │                                                      │
│       │ [Send]                                              │
│       ↓                                                      │
│  ┌─────────┐                                                │
│  │  SENT   │ ← Email delivered to client                    │
│  └────┬────┘                                                │
│       │                                                      │
│       │ [Client opens] (auto)                               │
│       ↓                                                      │
│  ┌─────────┐      ┌──────────┐                              │
│  │ VIEWED  │      │ OVERDUE  │ ← Past due date (auto)       │
│  └────┬────┘      └────┬─────┘                              │
│       │                │                                     │
│       │ [Mark Paid]    │ [Mark Paid]                        │
│       ↓                ↓                                     │
│  ┌─────────────────────────┐                                │
│  │         PAID            │ ← Payment received             │
│  └─────────────────────────┘                                │
│                                                              │
│  Any Status ──[Void]──► ┌─────────┐                         │
│                         │  VOID   │ ← Cancelled/deleted     │
│                         └─────────┘                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Invoice Status Enum

**File**: `core/domain/model/InvoiceStatus.kt`

```kotlin
enum class InvoiceStatus(val displayName: String, val color: StatusColor) {
    DRAFT("Draft", StatusColor.GRAY),
    SENT("Sent", StatusColor.BLUE),
    VIEWED("Viewed", StatusColor.BLUE),
    PAID("Paid", StatusColor.GREEN),
    OVERDUE("Overdue", StatusColor.RED),
    VOID("Void", StatusColor.GRAY);
    
    val isOutstanding: Boolean
        get() = this in listOf(SENT, VIEWED, OVERDUE)
    
    val canMarkPaid: Boolean
        get() = this in listOf(DRAFT, SENT, VIEWED, OVERDUE)
    
    val canSend: Boolean
        get() = this == DRAFT
    
    val canVoid: Boolean
        get() = this != VOID && this != PAID
}

enum class StatusColor {
    GRAY, BLUE, GREEN, RED
}
```

### 2.3 Status Transitions

| Current Status | Action | New Status | Side Effects |
|---------------|--------|------------|--------------|
| DRAFT | Send | SENT | sentAt = now, email sent |
| SENT | Client opens email | VIEWED | viewedAt = now |
| SENT/VIEWED | Due date passes | OVERDUE | (daily check job) |
| DRAFT/SENT/VIEWED/OVERDUE | Mark Paid | PAID | paidAt = now, paymentMethod set |
| Any (except VOID, PAID) | Void | VOID | voidedAt = now |

---

## 3. Invoice Number Generation

### 3.1 Numbering Format

**Format**: `INV-{YYYY}-{NNNN}`

- `YYYY`: 4-digit year
- `NNNN`: 4-digit sequential number, zero-padded

**Examples**: `INV-2025-0001`, `INV-2025-0042`, `INV-2025-1234`

### 3.2 Number Generator

**File**: `core/invoice/InvoiceNumberGenerator.kt`

```kotlin
class InvoiceNumberGenerator @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val userRepository: UserRepository
) {
    suspend fun generateNext(): String {
        val userId = userRepository.getCurrentUserId() 
            ?: throw IllegalStateException("User not logged in")
        
        val currentYear = LocalDate.now().year
        val yearPrefix = "INV-$currentYear-"
        
        // Get the highest number for current year
        val lastNumber = invoiceDao.getMaxInvoiceNumberForYear(userId, yearPrefix) ?: 0
        val nextNumber = lastNumber + 1
        
        return "$yearPrefix${nextNumber.toString().padStart(4, '0')}"
    }
}

// DAO query
@Query("""
    SELECT MAX(CAST(SUBSTR(invoice_number, 10) AS INTEGER))
    FROM invoices 
    WHERE user_id = :userId AND invoice_number LIKE :yearPrefix || '%'
""")
suspend fun getMaxInvoiceNumberForYear(userId: String, yearPrefix: String): Int?
```

### 3.3 Offline Number Handling

When offline:
1. Generate number using local sequence
2. On sync, server may reassign number if conflict detected
3. Original number stored as `localInvoiceNumber`, server number as `invoiceNumber`

---

## 4. Invoice Creation from Appointment

### 4.1 Create Invoice Flow

```
┌─────────────────────────────────────────────────────────────┐
│              CREATE INVOICE FROM APPOINTMENT                 │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. User completes appointment (marks COMPLETED)            │
│     ↓                                                       │
│  2. "Create Invoice" option appears in appointment detail   │
│     ↓                                                       │
│  3. User taps "Create Invoice"                              │
│     ↓                                                       │
│  4. System generates draft invoice:                         │
│     • Invoice number assigned                               │
│     • Line items from horse services                        │
│     • Client info populated                                 │
│     • Due date defaulted (from preferences)                 │
│     ↓                                                       │
│  5. Invoice editor opens for review/edit                    │
│     ↓                                                       │
│  6. User can:                                               │
│     • Add/edit/remove line items                            │
│     • Adjust prices                                         │
│     • Add notes                                             │
│     • Set due date                                          │
│     ↓                                                       │
│  7. User taps "Save" → Invoice saved as DRAFT               │
│     OR "Send" → Invoice sent to client                      │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Line Item Generation

**File**: `core/invoice/InvoiceGenerator.kt`

```kotlin
class InvoiceGenerator @Inject constructor(
    private val invoiceNumberGenerator: InvoiceNumberGenerator,
    private val invoiceDao: InvoiceDao,
    private val userPreferencesManager: UserPreferencesManager,
    private val syncManager: SyncManager
) {
    suspend fun createFromAppointment(
        appointment: AppointmentWithDetails
    ): Result<Invoice> {
        return try {
            val userId = appointment.appointment.userId
            val invoiceNumber = invoiceNumberGenerator.generateNext()
            
            // Generate line items from horse services
            val lineItems = appointment.horses.map { horseService ->
                LineItem(
                    id = UUID.randomUUID().toString(),
                    description = "${horseService.horseName} - ${horseService.serviceDisplayName}",
                    quantity = 1,
                    unitPrice = horseService.price,
                    serviceType = horseService.serviceType
                )
            }
            
            // Calculate totals
            val subtotal = lineItems.sumOf { it.total }
            val taxRate = userPreferencesManager.getTaxRate() ?: BigDecimal.ZERO
            val taxAmount = if (taxRate > BigDecimal.ZERO) {
                subtotal * taxRate / BigDecimal(100)
            } else {
                BigDecimal.ZERO
            }
            val total = subtotal + taxAmount
            
            // Determine due date
            val dueDate = calculateDueDate(userPreferencesManager.getPaymentTerms())
            
            val invoice = Invoice(
                id = UUID.randomUUID().toString(),
                userId = userId,
                clientId = appointment.client.id,
                appointmentId = appointment.appointment.id,
                invoiceNumber = invoiceNumber,
                lineItems = lineItems,
                subtotal = subtotal,
                taxRate = taxRate,
                taxAmount = taxAmount,
                total = total,
                status = InvoiceStatus.DRAFT,
                dueDate = dueDate,
                notes = null,
                createdAt = Instant.now()
            )
            
            // Save to database
            val entity = invoice.toEntity().copy(
                syncStatus = EntitySyncStatus.PENDING_CREATE
            )
            invoiceDao.insert(entity)
            
            // Queue sync
            syncManager.queueChange(
                entityType = "invoices",
                entityId = invoice.id,
                operation = SyncOperation.INSERT,
                payload = Json.encodeToString(entity)
            )
            
            Result.success(invoice)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun calculateDueDate(paymentTerms: PaymentTerms): LocalDate? {
        return when (paymentTerms) {
            PaymentTerms.UPON_RECEIPT -> null  // No specific due date
            PaymentTerms.NET_7 -> LocalDate.now().plusDays(7)
            PaymentTerms.NET_14 -> LocalDate.now().plusDays(14)
            PaymentTerms.NET_30 -> LocalDate.now().plusDays(30)
        }
    }
}
```

---

## 5. Invoice Editor Screen

### 5.1 Screen Layout

**Route**: `/invoice/edit/{invoiceId}` or `/invoice/create?appointmentId={id}`

```
┌─────────────────────────────────────────────────────────────┐
│                    INVOICE EDITOR                            │
├─────────────────────────────────────────────────────────────┤
│  [×] Invoice INV-2025-0042                        [Save]    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Status: 🔵 DRAFT                                           │
│                                                              │
│  Bill To                                                    │
│  ─────────────────────────────────────────────────────────  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Johnson Ranch                                        │  │
│  │  Sarah Johnson                                        │  │
│  │  sarah@johnsonranch.com                              │  │
│  │  123 Ranch Road, Austin, TX 78701                    │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  Invoice Date: January 13, 2025                            │
│  Due Date: Upon Receipt                               [▼]   │
│                                                              │
│  Line Items                                                 │
│  ─────────────────────────────────────────────────────────  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Midnight - Full Set                                  │  │
│  │  Qty: 1                              $180.00    [×]   │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Dusty - Trim                                         │  │
│  │  Qty: 1                               $45.00    [×]   │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Trip Fee                                             │  │
│  │  Qty: 1                               $15.00    [×]   │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  [+ Add Line Item]                                          │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                    Subtotal     $240.00     │
│                                    Tax (8.25%)   $19.80     │
│                                    ─────────────────────    │
│                                    Total        $259.80     │
│                                                              │
│  Notes (appears on invoice)                                 │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Thank you for your business!                          │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│  [Preview PDF]                                              │
│                                                              │
│  ═══════════════════════════════════════════════════════════│
│  [Save as Draft]                    [Send Invoice]          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 Line Item Editor

**Add/Edit Line Item Sheet**:

```
┌─────────────────────────────────────────────────────────────┐
│                   ADD LINE ITEM                              │
├─────────────────────────────────────────────────────────────┤
│  [×]                                          [Add]         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Description *                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Trip Fee                                              │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  Quick Add:                                                 │
│  [Trip Fee] [Supply Fee] [Emergency Fee] [Custom...]       │
│                                                              │
│  Quantity                                                   │
│  ┌─────────────────────┐                                    │
│  │ 1                   │                                    │
│  └─────────────────────┘                                    │
│                                                              │
│  Unit Price *                                               │
│  ┌─────────────────────┐                                    │
│  │ $ 15.00             │                                    │
│  └─────────────────────┘                                    │
│                                                              │
│  Total: $15.00                                              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 Due Date Options

**PaymentTerms Enum**:

```kotlin
enum class PaymentTerms(val displayName: String) {
    UPON_RECEIPT("Upon Receipt"),
    NET_7("Net 7 (7 days)"),
    NET_14("Net 14 (14 days)"),
    NET_30("Net 30 (30 days)");
}
```

**Due Date Selection**:
- Dropdown with payment term presets
- Custom date picker option
- Shows calculated date: "Due: January 20, 2025"

### 5.4 InvoiceEditorViewModel

**File**: `feature/invoice/ui/InvoiceEditorViewModel.kt`

```kotlin
@HiltViewModel
class InvoiceEditorViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val invoiceGenerator: InvoiceGenerator,
    private val pdfGenerator: InvoicePdfGenerator,
    private val emailService: InvoiceEmailService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val invoiceId: String? = savedStateHandle["invoiceId"]
    private val appointmentId: String? = savedStateHandle["appointmentId"]
    
    private val _state = MutableStateFlow(InvoiceEditorState())
    val state: StateFlow<InvoiceEditorState> = _state.asStateFlow()
    
    init {
        if (invoiceId != null) {
            loadInvoice(invoiceId)
        } else if (appointmentId != null) {
            createFromAppointment(appointmentId)
        }
    }
    
    private fun loadInvoice(id: String) {
        viewModelScope.launch {
            invoiceRepository.getInvoice(id)
                .collect { invoice ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            invoice = invoice,
                            lineItems = invoice.lineItems.toMutableList(),
                            subtotal = invoice.subtotal,
                            taxRate = invoice.taxRate,
                            taxAmount = invoice.taxAmount,
                            total = invoice.total,
                            notes = invoice.notes ?: "",
                            dueDate = invoice.dueDate
                        )
                    }
                }
        }
    }
    
    private fun createFromAppointment(appointmentId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            val result = invoiceGenerator.createFromAppointment(appointmentId)
            result.onSuccess { invoice ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        invoice = invoice,
                        lineItems = invoice.lineItems.toMutableList(),
                        subtotal = invoice.subtotal,
                        taxRate = invoice.taxRate,
                        taxAmount = invoice.taxAmount,
                        total = invoice.total,
                        dueDate = invoice.dueDate
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }
    
    fun addLineItem(item: LineItem) {
        val newItems = _state.value.lineItems.toMutableList().apply { add(item) }
        updateLineItems(newItems)
    }
    
    fun updateLineItem(index: Int, item: LineItem) {
        val newItems = _state.value.lineItems.toMutableList().apply { set(index, item) }
        updateLineItems(newItems)
    }
    
    fun removeLineItem(index: Int) {
        val newItems = _state.value.lineItems.toMutableList().apply { removeAt(index) }
        updateLineItems(newItems)
    }
    
    private fun updateLineItems(items: List<LineItem>) {
        val subtotal = items.sumOf { it.total }
        val taxAmount = subtotal * (_state.value.taxRate ?: BigDecimal.ZERO) / BigDecimal(100)
        val total = subtotal + taxAmount
        
        _state.update {
            it.copy(
                lineItems = items,
                subtotal = subtotal,
                taxAmount = taxAmount,
                total = total
            )
        }
    }
    
    fun setDueDate(date: LocalDate?) {
        _state.update { it.copy(dueDate = date) }
    }
    
    fun setNotes(notes: String) {
        _state.update { it.copy(notes = notes) }
    }
    
    fun saveAsDraft() {
        viewModelScope.launch {
            val invoice = buildInvoice(InvoiceStatus.DRAFT)
            invoiceRepository.saveInvoice(invoice)
            _state.update { it.copy(saveResult = SaveResult.Success) }
        }
    }
    
    fun sendInvoice() {
        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }
            
            val invoice = buildInvoice(InvoiceStatus.SENT)
            val saveResult = invoiceRepository.saveInvoice(invoice)
            
            if (saveResult.isSuccess) {
                val sendResult = invoiceRepository.sendInvoice(invoice.id)
                sendResult.onSuccess {
                    _state.update { 
                        it.copy(isSending = false, saveResult = SaveResult.Sent) 
                    }
                }.onFailure { error ->
                    _state.update { 
                        it.copy(isSending = false, error = "Failed to send: ${error.message}") 
                    }
                }
            }
        }
    }
    
    fun previewPdf() {
        viewModelScope.launch {
            _state.update { it.copy(isGeneratingPdf = true) }
            
            val invoice = buildInvoice(_state.value.invoice?.status ?: InvoiceStatus.DRAFT)
            val pdfFile = pdfGenerator.generate(invoice)
            
            _state.update { 
                it.copy(isGeneratingPdf = false, previewPdfFile = pdfFile) 
            }
        }
    }
    
    private fun buildInvoice(status: InvoiceStatus): Invoice {
        val currentState = _state.value
        return currentState.invoice!!.copy(
            lineItems = currentState.lineItems,
            subtotal = currentState.subtotal,
            taxAmount = currentState.taxAmount,
            total = currentState.total,
            dueDate = currentState.dueDate,
            notes = currentState.notes.takeIf { it.isNotBlank() },
            status = status
        )
    }
}

data class InvoiceEditorState(
    val isLoading: Boolean = true,
    val invoice: Invoice? = null,
    val client: Client? = null,
    val lineItems: List<LineItem> = emptyList(),
    val subtotal: BigDecimal = BigDecimal.ZERO,
    val taxRate: BigDecimal? = null,
    val taxAmount: BigDecimal = BigDecimal.ZERO,
    val total: BigDecimal = BigDecimal.ZERO,
    val dueDate: LocalDate? = null,
    val notes: String = "",
    val isSending: Boolean = false,
    val isGeneratingPdf: Boolean = false,
    val previewPdfFile: File? = null,
    val saveResult: SaveResult? = null,
    val error: String? = null
)

sealed class SaveResult {
    object Success : SaveResult()
    object Sent : SaveResult()
}
```

---

## 6. PDF Generation

### 6.1 PDF Template

**Layout Structure**:

```
┌─────────────────────────────────────────────────────────────┐
│                                                              │
│  [BUSINESS LOGO]              INVOICE                       │
│  Business Name                                              │
│  123 Business Street          Invoice #: INV-2025-0042      │
│  Austin, TX 78701             Date: January 13, 2025        │
│  (512) 555-1234               Due: Upon Receipt             │
│  email@business.com                                         │
│                                                              │
│  ───────────────────────────────────────────────────────────│
│                                                              │
│  BILL TO:                                                   │
│  Johnson Ranch                                              │
│  Sarah Johnson                                              │
│  456 Ranch Road                                             │
│  Dripping Springs, TX 78620                                 │
│  sarah@johnsonranch.com                                     │
│                                                              │
│  ───────────────────────────────────────────────────────────│
│                                                              │
│  DESCRIPTION                          QTY    PRICE    TOTAL │
│  ───────────────────────────────────────────────────────────│
│  Midnight - Full Set                    1   $180.00  $180.00│
│  Dusty - Trim                           1    $45.00   $45.00│
│  Trip Fee                               1    $15.00   $15.00│
│  ───────────────────────────────────────────────────────────│
│                                                              │
│                                         Subtotal:   $240.00 │
│                                         Tax (8.25%): $19.80 │
│                                         ════════════════════│
│                                         TOTAL:      $259.80 │
│                                                              │
│  ───────────────────────────────────────────────────────────│
│                                                              │
│  PAYMENT INSTRUCTIONS                                       │
│  ───────────────────────────────────────────────────────────│
│  Venmo: @farrier-john                                       │
│  Zelle: john@hoofcare.com                                   │
│  Check payable to: John's Farrier Service                   │
│                                                              │
│  ───────────────────────────────────────────────────────────│
│                                                              │
│  Thank you for your business!                               │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 InvoicePdfGenerator

**File**: `core/invoice/InvoicePdfGenerator.kt`

```kotlin
class InvoicePdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository
) {
    suspend fun generate(invoice: InvoiceWithDetails): File {
        val user = userRepository.getCurrentUser() 
            ?: throw IllegalStateException("User not found")
        
        val outputFile = File(
            context.cacheDir, 
            "invoice_${invoice.invoiceNumber.replace("-", "_")}.pdf"
        )
        
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(
            PAGE_WIDTH, PAGE_HEIGHT, 1
        ).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        
        var yPosition = MARGIN_TOP
        
        // Draw header
        yPosition = drawHeader(canvas, user, invoice, yPosition)
        
        // Draw client info
        yPosition = drawClientSection(canvas, invoice.client, yPosition)
        
        // Draw line items table
        yPosition = drawLineItemsTable(canvas, invoice.lineItems, yPosition)
        
        // Draw totals
        yPosition = drawTotals(canvas, invoice, yPosition)
        
        // Draw payment instructions
        yPosition = drawPaymentInstructions(canvas, user.paymentPreferences, yPosition)
        
        // Draw notes if present
        if (!invoice.notes.isNullOrBlank()) {
            yPosition = drawNotes(canvas, invoice.notes, yPosition)
        }
        
        document.finishPage(page)
        
        FileOutputStream(outputFile).use { outputStream ->
            document.writeTo(outputStream)
        }
        document.close()
        
        return outputFile
    }
    
    private fun drawHeader(
        canvas: Canvas,
        user: User,
        invoice: InvoiceWithDetails,
        startY: Float
    ): Float {
        var y = startY
        
        // Business name (large, bold)
        val titlePaint = Paint().apply {
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.BLACK
        }
        canvas.drawText(user.businessName ?: user.fullName, MARGIN_LEFT, y, titlePaint)
        
        // Invoice title (right side)
        val invoiceTitlePaint = Paint().apply {
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.parseColor("#1976D2")
        }
        canvas.drawText("INVOICE", PAGE_WIDTH - MARGIN_RIGHT - 120f, y, invoiceTitlePaint)
        y += 25f
        
        // Business address and contact
        val normalPaint = Paint().apply {
            textSize = 11f
            color = Color.DKGRAY
        }
        user.address?.let {
            canvas.drawText(it, MARGIN_LEFT, y, normalPaint)
            y += 15f
        }
        user.city?.let { city ->
            user.state?.let { state ->
                user.zip?.let { zip ->
                    canvas.drawText("$city, $state $zip", MARGIN_LEFT, y, normalPaint)
                    y += 15f
                }
            }
        }
        user.phone?.let {
            canvas.drawText(it, MARGIN_LEFT, y, normalPaint)
            y += 15f
        }
        canvas.drawText(user.email, MARGIN_LEFT, y, normalPaint)
        
        // Invoice details (right side)
        var rightY = startY + 35f
        val labelPaint = Paint().apply {
            textSize = 10f
            color = Color.GRAY
        }
        val valuePaint = Paint().apply {
            textSize = 11f
            color = Color.BLACK
        }
        
        canvas.drawText("Invoice #:", PAGE_WIDTH - MARGIN_RIGHT - 150f, rightY, labelPaint)
        canvas.drawText(invoice.invoiceNumber, PAGE_WIDTH - MARGIN_RIGHT - 80f, rightY, valuePaint)
        rightY += 18f
        
        canvas.drawText("Date:", PAGE_WIDTH - MARGIN_RIGHT - 150f, rightY, labelPaint)
        canvas.drawText(
            invoice.createdAt.atZone(ZoneId.systemDefault()).format(DATE_FORMATTER),
            PAGE_WIDTH - MARGIN_RIGHT - 80f, rightY, valuePaint
        )
        rightY += 18f
        
        canvas.drawText("Due:", PAGE_WIDTH - MARGIN_RIGHT - 150f, rightY, labelPaint)
        val dueText = invoice.dueDate?.format(DATE_FORMATTER) ?: "Upon Receipt"
        canvas.drawText(dueText, PAGE_WIDTH - MARGIN_RIGHT - 80f, rightY, valuePaint)
        
        return maxOf(y, rightY) + 30f
    }
    
    private fun drawLineItemsTable(
        canvas: Canvas,
        lineItems: List<LineItem>,
        startY: Float
    ): Float {
        var y = startY + 20f
        
        // Table header
        val headerPaint = Paint().apply {
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.WHITE
        }
        
        // Header background
        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#1976D2")
        }
        canvas.drawRect(
            MARGIN_LEFT, y - 15f,
            PAGE_WIDTH - MARGIN_RIGHT, y + 5f,
            headerBgPaint
        )
        
        canvas.drawText("DESCRIPTION", MARGIN_LEFT + 10f, y, headerPaint)
        canvas.drawText("QTY", COL_QTY, y, headerPaint)
        canvas.drawText("PRICE", COL_PRICE, y, headerPaint)
        canvas.drawText("TOTAL", COL_TOTAL, y, headerPaint)
        y += 20f
        
        // Line items
        val itemPaint = Paint().apply {
            textSize = 11f
            color = Color.BLACK
        }
        
        lineItems.forEachIndexed { index, item ->
            // Alternating row background
            if (index % 2 == 1) {
                val rowBgPaint = Paint().apply {
                    color = Color.parseColor("#F5F5F5")
                }
                canvas.drawRect(
                    MARGIN_LEFT, y - 12f,
                    PAGE_WIDTH - MARGIN_RIGHT, y + 8f,
                    rowBgPaint
                )
            }
            
            canvas.drawText(item.description, MARGIN_LEFT + 10f, y, itemPaint)
            canvas.drawText(item.quantity.toString(), COL_QTY, y, itemPaint)
            canvas.drawText(item.unitPrice.formatAsCurrency(), COL_PRICE, y, itemPaint)
            canvas.drawText(item.total.formatAsCurrency(), COL_TOTAL, y, itemPaint)
            y += 20f
        }
        
        return y + 10f
    }
    
    private fun drawTotals(
        canvas: Canvas,
        invoice: InvoiceWithDetails,
        startY: Float
    ): Float {
        var y = startY + 10f
        
        val labelPaint = Paint().apply {
            textSize = 11f
            color = Color.DKGRAY
        }
        val valuePaint = Paint().apply {
            textSize = 11f
            color = Color.BLACK
        }
        val totalLabelPaint = Paint().apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.BLACK
        }
        val totalValuePaint = Paint().apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.parseColor("#1976D2")
        }
        
        val labelX = PAGE_WIDTH - MARGIN_RIGHT - 150f
        val valueX = PAGE_WIDTH - MARGIN_RIGHT - 50f
        
        // Subtotal
        canvas.drawText("Subtotal:", labelX, y, labelPaint)
        canvas.drawText(invoice.subtotal.formatAsCurrency(), valueX, y, valuePaint)
        y += 20f
        
        // Tax
        val taxLabel = if (invoice.taxRate != null && invoice.taxRate > BigDecimal.ZERO) {
            "Tax (${invoice.taxRate}%):"
        } else {
            "Tax:"
        }
        canvas.drawText(taxLabel, labelX, y, labelPaint)
        canvas.drawText(invoice.taxAmount.formatAsCurrency(), valueX, y, valuePaint)
        y += 25f
        
        // Divider line
        val linePaint = Paint().apply {
            strokeWidth = 2f
            color = Color.parseColor("#1976D2")
        }
        canvas.drawLine(labelX, y - 5f, PAGE_WIDTH - MARGIN_RIGHT, y - 5f, linePaint)
        y += 10f
        
        // Total
        canvas.drawText("TOTAL:", labelX, y, totalLabelPaint)
        canvas.drawText(invoice.total.formatAsCurrency(), valueX, y, totalValuePaint)
        
        return y + 30f
    }
    
    private fun drawPaymentInstructions(
        canvas: Canvas,
        paymentPrefs: PaymentPreferences?,
        startY: Float
    ): Float {
        if (paymentPrefs == null || !paymentPrefs.hasAnyMethod()) {
            return startY
        }
        
        var y = startY + 10f
        
        // Section header
        val headerPaint = Paint().apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.BLACK
        }
        canvas.drawText("PAYMENT INSTRUCTIONS", MARGIN_LEFT, y, headerPaint)
        y += 5f
        
        // Divider
        val linePaint = Paint().apply {
            strokeWidth = 1f
            color = Color.LTGRAY
        }
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
        y += 20f
        
        val textPaint = Paint().apply {
            textSize = 11f
            color = Color.DKGRAY
        }
        
        paymentPrefs.venmoHandle?.let {
            canvas.drawText("Venmo: @$it", MARGIN_LEFT, y, textPaint)
            y += 18f
        }
        paymentPrefs.zelleEmail?.let {
            canvas.drawText("Zelle: $it", MARGIN_LEFT, y, textPaint)
            y += 18f
        }
        paymentPrefs.checkPayableTo?.let {
            canvas.drawText("Check payable to: $it", MARGIN_LEFT, y, textPaint)
            y += 18f
        }
        paymentPrefs.otherInstructions?.let {
            canvas.drawText(it, MARGIN_LEFT, y, textPaint)
            y += 18f
        }
        
        return y + 10f
    }
    
    companion object {
        const val PAGE_WIDTH = 612  // Letter size
        const val PAGE_HEIGHT = 792
        const val MARGIN_LEFT = 50f
        const val MARGIN_RIGHT = 50f
        const val MARGIN_TOP = 50f
        const val COL_QTY = 400f
        const val COL_PRICE = 450f
        const val COL_TOTAL = 520f
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")
    }
}
```

### 6.3 Offline PDF Generation

PDF generation uses Android's built-in `PdfDocument` class:
- No network required
- Works completely offline
- Generates to app cache directory
- File can be shared via intent

---

## 7. Send Invoice

### 7.1 Email Delivery

**File**: `core/invoice/InvoiceEmailService.kt`

```kotlin
class InvoiceEmailService @Inject constructor(
    private val emailApiService: EmailApiService,
    private val pdfGenerator: InvoicePdfGenerator,
    private val userRepository: UserRepository
) {
    suspend fun sendInvoice(invoice: InvoiceWithDetails): Result<Unit> {
        return try {
            val user = userRepository.getCurrentUser()
                ?: return Result.failure(Exception("User not found"))
            
            // Generate PDF
            val pdfFile = pdfGenerator.generate(invoice)
            val pdfBase64 = Base64.encodeToString(pdfFile.readBytes(), Base64.NO_WRAP)
            
            // Build email
            val subject = "Invoice ${invoice.invoiceNumber} from ${user.businessName ?: user.fullName}"
            val body = buildEmailBody(invoice, user)
            
            // Send via API
            val request = SendEmailRequest(
                to = invoice.client.email,
                subject = subject,
                htmlBody = body,
                attachments = listOf(
                    EmailAttachment(
                        filename = "invoice_${invoice.invoiceNumber}.pdf",
                        contentType = "application/pdf",
                        contentBase64 = pdfBase64
                    )
                ),
                trackOpens = true,
                metadata = mapOf(
                    "invoiceId" to invoice.id,
                    "type" to "invoice"
                )
            )
            
            emailApiService.sendEmail(request)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun buildEmailBody(invoice: InvoiceWithDetails, user: User): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; color: #333; }
                    .header { margin-bottom: 20px; }
                    .summary { background: #f5f5f5; padding: 15px; border-radius: 8px; }
                    .total { font-size: 24px; font-weight: bold; color: #1976D2; }
                    .footer { margin-top: 30px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="header">
                    <p>Hi ${invoice.client.firstName},</p>
                    <p>Please find attached invoice <strong>${invoice.invoiceNumber}</strong> from ${user.businessName ?: user.fullName}.</p>
                </div>
                
                <div class="summary">
                    <p><strong>Invoice Summary</strong></p>
                    <p>Amount Due: <span class="total">${invoice.total.formatAsCurrency()}</span></p>
                    <p>Due Date: ${invoice.dueDate?.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")) ?: "Upon Receipt"}</p>
                </div>
                
                ${buildPaymentSection(user.paymentPreferences)}
                
                <div class="footer">
                    <p>Thank you for your business!</p>
                    <p>${user.businessName ?: user.fullName}<br>
                    ${user.phone ?: ""}<br>
                    ${user.email}</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
    
    private fun buildPaymentSection(prefs: PaymentPreferences?): String {
        if (prefs == null || !prefs.hasAnyMethod()) return ""
        
        val methods = mutableListOf<String>()
        prefs.venmoHandle?.let { methods.add("Venmo: @$it") }
        prefs.zelleEmail?.let { methods.add("Zelle: $it") }
        prefs.checkPayableTo?.let { methods.add("Check payable to: $it") }
        
        return """
            <div style="margin-top: 20px;">
                <p><strong>Payment Options</strong></p>
                <ul>
                    ${methods.joinToString("") { "<li>$it</li>" }}
                </ul>
            </div>
        """.trimIndent()
    }
}
```

### 7.2 SMS Notification (Optional)

When invoice sent, optionally send SMS:
- "Invoice sent for \$259.80. Check your email for details."
- Controlled by client's notification preferences
- Uses same SMS infrastructure as reminders (FRD-008)

### 7.3 Email Open Tracking

When client opens email:
1. Tracking pixel loaded → webhook triggered
2. Server updates invoice `viewedAt` timestamp
3. Status transitions from SENT to VIEWED
4. Syncs to app on next sync

---

## 8. Invoice Detail Screen

### 8.1 Screen Layout

**Route**: `/invoice/{invoiceId}`

```
┌─────────────────────────────────────────────────────────────┐
│                   INVOICE DETAIL                             │
├─────────────────────────────────────────────────────────────┤
│  [←] Invoice INV-2025-0042                    [⋮ More]      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Status: 🟡 SENT                                      │  │
│  │  Sent: January 13, 2025 at 3:45 PM                    │  │
│  │  Due: Upon Receipt                                    │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  Bill To                                                    │
│  ─────────────────────────────────────────────────────────  │
│  Johnson Ranch                                              │
│  Sarah Johnson                                              │
│  sarah@johnsonranch.com                                     │
│  123 Ranch Road, Austin, TX 78701                          │
│                                                              │
│  Services                                                   │
│  ─────────────────────────────────────────────────────────  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Midnight - Full Set                         $180.00  │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Dusty - Trim                                 $45.00  │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Trip Fee                                     $15.00  │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                    Subtotal     $240.00     │
│                                    Tax (8.25%)   $19.80     │
│                                    ─────────────────────    │
│                                    Total        $259.80     │
│                                                              │
│  ───────────────────────────────────────────────────────────│
│                                                              │
│  [Mark as Paid]           [View PDF]           [Resend]    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 8.2 Mark as Paid Flow

**Trigger**: User taps "Mark as Paid"

**Payment Recording Sheet**:

```
┌─────────────────────────────────────────────────────────────┐
│                   RECORD PAYMENT                             │
├─────────────────────────────────────────────────────────────┤
│  [×]                                        [Confirm]       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Amount                                                     │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  $259.80                                              │  │
│  └───────────────────────────────────────────────────────┘  │
│  Invoice total: $259.80                                    │
│                                                              │
│  Payment Date                                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  January 15, 2025                              [📅]   │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  Payment Method                                             │
│  (●) Cash           ( ) Check                              │
│  ( ) Venmo          ( ) Zelle                              │
│  ( ) Credit Card    ( ) Other                              │
│                                                              │
│  Notes (optional)                                           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                                                        │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**Payment Method Enum**:

```kotlin
enum class PaymentMethod(val displayName: String) {
    CASH("Cash"),
    CHECK("Check"),
    VENMO("Venmo"),
    ZELLE("Zelle"),
    CREDIT_CARD("Credit Card"),
    OTHER("Other")
}
```

### 8.3 More Menu Actions

```
┌─────────────────────────┐
│  Edit Invoice          │  (only if DRAFT)
│  Download PDF          │
│  Share Invoice         │
│  View Appointment      │  (if linked)
│  ──────────────────────│
│  Void Invoice          │  (destructive)
└─────────────────────────┘
```

---

## 9. Invoice List Screen

### 9.1 Screen Layout

**Route**: `/invoices`  
**Entry Point**: Bottom navigation or Dashboard → "Invoices"

```
┌─────────────────────────────────────────────────────────────┐
│                     INVOICES                                 │
├─────────────────────────────────────────────────────────────┤
│  [All ▼]                                    [+ New Invoice] │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Outstanding Summary                                        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Total Outstanding                        $1,247.50   │  │
│  │  ──────────────────────────────────────────────────── │  │
│  │  Current (< 30 days)       8 invoices      $847.50   │  │
│  │  30-60 days               2 invoices      $280.00   │  │
│  │  60-90 days               1 invoice       $120.00   │  │
│  │  > 90 days                0 invoices        $0.00   │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  This Week                                                  │
│  ─────────────────────────────────────────────────────────  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  🟡 INV-2025-0042         Johnson Ranch              │  │
│  │  Sent Jan 13              $259.80                    │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  🟢 INV-2025-0041         Williams Farm              │  │
│  │  Paid Jan 12              $180.00                    │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  🔵 INV-2025-0040         Martinez Stables           │  │
│  │  Draft                    $425.00                    │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  Last Week                                                  │
│  ─────────────────────────────────────────────────────────  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  🔴 INV-2025-0039         Anderson Ranch             │  │
│  │  Overdue (35 days)        $340.00                    │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  [Load more...]                                             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 9.2 Filter Options

**Filter Dropdown**:
- All
- Unpaid (DRAFT, SENT, VIEWED, OVERDUE)
- Overdue
- Paid
- Draft

### 9.3 Sort Options

**Sort Menu** (accessible via filter area):
- Date (newest first) - default
- Date (oldest first)
- Amount (highest first)
- Amount (lowest first)
- Client name (A-Z)

### 9.4 Aging Summary

**File**: `core/invoice/InvoiceAgingCalculator.kt`

```kotlin
class InvoiceAgingCalculator @Inject constructor(
    private val invoiceDao: InvoiceDao
) {
    suspend fun calculateAging(userId: String): AgingSummary {
        val outstandingInvoices = invoiceDao.getOutstandingInvoices(userId)
        val today = LocalDate.now()
        
        var current = AgingBucket(0, BigDecimal.ZERO)
        var days30to60 = AgingBucket(0, BigDecimal.ZERO)
        var days60to90 = AgingBucket(0, BigDecimal.ZERO)
        var over90 = AgingBucket(0, BigDecimal.ZERO)
        
        outstandingInvoices.forEach { invoice ->
            val daysSinceSent = invoice.sentAt?.let {
                ChronoUnit.DAYS.between(
                    it.atZone(ZoneId.systemDefault()).toLocalDate(),
                    today
                ).toInt()
            } ?: 0
            
            when {
                daysSinceSent < 30 -> current = current.add(invoice.total)
                daysSinceSent < 60 -> days30to60 = days30to60.add(invoice.total)
                daysSinceSent < 90 -> days60to90 = days60to90.add(invoice.total)
                else -> over90 = over90.add(invoice.total)
            }
        }
        
        return AgingSummary(
            totalOutstanding = current.amount + days30to60.amount + 
                               days60to90.amount + over90.amount,
            current = current,
            days30to60 = days30to60,
            days60to90 = days60to90,
            over90 = over90
        )
    }
}

data class AgingSummary(
    val totalOutstanding: BigDecimal,
    val current: AgingBucket,
    val days30to60: AgingBucket,
    val days60to90: AgingBucket,
    val over90: AgingBucket
)

data class AgingBucket(
    val count: Int,
    val amount: BigDecimal
) {
    fun add(invoiceAmount: BigDecimal): AgingBucket {
        return copy(count = count + 1, amount = amount + invoiceAmount)
    }
}
```

---

## 10. Manual Invoice Creation

### 10.1 Create Manual Invoice Flow

**Route**: `/invoice/create` (without appointmentId)

**Behavior**:
1. User taps "+ New Invoice" from Invoice List
2. Opens Invoice Editor with empty state
3. User must select client
4. Add line items manually
5. Set due date, notes, etc.
6. Save as Draft or Send

**Client Selection**:
- Client dropdown at top of Invoice Editor
- Required before saving
- Shows client name, business name, email

---

## 11. Data Models

### 11.1 InvoiceEntity

**File**: `core/database/entity/InvoiceEntity.kt`

```kotlin
@Entity(
    tableName = "invoices",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["client_id"]),
        Index(value = ["appointment_id"]),
        Index(value = ["invoice_number"], unique = true),
        Index(value = ["status"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AppointmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["appointment_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class InvoiceEntity(
    @PrimaryKey 
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "user_id") 
    val userId: String,
    
    @ColumnInfo(name = "client_id") 
    val clientId: String,
    
    @ColumnInfo(name = "appointment_id") 
    val appointmentId: String?,
    
    @ColumnInfo(name = "invoice_number") 
    val invoiceNumber: String,
    
    @ColumnInfo(name = "line_items") 
    val lineItemsJson: String,  // JSON array of LineItem
    
    val subtotal: BigDecimal,
    
    @ColumnInfo(name = "tax_rate") 
    val taxRate: BigDecimal?,
    
    @ColumnInfo(name = "tax_amount") 
    val taxAmount: BigDecimal,
    
    val total: BigDecimal,
    
    val status: InvoiceStatus,
    
    @ColumnInfo(name = "due_date") 
    val dueDate: LocalDate?,
    
    @ColumnInfo(name = "sent_at") 
    val sentAt: Instant?,
    
    @ColumnInfo(name = "viewed_at") 
    val viewedAt: Instant?,
    
    @ColumnInfo(name = "paid_at") 
    val paidAt: Instant?,
    
    @ColumnInfo(name = "payment_method") 
    val paymentMethod: PaymentMethod?,
    
    @ColumnInfo(name = "payment_notes") 
    val paymentNotes: String?,
    
    val notes: String?,
    
    @ColumnInfo(name = "created_at") 
    val createdAt: Instant = Instant.now(),
    
    @ColumnInfo(name = "updated_at") 
    val updatedAt: Instant = Instant.now(),
    
    @ColumnInfo(name = "sync_status") 
    val syncStatus: EntitySyncStatus = EntitySyncStatus.SYNCED
)
```

### 11.2 LineItem Model

**File**: `core/domain/model/LineItem.kt`

```kotlin
@Serializable
data class LineItem(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val quantity: Int = 1,
    val unitPrice: BigDecimal,
    val serviceType: String? = null  // Links to service if applicable
) {
    val total: BigDecimal
        get() = unitPrice * quantity.toBigDecimal()
}
```

### 11.3 Invoice Domain Model

**File**: `core/domain/model/Invoice.kt`

```kotlin
data class Invoice(
    val id: String,
    val userId: String,
    val clientId: String,
    val appointmentId: String?,
    val invoiceNumber: String,
    val lineItems: List<LineItem>,
    val subtotal: BigDecimal,
    val taxRate: BigDecimal?,
    val taxAmount: BigDecimal,
    val total: BigDecimal,
    val status: InvoiceStatus,
    val dueDate: LocalDate?,
    val sentAt: Instant?,
    val viewedAt: Instant?,
    val paidAt: Instant?,
    val paymentMethod: PaymentMethod?,
    val paymentNotes: String?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    val isOverdue: Boolean
        get() = status.isOutstanding && 
                dueDate != null && 
                LocalDate.now().isAfter(dueDate)
    
    val daysSinceSent: Int?
        get() = sentAt?.let {
            ChronoUnit.DAYS.between(
                it.atZone(ZoneId.systemDefault()).toLocalDate(),
                LocalDate.now()
            ).toInt()
        }
}

data class InvoiceWithDetails(
    val invoice: Invoice,
    val client: Client,
    val appointment: Appointment?
) {
    // Delegate properties for convenience
    val id get() = invoice.id
    val invoiceNumber get() = invoice.invoiceNumber
    val lineItems get() = invoice.lineItems
    val total get() = invoice.total
    val status get() = invoice.status
    // ... etc
}
```

### 11.4 InvoiceDao

**File**: `core/database/dao/InvoiceDao.kt`

```kotlin
@Dao
interface InvoiceDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InvoiceEntity)
    
    @Update
    suspend fun update(entity: InvoiceEntity)
    
    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getById(id: String): InvoiceEntity?
    
    @Query("SELECT * FROM invoices WHERE id = :id")
    fun getByIdFlow(id: String): Flow<InvoiceEntity?>
    
    @Query("""
        SELECT * FROM invoices 
        WHERE user_id = :userId 
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getInvoices(userId: String, limit: Int, offset: Int): Flow<List<InvoiceEntity>>
    
    @Query("""
        SELECT * FROM invoices 
        WHERE user_id = :userId AND status IN ('SENT', 'VIEWED', 'OVERDUE')
        ORDER BY sent_at ASC
    """)
    suspend fun getOutstandingInvoices(userId: String): List<InvoiceEntity>
    
    @Query("""
        SELECT * FROM invoices 
        WHERE user_id = :userId AND status = :status
        ORDER BY created_at DESC
    """)
    fun getInvoicesByStatus(userId: String, status: InvoiceStatus): Flow<List<InvoiceEntity>>
    
    @Query("""
        SELECT * FROM invoices 
        WHERE appointment_id = :appointmentId
    """)
    suspend fun getInvoiceForAppointment(appointmentId: String): InvoiceEntity?
    
    @Query("""
        UPDATE invoices 
        SET status = :status, 
            sent_at = :sentAt, 
            updated_at = :updatedAt,
            sync_status = :syncStatus
        WHERE id = :id
    """)
    suspend fun updateStatusSent(
        id: String,
        status: InvoiceStatus = InvoiceStatus.SENT,
        sentAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now(),
        syncStatus: EntitySyncStatus = EntitySyncStatus.PENDING_UPDATE
    )
    
    @Query("""
        UPDATE invoices 
        SET status = :status, 
            viewed_at = :viewedAt, 
            updated_at = :updatedAt,
            sync_status = :syncStatus
        WHERE id = :id
    """)
    suspend fun updateStatusViewed(
        id: String,
        status: InvoiceStatus = InvoiceStatus.VIEWED,
        viewedAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now(),
        syncStatus: EntitySyncStatus = EntitySyncStatus.PENDING_UPDATE
    )
    
    @Query("""
        UPDATE invoices 
        SET status = :status, 
            paid_at = :paidAt, 
            payment_method = :paymentMethod,
            payment_notes = :paymentNotes,
            updated_at = :updatedAt,
            sync_status = :syncStatus
        WHERE id = :id
    """)
    suspend fun markAsPaid(
        id: String,
        status: InvoiceStatus = InvoiceStatus.PAID,
        paidAt: Instant = Instant.now(),
        paymentMethod: PaymentMethod,
        paymentNotes: String?,
        updatedAt: Instant = Instant.now(),
        syncStatus: EntitySyncStatus = EntitySyncStatus.PENDING_UPDATE
    )
    
    @Query("""
        UPDATE invoices 
        SET status = 'OVERDUE', 
            updated_at = :updatedAt,
            sync_status = :syncStatus
        WHERE status IN ('SENT', 'VIEWED') 
        AND due_date IS NOT NULL 
        AND due_date < :today
    """)
    suspend fun markOverdueInvoices(
        today: LocalDate = LocalDate.now(),
        updatedAt: Instant = Instant.now(),
        syncStatus: EntitySyncStatus = EntitySyncStatus.PENDING_UPDATE
    )
    
    @Query("SELECT * FROM invoices WHERE sync_status != 'SYNCED'")
    suspend fun getPendingSync(): List<InvoiceEntity>
}
```

---

## 12. InvoiceRepository

**File**: `core/data/repository/InvoiceRepositoryImpl.kt`

```kotlin
class InvoiceRepositoryImpl @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val clientDao: ClientDao,
    private val appointmentDao: AppointmentDao,
    private val invoiceEmailService: InvoiceEmailService,
    private val syncManager: SyncManager,
    private val userRepository: UserRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : InvoiceRepository {
    
    override fun getInvoice(id: String): Flow<InvoiceWithDetails?> {
        return invoiceDao.getByIdFlow(id)
            .map { entity ->
                entity?.let { 
                    val client = clientDao.getById(it.clientId)!!
                    val appointment = it.appointmentId?.let { apptId ->
                        appointmentDao.getById(apptId)
                    }
                    InvoiceWithDetails(
                        invoice = it.toDomain(),
                        client = client.toDomain(),
                        appointment = appointment?.toDomain()
                    )
                }
            }
            .flowOn(ioDispatcher)
    }
    
    override suspend fun saveInvoice(invoice: Invoice): Result<Invoice> {
        return withContext(ioDispatcher) {
            try {
                val entity = invoice.toEntity().copy(
                    updatedAt = Instant.now(),
                    syncStatus = if (invoice.id == invoice.id) {
                        EntitySyncStatus.PENDING_UPDATE
                    } else {
                        EntitySyncStatus.PENDING_CREATE
                    }
                )
                
                invoiceDao.update(entity)
                
                syncManager.queueChange(
                    entityType = "invoices",
                    entityId = invoice.id,
                    operation = SyncOperation.UPDATE,
                    payload = Json.encodeToString(entity)
                )
                
                Result.success(invoice)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun sendInvoice(invoiceId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val invoice = getInvoice(invoiceId).first()
                    ?: return@withContext Result.failure(Exception("Invoice not found"))
                
                // Send email
                invoiceEmailService.sendInvoice(invoice)
                
                // Update status
                invoiceDao.updateStatusSent(invoiceId)
                
                syncManager.queueChange(
                    entityType = "invoices",
                    entityId = invoiceId,
                    operation = SyncOperation.UPDATE,
                    payload = null
                )
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun markAsPaid(
        invoiceId: String,
        paymentMethod: PaymentMethod,
        paymentDate: Instant,
        notes: String?
    ): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                invoiceDao.markAsPaid(
                    id = invoiceId,
                    paidAt = paymentDate,
                    paymentMethod = paymentMethod,
                    paymentNotes = notes
                )
                
                syncManager.queueChange(
                    entityType = "invoices",
                    entityId = invoiceId,
                    operation = SyncOperation.UPDATE,
                    payload = null
                )
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun voidInvoice(invoiceId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val entity = invoiceDao.getById(invoiceId)
                    ?: return@withContext Result.failure(Exception("Invoice not found"))
                
                invoiceDao.update(entity.copy(
                    status = InvoiceStatus.VOID,
                    updatedAt = Instant.now(),
                    syncStatus = EntitySyncStatus.PENDING_UPDATE
                ))
                
                syncManager.queueChange(
                    entityType = "invoices",
                    entityId = invoiceId,
                    operation = SyncOperation.UPDATE,
                    payload = null
                )
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
```

---

## 13. Overdue Invoice Check

**File**: `core/invoice/OverdueInvoiceWorker.kt`

```kotlin
@HiltWorker
class OverdueInvoiceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val invoiceDao: InvoiceDao
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        return try {
            invoiceDao.markOverdueInvoices()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
    
    companion object {
        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<OverdueInvoiceWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                "overdue_invoice_check",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
```

---

## 14. Acceptance Criteria

### Invoice Creation
| ID | Criteria | Test |
|----|----------|------|
| AC-013-01 | User completes appointment with 2 horses (Full Set $180, Trim $45), taps "Create Invoice" → draft invoice created with 2 line items, subtotal $225 | Integration |
| AC-013-02 | Invoice created → invoice number INV-2025-0001 assigned (first of year) | Integration |
| AC-013-03 | Second invoice same year → INV-2025-0002 assigned | Integration |
| AC-013-04 | User adds "Trip Fee" line item at $15 → subtotal updates to $240 | UI |
| AC-013-05 | User sets tax rate 8.25% on $240 subtotal → tax shows $19.80, total $259.80 | Unit |
| AC-013-06 | User taps "Save as Draft" → status = DRAFT, appears in invoice list | Integration |

### PDF Generation
| ID | Criteria | Test |
|----|----------|------|
| AC-013-07 | User taps "Preview PDF" → PDF generates with business info, client info, line items, totals, payment instructions | Integration |
| AC-013-08 | PDF generated while offline → generates successfully from local data | Integration |
| AC-013-09 | PDF includes payment instructions from user's preferences (Venmo, Zelle, etc.) | UI |

### Sending Invoices
| ID | Criteria | Test |
|----|----------|------|
| AC-013-10 | User taps "Send Invoice" → email sent to client with PDF attachment | Integration |
| AC-013-11 | Email subject: "Invoice INV-2025-0042 from [Business Name]" | Integration |
| AC-013-12 | Invoice sent → status changes from DRAFT to SENT, sentAt timestamp recorded | Integration |
| AC-013-13 | Client opens email → status changes to VIEWED, viewedAt recorded | Integration |
| AC-013-14 | User taps "Resend" on sent invoice → email sent again | Integration |

### Payment Tracking
| ID | Criteria | Test |
|----|----------|------|
| AC-013-15 | User taps "Mark as Paid", selects Venmo, confirms → status = PAID, paidAt and paymentMethod recorded | Integration |
| AC-013-16 | Paid invoice → no longer appears in "Outstanding" filter | Integration |
| AC-013-17 | Invoice past due date and still SENT/VIEWED → daily job marks as OVERDUE | Integration |

### Invoice List
| ID | Criteria | Test |
|----|----------|------|
| AC-013-18 | User views invoice list → invoices sorted by date, newest first | UI |
| AC-013-19 | User selects "Unpaid" filter → only DRAFT, SENT, VIEWED, OVERDUE invoices shown | UI |
| AC-013-20 | Outstanding summary shows correct totals by aging bucket (current, 30-60, 60-90, >90 days) | Unit |
| AC-013-21 | User taps invoice in list → navigates to invoice detail screen | UI |

### Manual Invoice
| ID | Criteria | Test |
|----|----------|------|
| AC-013-22 | User taps "+ New Invoice", selects client, adds line items → invoice created without appointment link | Integration |

### Void Invoice
| ID | Criteria | Test |
|----|----------|------|
| AC-013-23 | User taps "Void Invoice" on SENT invoice → confirmation dialog, then status = VOID | Integration |
| AC-013-24 | PAID invoice → "Void Invoice" option not available | UI |

### Offline
| ID | Criteria | Test |
|----|----------|------|
| AC-013-25 | User creates invoice while offline → saved locally, syncStatus = PENDING_CREATE | Integration |
| AC-013-26 | User views invoice list offline → all local invoices displayed | Integration |
| AC-013-27 | User comes online → pending invoices sync to server | Integration |

---

## 15. Performance Requirements

| Metric | Target | Measurement |
|--------|--------|-------------|
| Invoice creation | < 500ms | Instrumentation |
| PDF generation | < 3s | Instrumentation |
| Invoice list load (50 items) | < 300ms | Profiler |
| Email send | < 5s | Instrumentation |
| Aging calculation | < 200ms | Profiler |

---

## 16. File Reference Summary

| Component | File Path |
|-----------|-----------|
| Invoice List Screen | `feature/invoice/ui/InvoiceListScreen.kt` |
| Invoice List ViewModel | `feature/invoice/ui/InvoiceListViewModel.kt` |
| Invoice Detail Screen | `feature/invoice/ui/InvoiceDetailScreen.kt` |
| Invoice Detail ViewModel | `feature/invoice/ui/InvoiceDetailViewModel.kt` |
| Invoice Editor Screen | `feature/invoice/ui/InvoiceEditorScreen.kt` |
| Invoice Editor ViewModel | `feature/invoice/ui/InvoiceEditorViewModel.kt` |
| Line Item Sheet | `feature/invoice/ui/components/LineItemSheet.kt` |
| Payment Recording Sheet | `feature/invoice/ui/components/PaymentRecordingSheet.kt` |
| Aging Summary Card | `feature/invoice/ui/components/AgingSummaryCard.kt` |
| Invoice Card | `feature/invoice/ui/components/InvoiceCard.kt` |
| Invoice Entity | `core/database/entity/InvoiceEntity.kt` |
| Invoice DAO | `core/database/dao/InvoiceDao.kt` |
| Invoice Model | `core/domain/model/Invoice.kt` |
| Line Item Model | `core/domain/model/LineItem.kt` |
| Invoice Status | `core/domain/model/InvoiceStatus.kt` |
| Payment Method | `core/domain/model/PaymentMethod.kt` |
| Repository Interface | `core/domain/repository/InvoiceRepository.kt` |
| Repository Impl | `core/data/repository/InvoiceRepositoryImpl.kt` |
| Invoice Generator | `core/invoice/InvoiceGenerator.kt` |
| Number Generator | `core/invoice/InvoiceNumberGenerator.kt` |
| PDF Generator | `core/invoice/InvoicePdfGenerator.kt` |
| Email Service | `core/invoice/InvoiceEmailService.kt` |
| Aging Calculator | `core/invoice/InvoiceAgingCalculator.kt` |
| Overdue Worker | `core/invoice/OverdueInvoiceWorker.kt` |

---

## 17. Open Questions

1. **Partial Payments**: Should the system support partial payments with payment tracking? (Future enhancement)
2. **Recurring Invoices**: Should there be support for recurring invoice templates? (Future enhancement)
3. **Online Payment**: Integration with Stripe/Square for online payment acceptance? (Future enhancement)
4. **Invoice Templates**: Should users be able to customize the PDF template design?
5. **Tax Configuration**: Support for multiple tax rates or tax-exempt clients?
