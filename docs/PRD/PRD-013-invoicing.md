# PRD-013: Invoicing

**Priority**: P0  
**Phase**: 4 - Financial Tools  
**Estimated Duration**: 2 weeks

---

## Overview

### Purpose
Generate, send, and track invoices for completed appointments with PDF generation, email delivery, and payment status tracking.

### Business Value
- Professional invoicing reduces payment delays
- Tracks outstanding receivables
- Integrates with appointment workflow
- Works offline (generate PDF locally)

### Success Metrics
| Metric | Target |
|--------|--------|
| Invoice generation time | < 3 seconds |
| Email delivery rate | > 98% |
| Payment tracking accuracy | 100% |
| PDF generation (offline) | Working |

---

## User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|--------------|------------|----------|
| US-013-01 | Farrier | Generate invoice from appointment | I bill accurately | P0 |
| US-013-02 | Farrier | Send invoice via email | Client receives bill | P0 |
| US-013-03 | Farrier | Mark invoice as paid | I track payments | P0 |
| US-013-04 | Farrier | See outstanding invoices | I know who owes me | P0 |
| US-013-05 | Farrier | Add custom line items | I bill for extras | P1 |
| US-013-06 | Farrier | Create manual invoice | Some work isn't scheduled | P1 |
| US-013-07 | Farrier | Configure tax rate | Invoices are complete | P2 |

---

## Functional Requirements

### FR-013-01: Invoice Generation
- Auto-generate from completed appointment
- Line items from appointment services
- Add additional line items (trip fee, supplies)
- Auto-numbering (INV-YYYY-NNNN)
- Subtotal, tax (optional), total
- Due date (default: upon receipt)

### FR-013-02: Invoice Status
```kotlin
enum class InvoiceStatus {
    DRAFT,      // Created, not sent
    SENT,       // Emailed to client
    VIEWED,     // Client opened email/link
    PAID,       // Payment received
    OVERDUE,    // Past due date
    VOID        // Cancelled
}
```

### FR-013-03: PDF Generation
- Generate locally (iText library)
- Professional template with business info
- Client details and address
- Line items with prices
- Payment instructions (from preferences)
- Farrier contact info
- Works offline

### FR-013-04: Send Invoice
- Email with PDF attachment
- Subject: "Invoice {number} from {business}"
- Body: summary + payment options
- Track email opens
- SMS notification option

### FR-013-05: Payment Tracking
- Mark as paid
- Record payment method
- Record payment date
- Partial payments (future)

### FR-013-06: Invoice List
- Filter: All, Unpaid, Overdue, Paid
- Sort: Date, Amount, Client
- Aging summary (30/60/90 days)

---

## Technical Implementation

```kotlin
@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "client_id") val clientId: String,
    @ColumnInfo(name = "appointment_id") val appointmentId: String?,
    @ColumnInfo(name = "invoice_number") val invoiceNumber: String,
    @ColumnInfo(name = "line_items") val lineItems: String, // JSON
    val subtotal: BigDecimal,
    @ColumnInfo(name = "tax_rate") val taxRate: BigDecimal?,
    @ColumnInfo(name = "tax_amount") val taxAmount: BigDecimal?,
    val total: BigDecimal,
    val status: InvoiceStatus = InvoiceStatus.DRAFT,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate?,
    @ColumnInfo(name = "sent_at") val sentAt: Instant?,
    @ColumnInfo(name = "viewed_at") val viewedAt: Instant?,
    @ColumnInfo(name = "paid_at") val paidAt: Instant?,
    @ColumnInfo(name = "payment_method") val paymentMethod: String?,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now()
)

data class LineItem(
    val description: String,
    val quantity: Int = 1,
    val unitPrice: BigDecimal
) {
    val total: BigDecimal get() = unitPrice * quantity.toBigDecimal()
}

class InvoiceRepository @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val pdfGenerator: InvoicePdfGenerator,
    private val emailService: EmailService,
    private val syncManager: SyncManager
) {
    suspend fun createFromAppointment(
        appointment: AppointmentWithDetails
    ): Result<Invoice> {
        val lineItems = appointment.horses.map { horse ->
            LineItem(
                description = "${horse.horseName} - ${horse.serviceType.displayName}",
                quantity = 1,
                unitPrice = horse.price
            )
        }
        
        val invoice = Invoice(
            id = UUID.randomUUID().toString(),
            clientId = appointment.client.id,
            appointmentId = appointment.appointment.id,
            invoiceNumber = generateInvoiceNumber(),
            lineItems = lineItems,
            subtotal = lineItems.sumOf { it.total },
            status = InvoiceStatus.DRAFT
        )
        
        invoiceDao.insert(invoice.toEntity())
        syncManager.queueChange("invoices", invoice.id, SyncOperation.INSERT, ...)
        
        return Result.success(invoice)
    }
    
    suspend fun generatePdf(invoiceId: String): File {
        val invoice = invoiceDao.getById(invoiceId)
        val user = userDao.getCurrentUser()
        return pdfGenerator.generate(invoice, user)
    }
    
    suspend fun send(invoiceId: String): Result<Unit> {
        val invoice = invoiceDao.getById(invoiceId)
        val pdfFile = generatePdf(invoiceId)
        
        emailService.send(
            to = invoice.clientEmail,
            subject = "Invoice ${invoice.invoiceNumber} from ${invoice.businessName}",
            body = buildEmailBody(invoice),
            attachments = listOf(pdfFile)
        )
        
        invoiceDao.updateStatus(invoiceId, InvoiceStatus.SENT, sentAt = Instant.now())
        return Result.success(Unit)
    }
}

// InvoicePdfGenerator.kt
class InvoicePdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun generate(invoice: Invoice, user: User): File {
        val outputFile = File(context.cacheDir, "invoice_${invoice.invoiceNumber}.pdf")
        
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create() // Letter size
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        
        // Draw header with business info
        drawHeader(canvas, user)
        
        // Draw client info
        drawClientInfo(canvas, invoice)
        
        // Draw line items table
        drawLineItems(canvas, invoice.lineItems)
        
        // Draw totals
        drawTotals(canvas, invoice)
        
        // Draw payment instructions
        drawPaymentInstructions(canvas, user.paymentPreferences)
        
        document.finishPage(page)
        document.writeTo(FileOutputStream(outputFile))
        document.close()
        
        return outputFile
    }
}
```

---

## UI Specifications

### Invoice Detail Screen
```
┌─────────────────────────────────────────┐
│ [←] Invoice INV-2024-0042       [Send]  │
├─────────────────────────────────────────┤
│                                         │
│  Status: 🟡 UNPAID                      │
│  Due: Upon Receipt                      │
│                                         │
│  Bill To                                │
│  ─────────────────────────────────────  │
│  Johnson Ranch                          │
│  Sarah Johnson                          │
│  sarah@johnsonranch.com                │
│                                         │
│  Services                               │
│  ─────────────────────────────────────  │
│  Midnight - Full Set        $180.00    │
│  Dusty - Trim               $45.00     │
│  Trip Fee                   $15.00     │
│                            ──────────   │
│  Subtotal                  $240.00     │
│  Tax (0%)                  $0.00       │
│  Total                     $240.00     │
│                                         │
│  [Mark as Paid]  [View PDF]            │
│                                         │
└─────────────────────────────────────────┘
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-013-01 | Invoice generates from appointment | Integration test |
| AC-013-02 | PDF generates offline | Integration test |
| AC-013-03 | Email sends with PDF | Manual test |
| AC-013-04 | Status updates correctly | Unit test |
| AC-013-05 | Outstanding list accurate | Integration test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-005 (Appointments) | Internal | Required |
| PRD-012 (Prices) | Internal | Required |
| PRD-014 (Payment Prefs) | Internal | Required |
| iText PDF library | Library | Available |
