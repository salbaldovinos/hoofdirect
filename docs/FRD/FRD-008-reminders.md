# FRD-008: Reminders & Confirmations

**PRD Reference**: PRD-008-reminders.md  
**Priority**: P1  
**Phase**: 2 - Scheduling Core  
**Estimated Duration**: 1 week

---

## 1. Overview

### 1.1 Purpose

This document specifies the complete functional requirements for automated appointment reminders via SMS and email, confirmation tracking from client responses, daily digest notifications, and SMS usage management against subscription tier limits.

### 1.2 Scope

This FRD covers:
- Reminder scheduling via WorkManager
- SMS reminder delivery via Twilio
- Email reminder delivery via SendGrid
- Confirmation tracking and status updates
- Twilio webhook handling for SMS replies
- Daily digest push notifications
- SMS usage tracking and limits
- Reminder settings UI

### 1.3 Dependencies

| Dependency | FRD | Description |
|------------|-----|-------------|
| Appointments | FRD-005 | Appointment data and status |
| Client Management | FRD-003 | Client preferences and contact info |
| Usage Limits | FRD-017 | SMS tier limits |
| Offline Architecture | FRD-002 | Network state monitoring |

---

## 2. Confirmation Status State Machine

### 2.1 Status Definitions

```kotlin
// File: core/domain/model/ConfirmationStatus.kt

enum class ConfirmationStatus {
    NOT_SENT,      // Reminder not yet sent
    AWAITING,      // Reminder sent, no response yet
    CONFIRMED,     // Client replied YES or clicked confirm link
    DECLINED,      // Client replied NO or requested reschedule
    NOT_REQUIRED   // Client preference is no reminders
}
```

### 2.2 Status Transitions

```
NOT_SENT ─────────────────────────────────────────────────────────────┐
    │                                                                  │
    │ [Reminder sent]                                                  │
    ▼                                                                  │
AWAITING ─────────────────────────────────────────────────────────────┤
    │                                                                  │
    ├── [Client replies YES/CONFIRM] ──► CONFIRMED                    │
    │                                                                  │
    └── [Client replies NO/CANCEL] ───► DECLINED                      │
                                                                       │
NOT_REQUIRED ◄─────── [Client preference = NONE] ─────────────────────┘
```

### 2.3 Status Display

| Status | Icon | Color | Label |
|--------|------|-------|-------|
| NOT_SENT | ○ (empty circle) | Gray (#9E9E9E) | "Pending" |
| AWAITING | ⏳ (hourglass) | Orange (#FF9800) | "Awaiting" |
| CONFIRMED | ✓ (checkmark) | Green (#4CAF50) | "Confirmed" |
| DECLINED | ✗ (x mark) | Red (#F44336) | "Declined" |
| NOT_REQUIRED | — (dash) | Gray (#757575) | "No reminder" |

---

## 3. Reminder Scheduling

### 3.1 Reminder Scheduler

**File**: `core/reminder/ReminderScheduler.kt`

```kotlin
@Singleton
class ReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val clientRepository: ClientRepository
) {
    suspend fun scheduleReminder(appointment: AppointmentWithDetails) {
        val client = appointment.client
        
        // Skip if client doesn't want reminders
        if (client.reminderPreference == ReminderPreference.NONE) {
            return
        }
        
        // Calculate reminder time
        val reminderHours = client.reminderHours ?: DEFAULT_REMINDER_HOURS // 24
        val appointmentDateTime = LocalDateTime.of(appointment.date, appointment.time)
        val reminderDateTime = appointmentDateTime.minusHours(reminderHours.toLong())
        
        // Skip if reminder time is in the past
        val now = LocalDateTime.now()
        if (reminderDateTime.isBefore(now)) {
            return
        }
        
        val delay = Duration.between(now, reminderDateTime)
        
        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                KEY_APPOINTMENT_ID to appointment.id,
                KEY_CLIENT_ID to client.id
            ))
            .addTag(TAG_REMINDER)
            .addTag("reminder_${appointment.id}")
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        
        workManager.enqueueUniqueWork(
            "reminder_${appointment.id}",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
    
    fun cancelReminder(appointmentId: String) {
        workManager.cancelUniqueWork("reminder_$appointmentId")
    }
    
    fun cancelAllReminders() {
        workManager.cancelAllWorkByTag(TAG_REMINDER)
    }
    
    companion object {
        const val DEFAULT_REMINDER_HOURS = 24
        const val TAG_REMINDER = "appointment_reminder"
        const val KEY_APPOINTMENT_ID = "appointment_id"
        const val KEY_CLIENT_ID = "client_id"
    }
}
```

### 3.2 Scheduling Triggers

| Event | Action |
|-------|--------|
| Appointment created | Schedule reminder based on client preference |
| Appointment rescheduled | Cancel existing, schedule new reminder |
| Appointment cancelled | Cancel reminder |
| Appointment status → CONFIRMED | Cancel reminder (no longer needed) |
| Client reminder preference changed | Reschedule all pending reminders for client |

### 3.3 When Appointments are Created

**File**: `feature/appointments/domain/CreateAppointmentUseCase.kt`

After creating appointment:
```kotlin
// Schedule reminder
reminderScheduler.scheduleReminder(createdAppointment)
```

### 3.4 When Appointments are Rescheduled

**File**: `feature/appointments/domain/RescheduleAppointmentUseCase.kt`

```kotlin
// Cancel old reminder
reminderScheduler.cancelReminder(appointment.id)

// Update appointment date/time
appointmentRepository.updateDateTime(appointment.id, newDate, newTime)

// Schedule new reminder
val updatedAppointment = appointmentRepository.getById(appointment.id)
reminderScheduler.scheduleReminder(updatedAppointment)
```

---

## 4. Reminder Worker

### 4.1 Worker Implementation

**File**: `core/worker/ReminderWorker.kt`

```kotlin
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val appointmentRepository: AppointmentRepository,
    private val clientRepository: ClientRepository,
    private val smsService: TwilioSmsService,
    private val emailService: SendGridEmailService,
    private val usageLimitsManager: UsageLimitsManager,
    private val userRepository: UserRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appointmentId = inputData.getString(KEY_APPOINTMENT_ID) 
            ?: return Result.failure()
        
        // Load appointment with details
        val appointment = appointmentRepository.getWithDetailsById(appointmentId)
            ?: return Result.failure() // Appointment deleted
        
        // Skip if appointment already confirmed, cancelled, or completed
        if (appointment.status in listOf(
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.CANCELLED,
            AppointmentStatus.COMPLETED,
            AppointmentStatus.NO_SHOW
        )) {
            return Result.success()
        }
        
        // Skip if confirmation already received
        if (appointment.confirmationStatus == ConfirmationStatus.CONFIRMED) {
            return Result.success()
        }
        
        val client = appointment.client
        val user = userRepository.getCurrentUser()
        
        return try {
            when (client.reminderPreference) {
                ReminderPreference.SMS -> {
                    sendSmsReminder(appointment, client, user)
                }
                ReminderPreference.EMAIL -> {
                    sendEmailReminder(appointment, client, user)
                }
                ReminderPreference.BOTH -> {
                    sendSmsReminder(appointment, client, user)
                    sendEmailReminder(appointment, client, user)
                }
                ReminderPreference.NONE -> {
                    // Should not reach here, but handle gracefully
                }
            }
            
            // Update appointment reminder sent timestamp
            appointmentRepository.updateReminderSent(
                appointmentId = appointmentId,
                sentAt = Instant.now()
            )
            
            // Update confirmation status to AWAITING
            appointmentRepository.updateConfirmationStatus(
                appointmentId = appointmentId,
                status = ConfirmationStatus.AWAITING
            )
            
            Result.success()
        } catch (e: SmsLimitExceededException) {
            // Don't retry - user at SMS limit
            Result.failure(workDataOf("error" to "SMS_LIMIT_EXCEEDED"))
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to e.message))
            }
        }
    }
    
    private suspend fun sendSmsReminder(
        appointment: AppointmentWithDetails,
        client: ClientEntity,
        user: UserEntity
    ) {
        // Check SMS limit
        if (!usageLimitsManager.canSendSms()) {
            throw SmsLimitExceededException()
        }
        
        val phone = client.phone ?: throw IllegalStateException("Client has no phone")
        
        val message = buildSmsMessage(appointment, client, user)
        
        smsService.sendSms(
            to = phone,
            body = message,
            statusCallback = "${BuildConfig.SUPABASE_FUNCTIONS_URL}/twilio-status"
        )
        
        // Increment usage counter
        usageLimitsManager.incrementSmsCount()
    }
    
    private suspend fun sendEmailReminder(
        appointment: AppointmentWithDetails,
        client: ClientEntity,
        user: UserEntity
    ) {
        val email = client.email ?: throw IllegalStateException("Client has no email")
        
        val htmlContent = buildEmailHtml(appointment, client, user)
        val icsAttachment = buildCalendarAttachment(appointment, client)
        
        emailService.sendEmail(
            to = email,
            subject = "Appointment Reminder - ${appointment.date.format(DateTimeFormatter.ofPattern("MMM d"))}",
            htmlBody = htmlContent,
            attachments = listOf(icsAttachment)
        )
    }
    
    companion object {
        const val MAX_RETRIES = 3
        const val KEY_APPOINTMENT_ID = "appointment_id"
    }
}
```

### 4.2 SMS Message Format

**Function**: `buildSmsMessage()`

```kotlin
private fun buildSmsMessage(
    appointment: AppointmentWithDetails,
    client: ClientEntity,
    user: UserEntity
): String {
    val dateText = formatDateRelative(appointment.date) // "tomorrow", "Monday, Jan 20"
    val timeText = appointment.time.format(DateTimeFormatter.ofPattern("h:mm a"))
    val horseNames = appointment.horses.take(3).joinToString(", ") { it.name }
    val horseSuffix = if (appointment.horses.size > 3) " + ${appointment.horses.size - 3} more" else ""
    val location = client.locationName ?: "${client.city}, ${client.state}"
    
    return """
Hi ${client.firstName}! Reminder: Your farrier appointment is $dateText at $timeText.

Horses: $horseNames$horseSuffix
Location: $location

Reply YES to confirm or call ${user.phone} to reschedule.
    """.trimIndent()
}
```

**Example Output**:
```
Hi Sarah! Reminder: Your farrier appointment is tomorrow at 8:00 AM.

Horses: Midnight, Dusty, Thunder
Location: Johnson Ranch

Reply YES to confirm or call (555) 987-6543 to reschedule.
```

### 4.3 Email HTML Template

**Function**: `buildEmailHtml()`

The email template includes:
- Business branding (name, logo if available)
- Appointment date/time prominently displayed
- List of horses and services
- Location with map link
- One-click confirm button (deep link)
- Farrier contact information
- Unsubscribe link

**Deep Link URL**: `https://confirm.hoofdirect.com/a/{appointmentId}?token={confirmToken}`

### 4.4 Calendar Attachment (.ics)

**Function**: `buildCalendarAttachment()`

```kotlin
private fun buildCalendarAttachment(
    appointment: AppointmentWithDetails,
    client: ClientEntity
): EmailAttachment {
    val startDateTime = LocalDateTime.of(appointment.date, appointment.time)
    val endDateTime = startDateTime.plusMinutes(appointment.durationMinutes.toLong())
    
    val icsContent = """
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Hoof Direct//Appointment//EN
BEGIN:VEVENT
UID:${appointment.id}@hoofdirect.com
DTSTART:${startDateTime.format(ICS_DATE_FORMAT)}
DTEND:${endDateTime.format(ICS_DATE_FORMAT)}
SUMMARY:Farrier Appointment
LOCATION:${client.fullAddress}
DESCRIPTION:Horses: ${appointment.horses.joinToString(", ") { it.name }}
END:VEVENT
END:VCALENDAR
    """.trimIndent()
    
    return EmailAttachment(
        filename = "appointment.ics",
        contentType = "text/calendar",
        content = icsContent.toByteArray()
    )
}
```

---

## 5. SMS Reply Handling

### 5.1 Twilio Webhook

**Supabase Edge Function**: `functions/twilio-sms-webhook/index.ts`

```typescript
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

serve(async (req) => {
    // Parse Twilio webhook payload
    const formData = await req.formData()
    const from = formData.get("From") as string // +15551234567
    const body = (formData.get("Body") as string).toUpperCase().trim()
    const messageSid = formData.get("MessageSid") as string
    
    const supabase = createClient(
        Deno.env.get("SUPABASE_URL")!,
        Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    )
    
    // Find pending appointment by client phone
    const { data: appointment, error: aptError } = await supabase
        .from("appointments")
        .select(`
            id,
            user_id,
            confirmation_status,
            date,
            clients!inner(
                first_name,
                phone
            )
        `)
        .eq("clients.phone", from)
        .eq("confirmation_status", "AWAITING")
        .gte("date", new Date().toISOString().split("T")[0])
        .order("date", { ascending: true })
        .limit(1)
        .single()
    
    if (!appointment) {
        // No pending appointment found - ignore
        return new Response("OK", { status: 200 })
    }
    
    // Parse response
    let newStatus: string | null = null
    
    if (["YES", "Y", "CONFIRM", "CONFIRMED", "OK", "YEP", "SURE"].includes(body)) {
        newStatus = "CONFIRMED"
    } else if (["NO", "N", "CANCEL", "RESCHEDULE"].includes(body)) {
        newStatus = "DECLINED"
    }
    
    if (!newStatus) {
        // Unknown response - ignore
        return new Response("OK", { status: 200 })
    }
    
    // Update appointment confirmation status
    const { error: updateError } = await supabase
        .from("appointments")
        .update({
            confirmation_status: newStatus,
            confirmation_received_at: new Date().toISOString()
        })
        .eq("id", appointment.id)
    
    // Send push notification to farrier
    const title = newStatus === "CONFIRMED" 
        ? "Appointment Confirmed" 
        : "Appointment Declined"
    const notificationBody = `${appointment.clients.first_name} ${
        newStatus === "CONFIRMED" ? "confirmed" : "declined"
    } their appointment on ${new Date(appointment.date).toLocaleDateString()}`
    
    await supabase.functions.invoke("send-push-notification", {
        body: {
            userId: appointment.user_id,
            title,
            body: notificationBody,
            data: {
                type: "confirmation_response",
                appointmentId: appointment.id
            }
        }
    })
    
    // Return TwiML response (optional thank you message)
    const twiml = newStatus === "CONFIRMED"
        ? `<?xml version="1.0" encoding="UTF-8"?><Response><Message>Thanks! Your appointment is confirmed.</Message></Response>`
        : `<?xml version="1.0" encoding="UTF-8"?><Response><Message>Got it. Your farrier will reach out to reschedule.</Message></Response>`
    
    return new Response(twiml, {
        headers: { "Content-Type": "text/xml" }
    })
})
```

### 5.2 Confirmation Response Sync

When the app receives a push notification about confirmation response, it syncs the updated appointment:

**File**: `core/push/PushNotificationHandler.kt`

```kotlin
fun handleConfirmationResponse(data: Map<String, String>) {
    val appointmentId = data["appointmentId"] ?: return
    
    // Trigger sync for this appointment
    syncManager.syncAppointment(appointmentId)
    
    // Show local notification
    notificationHelper.showConfirmationNotification(
        title = data["title"] ?: "Confirmation Update",
        body = data["body"] ?: ""
    )
}
```

---

## 6. Daily Digest

### 6.1 Digest Worker

**File**: `core/worker/DailyDigestWorker.kt`

```kotlin
@HiltWorker
class DailyDigestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val appointmentRepository: AppointmentRepository,
    private val routeRepository: RouteRepository,
    private val notificationHelper: NotificationHelper,
    private val userPreferencesManager: UserPreferencesManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        val appointments = appointmentRepository.getAppointmentsForDate(today)
        
        // Skip if no appointments today
        if (appointments.isEmpty()) {
            return Result.success()
        }
        
        // Calculate statistics
        val total = appointments.size
        val confirmed = appointments.count { 
            it.confirmationStatus == ConfirmationStatus.CONFIRMED 
        }
        val awaiting = appointments.count { 
            it.confirmationStatus == ConfirmationStatus.AWAITING 
        }
        val firstTime = appointments.minOf { it.time }
        
        // Get route info if available
        val route = routeRepository.getRouteForDate(today)
        val totalMiles = route?.totalMiles
        val totalDriveMinutes = route?.totalDriveMinutes
        
        // Build notification content
        val title = "Today's Schedule"
        val body = buildDigestBody(total, confirmed, awaiting, firstTime, totalMiles)
        
        notificationHelper.showDailyDigest(
            title = title,
            body = body,
            appointmentCount = total,
            confirmedCount = confirmed
        )
        
        return Result.success()
    }
    
    private fun buildDigestBody(
        total: Int,
        confirmed: Int,
        awaiting: Int,
        firstTime: LocalTime,
        totalMiles: Double?
    ): String {
        val sb = StringBuilder()
        sb.append("$total appointment${if (total > 1) "s" else ""}")
        
        if (confirmed > 0 || awaiting > 0) {
            sb.append(" (")
            if (confirmed > 0) sb.append("$confirmed confirmed")
            if (confirmed > 0 && awaiting > 0) sb.append(", ")
            if (awaiting > 0) sb.append("$awaiting awaiting")
            sb.append(")")
        }
        
        sb.append("\n")
        sb.append("First at ${firstTime.format(DateTimeFormatter.ofPattern("h:mm a"))}")
        
        if (totalMiles != null && totalMiles > 0) {
            sb.append("\n")
            sb.append("${totalMiles.toInt()} miles total")
        }
        
        return sb.toString()
    }
}
```

### 6.2 Digest Scheduling

**File**: `core/worker/DigestScheduler.kt`

```kotlin
@Singleton
class DigestScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val userPreferencesManager: UserPreferencesManager
) {
    suspend fun scheduleDigest() {
        val digestTime = userPreferencesManager.dailyDigestTime.first() // Default 6:00 AM
        
        // Calculate delay until next digest time
        val now = LocalDateTime.now()
        var nextDigestDateTime = LocalDateTime.of(now.toLocalDate(), digestTime)
        
        // If today's time has passed, schedule for tomorrow
        if (now.isAfter(nextDigestDateTime)) {
            nextDigestDateTime = nextDigestDateTime.plusDays(1)
        }
        
        val delay = Duration.between(now, nextDigestDateTime)
        
        val workRequest = OneTimeWorkRequestBuilder<DailyDigestWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(TAG_DAILY_DIGEST)
            .build()
        
        workManager.enqueueUniqueWork(
            WORK_NAME_DAILY_DIGEST,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
    
    fun cancelDigest() {
        workManager.cancelUniqueWork(WORK_NAME_DAILY_DIGEST)
    }
    
    companion object {
        const val TAG_DAILY_DIGEST = "daily_digest"
        const val WORK_NAME_DAILY_DIGEST = "daily_digest"
    }
}
```

### 6.3 Digest Notification

**File**: `core/notification/NotificationHelper.kt`

```kotlin
fun showDailyDigest(
    title: String,
    body: String,
    appointmentCount: Int,
    confirmedCount: Int
) {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = ACTION_OPEN_SCHEDULE
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    
    val pendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE_DAILY_DIGEST,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_DIGEST)
        .setSmallIcon(R.drawable.ic_notification_schedule)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    
    notificationManager.notify(NOTIFICATION_ID_DAILY_DIGEST, notification)
}
```

### 6.4 Notification Channel Setup

**File**: `HoofDirectApplication.kt`

```kotlin
private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channels = listOf(
            NotificationChannel(
                CHANNEL_DAILY_DIGEST,
                "Daily Schedule Digest",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Your daily appointment summary"
            },
            NotificationChannel(
                CHANNEL_CONFIRMATIONS,
                "Appointment Confirmations",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Client confirmation responses"
            },
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Reminder Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Reminder delivery updates"
            }
        )
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        channels.forEach { notificationManager.createNotificationChannel(it) }
    }
}
```

---

## 7. SMS Usage Tracking

### 7.1 Usage Limits Manager

**File**: `core/subscription/UsageLimitsManager.kt`

```kotlin
@Singleton
class UsageLimitsManager @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val usageRepository: UsageRepository
) {
    suspend fun canSendSms(): Boolean {
        val usage = getCurrentSmsUsage()
        return usage.used < usage.limit
    }
    
    suspend fun getCurrentSmsUsage(): SmsUsage {
        val subscription = subscriptionRepository.getCurrentSubscription()
        val smsLimit = subscription.tier.smsLimit // 50, 200, or unlimited
        val currentMonth = YearMonth.now()
        val usedCount = usageRepository.getSmsCountForMonth(currentMonth)
        val resetDate = currentMonth.plusMonths(1).atDay(1)
        
        return SmsUsage(
            used = usedCount,
            limit = smsLimit,
            resetDate = resetDate,
            tierName = subscription.tier.name
        )
    }
    
    suspend fun incrementSmsCount() {
        val currentMonth = YearMonth.now()
        usageRepository.incrementSmsCount(currentMonth)
    }
    
    suspend fun getSmsWarningState(): SmsWarningState {
        val usage = getCurrentSmsUsage()
        
        return when {
            usage.limit == Int.MAX_VALUE -> SmsWarningState.None // Unlimited
            usage.percentUsed >= 1.0f -> SmsWarningState.AtLimit
            usage.percentUsed >= 0.8f -> SmsWarningState.Warning(usage.remaining)
            else -> SmsWarningState.None
        }
    }
}

data class SmsUsage(
    val used: Int,
    val limit: Int,
    val resetDate: LocalDate,
    val tierName: String
) {
    val remaining: Int get() = maxOf(0, limit - used)
    val percentUsed: Float get() = if (limit == Int.MAX_VALUE) 0f else used.toFloat() / limit
    val isAtLimit: Boolean get() = limit != Int.MAX_VALUE && used >= limit
    val isUnlimited: Boolean get() = limit == Int.MAX_VALUE
}

sealed class SmsWarningState {
    object None : SmsWarningState()
    data class Warning(val remaining: Int) : SmsWarningState()
    object AtLimit : SmsWarningState()
}
```

### 7.2 SMS Limit Exceeded Handling

When SMS limit is exceeded:

1. **ReminderWorker**: Returns `Result.failure()` without retry
2. **UI Notification**: Shows "SMS limit reached" notification
3. **Settings Badge**: Shows warning indicator on Settings tab
4. **Upgrade Prompt**: Modal bottom sheet with upgrade options

**File**: `feature/settings/ui/SmsLimitBottomSheet.kt`

```kotlin
@Composable
fun SmsLimitBottomSheet(
    usage: SmsUsage,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "SMS Limit Reached",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "You've used all ${usage.limit} SMS reminders for this month. " +
                    "Upgrade your plan to send more reminders.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Resets ${usage.resetDate.format(DateTimeFormatter.ofPattern("MMM d"))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Upgrade Plan")
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            TextButton(onClick = onDismiss) {
                Text("Maybe Later")
            }
        }
    }
}
```

---

## 8. Reminder Settings Screen

### 8.1 Screen Overview

**Route**: `/settings/reminders`

**Entry Point**: Settings screen → "Reminders" row

### 8.2 Screen Layout

```
┌─────────────────────────────────────────────────────────────┐
│  ← Reminders                                                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  SMS REMINDERS                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Enable SMS Reminders              [Toggle: ON]     │   │
│  │  Send text message reminders to clients             │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  SMS Usage This Month                                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  ████████████░░░░░░░░  38/50 used                   │   │
│  │  12 remaining • Resets Feb 1                        │   │
│  │                                                     │   │
│  │  [Upgrade for More SMS →]                           │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  EMAIL REMINDERS                                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Enable Email Reminders            [Toggle: ON]     │   │
│  │  Send email reminders with calendar attachment      │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  DAILY DIGEST                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Enable Daily Digest               [Toggle: ON]     │   │
│  │  Get a summary of your day each morning             │   │
│  ├─────────────────────────────────────────────────────┤   │
│  │  Digest Time                       6:00 AM    >     │   │
│  │  When to receive your daily summary                 │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  DEFAULT REMINDER TIMING                                    │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Default Reminder Time             24 hours    >    │   │
│  │  Hours before appointment for new clients           │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ⓘ Individual clients can have custom reminder             │
│    preferences set in their profile.                       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 8.3 Reminder Settings ViewModel

**File**: `feature/settings/ui/ReminderSettingsViewModel.kt`

```kotlin
@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(
    private val userPreferencesManager: UserPreferencesManager,
    private val usageLimitsManager: UsageLimitsManager,
    private val digestScheduler: DigestScheduler
) : ViewModel() {

    val smsEnabled = userPreferencesManager.smsRemindersEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val emailEnabled = userPreferencesManager.emailRemindersEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val dailyDigestEnabled = userPreferencesManager.dailyDigestEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val digestTime = userPreferencesManager.dailyDigestTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalTime.of(6, 0))
    
    val defaultReminderHours = userPreferencesManager.defaultReminderHours
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 24)
    
    private val _smsUsage = MutableStateFlow<SmsUsage?>(null)
    val smsUsage: StateFlow<SmsUsage?> = _smsUsage.asStateFlow()
    
    init {
        loadSmsUsage()
    }
    
    private fun loadSmsUsage() {
        viewModelScope.launch {
            _smsUsage.value = usageLimitsManager.getCurrentSmsUsage()
        }
    }
    
    fun setSmsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesManager.setSmsRemindersEnabled(enabled)
        }
    }
    
    fun setEmailEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesManager.setEmailRemindersEnabled(enabled)
        }
    }
    
    fun setDailyDigestEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesManager.setDailyDigestEnabled(enabled)
            if (enabled) {
                digestScheduler.scheduleDigest()
            } else {
                digestScheduler.cancelDigest()
            }
        }
    }
    
    fun setDigestTime(time: LocalTime) {
        viewModelScope.launch {
            userPreferencesManager.setDailyDigestTime(time)
            if (dailyDigestEnabled.value) {
                digestScheduler.scheduleDigest() // Reschedule with new time
            }
        }
    }
    
    fun setDefaultReminderHours(hours: Int) {
        viewModelScope.launch {
            userPreferencesManager.setDefaultReminderHours(hours)
        }
    }
}
```

### 8.4 Digest Time Picker

**User Action**: Taps "Digest Time" row

**System Response**: Shows time picker dialog

```kotlin
@Composable
fun DigestTimePicker(
    currentTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = currentTime.hour,
        initialMinute = currentTime.minute
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily Digest Time") },
        text = {
            TimePicker(state = timePickerState)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

### 8.5 Default Reminder Hours Picker

**Options**: 12 hours, 24 hours, 48 hours, 72 hours

**User Action**: Taps "Default Reminder Time" row

**System Response**: Shows selection dialog

```kotlin
@Composable
fun ReminderHoursDialog(
    currentHours: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(12, 24, 48, 72)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default Reminder Time") },
        text = {
            Column {
                options.forEach { hours ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(hours) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = hours == currentHours,
                            onClick = { onSelected(hours) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("$hours hours before")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

---

## 9. Confirmation Status in Appointment UI

### 9.1 Appointment Card with Confirmation Badge

**File**: `feature/appointments/ui/components/AppointmentCard.kt`

Add confirmation status display:

```kotlin
@Composable
fun ConfirmationStatusBadge(status: ConfirmationStatus) {
    val (icon, color, label) = when (status) {
        ConfirmationStatus.NOT_SENT -> Triple(
            Icons.Outlined.Circle,
            Color(0xFF9E9E9E),
            "Pending"
        )
        ConfirmationStatus.AWAITING -> Triple(
            Icons.Default.Schedule,
            Color(0xFFFF9800),
            "Awaiting"
        )
        ConfirmationStatus.CONFIRMED -> Triple(
            Icons.Default.CheckCircle,
            Color(0xFF4CAF50),
            "Confirmed"
        )
        ConfirmationStatus.DECLINED -> Triple(
            Icons.Default.Cancel,
            Color(0xFFF44336),
            "Declined"
        )
        ConfirmationStatus.NOT_REQUIRED -> Triple(
            Icons.Default.Remove,
            Color(0xFF757575),
            "No reminder"
        )
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
```

### 9.2 Appointment Detail - Confirmation Section

**File**: `feature/appointments/ui/AppointmentDetailScreen.kt`

```kotlin
@Composable
fun ConfirmationSection(
    appointment: AppointmentWithDetails,
    onResendReminder: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confirmation",
                    style = MaterialTheme.typography.titleSmall
                )
                ConfirmationStatusBadge(appointment.confirmationStatus)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (appointment.reminderSentAt != null) {
                Text(
                    text = "Reminder sent ${formatRelativeTime(appointment.reminderSentAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (appointment.confirmationReceivedAt != null) {
                Text(
                    text = "Response received ${formatRelativeTime(appointment.confirmationReceivedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Show resend option for certain statuses
            if (appointment.confirmationStatus in listOf(
                ConfirmationStatus.NOT_SENT,
                ConfirmationStatus.AWAITING
            )) {
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = onResendReminder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (appointment.confirmationStatus == ConfirmationStatus.NOT_SENT)
                            "Send Reminder Now"
                        else
                            "Resend Reminder"
                    )
                }
            }
            
            // Follow-up prompt for declined
            if (appointment.confirmationStatus == ConfirmationStatus.DECLINED) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Client requested to reschedule. Consider reaching out.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
```

### 9.3 Filter by Confirmation Status

**File**: `feature/appointments/ui/AppointmentListScreen.kt`

Add confirmation status filter:

```kotlin
enum class ConfirmationFilter {
    ALL,
    AWAITING,
    CONFIRMED,
    DECLINED
}

@Composable
fun ConfirmationFilterChips(
    selected: ConfirmationFilter,
    onSelect: (ConfirmationFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(ConfirmationFilter.values()) { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        when (filter) {
                            ConfirmationFilter.ALL -> "All"
                            ConfirmationFilter.AWAITING -> "Awaiting"
                            ConfirmationFilter.CONFIRMED -> "Confirmed"
                            ConfirmationFilter.DECLINED -> "Declined"
                        }
                    )
                }
            )
        }
    }
}
```

---

## 10. Data Models

### 10.1 Reminder Preference Enum

**File**: `core/domain/model/ReminderPreference.kt`

```kotlin
enum class ReminderPreference {
    SMS,
    EMAIL,
    BOTH,
    NONE
}
```

### 10.2 Updated Client Entity Fields

**File**: `core/database/entity/ClientEntity.kt`

Additional fields for reminders:
```kotlin
@ColumnInfo(name = "reminder_preference")
val reminderPreference: ReminderPreference = ReminderPreference.SMS,

@ColumnInfo(name = "reminder_hours")
val reminderHours: Int? = null // null = use default (24)
```

### 10.3 Updated Appointment Entity Fields

**File**: `core/database/entity/AppointmentEntity.kt`

Additional fields for confirmations:
```kotlin
@ColumnInfo(name = "confirmation_status")
val confirmationStatus: ConfirmationStatus = ConfirmationStatus.NOT_SENT,

@ColumnInfo(name = "reminder_sent_at")
val reminderSentAt: Instant? = null,

@ColumnInfo(name = "confirmation_received_at")
val confirmationReceivedAt: Instant? = null
```

### 10.4 SMS Usage Entity

**File**: `core/database/entity/SmsUsageEntity.kt`

```kotlin
@Entity(tableName = "sms_usage")
data class SmsUsageEntity(
    @PrimaryKey
    @ColumnInfo(name = "year_month")
    val yearMonth: String, // Format: "2025-01"
    
    @ColumnInfo(name = "count")
    val count: Int = 0,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now()
)
```

### 10.5 User Preferences DataStore

**File**: `core/datastore/UserPreferencesManager.kt`

Additional preferences:
```kotlin
val smsRemindersEnabled: Flow<Boolean>
val emailRemindersEnabled: Flow<Boolean>
val dailyDigestEnabled: Flow<Boolean>
val dailyDigestTime: Flow<LocalTime>
val defaultReminderHours: Flow<Int>

suspend fun setSmsRemindersEnabled(enabled: Boolean)
suspend fun setEmailRemindersEnabled(enabled: Boolean)
suspend fun setDailyDigestEnabled(enabled: Boolean)
suspend fun setDailyDigestTime(time: LocalTime)
suspend fun setDefaultReminderHours(hours: Int)
```

---

## 11. External Service Integration

### 11.1 Twilio SMS Service

**File**: `core/network/TwilioSmsService.kt`

```kotlin
interface TwilioSmsService {
    suspend fun sendSms(
        to: String,
        body: String,
        statusCallback: String? = null
    ): TwilioResponse
}

@Singleton
class TwilioSmsServiceImpl @Inject constructor(
    private val supabaseFunctions: SupabaseFunctionsClient
) : TwilioSmsService {
    
    override suspend fun sendSms(
        to: String,
        body: String,
        statusCallback: String?
    ): TwilioResponse {
        return supabaseFunctions.invoke(
            function = "send-sms",
            body = SendSmsRequest(
                to = to,
                body = body,
                statusCallback = statusCallback
            )
        )
    }
}
```

**Supabase Edge Function**: `functions/send-sms/index.ts`

```typescript
import twilio from "npm:twilio"

serve(async (req) => {
    const { to, body, statusCallback } = await req.json()
    
    const client = twilio(
        Deno.env.get("TWILIO_ACCOUNT_SID"),
        Deno.env.get("TWILIO_AUTH_TOKEN")
    )
    
    const message = await client.messages.create({
        to,
        from: Deno.env.get("TWILIO_PHONE_NUMBER"),
        body,
        statusCallback
    })
    
    return Response.json({ messageSid: message.sid })
})
```

### 11.2 SendGrid Email Service

**File**: `core/network/SendGridEmailService.kt`

```kotlin
interface SendGridEmailService {
    suspend fun sendEmail(
        to: String,
        subject: String,
        htmlBody: String,
        attachments: List<EmailAttachment> = emptyList()
    ): SendGridResponse
}

data class EmailAttachment(
    val filename: String,
    val contentType: String,
    val content: ByteArray
)
```

**Supabase Edge Function**: `functions/send-email/index.ts`

---

## 12. Error Handling

### 12.1 SMS Sending Errors

| Error | Handling |
|-------|----------|
| SmsLimitExceededException | Don't retry, show upgrade prompt |
| InvalidPhoneNumberException | Mark client phone as invalid, skip |
| TwilioRateLimitException | Retry with exponential backoff |
| NetworkException | Retry up to 3 times |
| TwilioAuthException | Log error, notify admin |

### 12.2 Email Sending Errors

| Error | Handling |
|-------|----------|
| InvalidEmailException | Mark client email as invalid, skip |
| SendGridRateLimitException | Retry with exponential backoff |
| NetworkException | Retry up to 3 times |

### 12.3 Webhook Processing Errors

| Error | Handling |
|-------|----------|
| Appointment not found | Return 200 OK (idempotent) |
| Invalid message format | Ignore, return 200 OK |
| Database error | Return 500, Twilio will retry |

---

## 13. Offline Behavior

### 13.1 Reminder Scheduling

- Reminders are scheduled via WorkManager with network constraint
- If offline when reminder is due, WorkManager waits for connectivity
- Reminder may be late but will still be sent when online

### 13.2 Confirmation Updates

- Confirmations come via push notification
- If offline, push is queued by FCM
- On reconnect, appointment syncs and confirmation status updates

### 13.3 SMS Usage

- SMS count is stored locally and synced
- Before sending, checks local count against limit
- After sending, increments local count and syncs

---

## 14. Performance Requirements

| Operation | Target | Measurement |
|-----------|--------|-------------|
| Schedule reminder | < 100ms | WorkManager enqueue time |
| Send SMS | < 3s | API call + response |
| Send email | < 5s | API call + response |
| Daily digest generation | < 500ms | Query + notification |
| SMS usage check | < 50ms | Local database query |

---

## 15. Acceptance Criteria

### Reminder Scheduling

| ID | Given | When | Then |
|----|-------|------|------|
| AC-008-01 | Client has SMS reminder preference with 24 hour timing | Appointment created for tomorrow at 10 AM | Reminder scheduled for today at 10 AM |
| AC-008-02 | Client has EMAIL reminder preference | Appointment created | Email reminder scheduled, not SMS |
| AC-008-03 | Client has BOTH reminder preference | Reminder time arrives | Both SMS and email sent |
| AC-008-04 | Client has NONE reminder preference | Appointment created | No reminder scheduled |
| AC-008-05 | Appointment is rescheduled to different day | User saves new date | Old reminder cancelled, new reminder scheduled |
| AC-008-06 | Appointment is cancelled | User cancels appointment | Reminder cancelled |
| AC-008-07 | Appointment already confirmed | Reminder time arrives | Reminder skipped |

### SMS Delivery

| ID | Given | When | Then |
|----|-------|------|------|
| AC-008-08 | Valid client phone number | SMS reminder sent | Client receives message with appointment details |
| AC-008-09 | SMS contains appointment date/time | Client views message | Date shows "tomorrow" or day name, time shows "8:00 AM" format |
| AC-008-10 | SMS contains horse names | Appointment has 4 horses | Message shows first 3 names + "+ 1 more" |
| AC-008-11 | SMS limit is 50, user has sent 50 | Reminder tries to send | SMS not sent, user notified of limit |
| AC-008-12 | SMS limit is 50, user has sent 40 | Reminder sends | SMS sent, count incremented to 41 |

### Confirmation Tracking

| ID | Given | When | Then |
|----|-------|------|------|
| AC-008-13 | Reminder sent, status is AWAITING | Client replies "YES" | Status changes to CONFIRMED, farrier receives notification |
| AC-008-14 | Reminder sent, status is AWAITING | Client replies "y" (lowercase) | Status changes to CONFIRMED |
| AC-008-15 | Reminder sent, status is AWAITING | Client replies "NO" | Status changes to DECLINED, farrier receives notification |
| AC-008-16 | Reminder sent, status is AWAITING | Client replies "Maybe later" | Status remains AWAITING (unrecognized) |
| AC-008-17 | No pending appointment for phone number | SMS received from unknown number | Webhook returns success, no action taken |

### Daily Digest

| ID | Given | When | Then |
|----|-------|------|------|
| AC-008-18 | Daily digest enabled, time set to 6 AM | Clock reaches 6 AM | Push notification with today's appointment summary |
| AC-008-19 | 5 appointments today, 3 confirmed, 2 awaiting | Digest generated | Notification shows "5 appointments (3 confirmed, 2 awaiting)" |
| AC-008-20 | No appointments today | Digest time arrives | No notification sent |
| AC-008-21 | Daily digest disabled | Digest time arrives | No notification sent |
| AC-008-22 | User taps digest notification | Notification opened | App opens to Schedule/Calendar screen |

### SMS Usage

| ID | Given | When | Then |
|----|-------|------|------|
| AC-008-23 | User on Solo tier (50 SMS limit), sent 38 | View Settings > Reminders | Shows "38/50 used" with 76% filled progress bar |
| AC-008-24 | User at 80% of SMS limit | View Settings > Reminders | Warning shown about approaching limit |
| AC-008-25 | User at 100% of SMS limit | Attempt to send reminder | Upgrade prompt shown, SMS not sent |
| AC-008-26 | New billing month starts | View usage | Counter reset to 0 |

### Reminder Settings

| ID | Given | When | Then |
|----|-------|------|------|
| AC-008-27 | SMS reminders enabled | User disables toggle | Future SMS reminders not sent (email still sent if enabled) |
| AC-008-28 | Digest time is 6 AM | User changes to 5:30 AM | Next digest scheduled for 5:30 AM |
| AC-008-29 | Default reminder hours is 24 | User changes to 48 | New clients default to 48-hour reminders |

---

## 16. File Reference Summary

### Core Files
- `core/reminder/ReminderScheduler.kt` - Schedules/cancels reminders
- `core/worker/ReminderWorker.kt` - Sends SMS/email reminders
- `core/worker/DailyDigestWorker.kt` - Generates daily digest
- `core/worker/DigestScheduler.kt` - Schedules daily digest
- `core/subscription/UsageLimitsManager.kt` - SMS usage tracking
- `core/notification/NotificationHelper.kt` - Shows notifications
- `core/network/TwilioSmsService.kt` - Twilio SMS API
- `core/network/SendGridEmailService.kt` - SendGrid email API

### Database
- `core/database/entity/SmsUsageEntity.kt` - SMS usage storage
- `core/database/dao/SmsUsageDao.kt` - SMS usage queries

### Feature Files
- `feature/settings/ui/ReminderSettingsScreen.kt` - Reminder settings UI
- `feature/settings/ui/ReminderSettingsViewModel.kt` - Settings logic
- `feature/settings/ui/SmsLimitBottomSheet.kt` - Upgrade prompt
- `feature/appointments/ui/components/ConfirmationStatusBadge.kt` - Status display

### Edge Functions
- `functions/send-sms/index.ts` - Twilio SMS sending
- `functions/send-email/index.ts` - SendGrid email sending
- `functions/twilio-sms-webhook/index.ts` - SMS reply handling
- `functions/send-push-notification/index.ts` - FCM push sending

---

## 17. Testing Scenarios

### Unit Tests

```kotlin
class ReminderSchedulerTest {
    @Test
    fun `scheduleReminder with NONE preference does not schedule`()
    
    @Test
    fun `scheduleReminder with past time does not schedule`()
    
    @Test
    fun `scheduleReminder calculates correct delay`()
    
    @Test
    fun `cancelReminder removes work from queue`()
}

class UsageLimitsManagerTest {
    @Test
    fun `canSendSms returns true when under limit`()
    
    @Test
    fun `canSendSms returns false when at limit`()
    
    @Test
    fun `getSmsWarningState returns Warning at 80 percent`()
    
    @Test
    fun `getSmsWarningState returns AtLimit at 100 percent`()
}
```

### Integration Tests

```kotlin
class ReminderWorkerTest {
    @Test
    fun `worker sends SMS for SMS preference`()
    
    @Test
    fun `worker sends email for EMAIL preference`()
    
    @Test
    fun `worker sends both for BOTH preference`()
    
    @Test
    fun `worker skips confirmed appointments`()
    
    @Test
    fun `worker fails when at SMS limit`()
}

class TwilioWebhookTest {
    @Test
    fun `YES reply confirms appointment`()
    
    @Test
    fun `NO reply declines appointment`()
    
    @Test
    fun `unknown reply is ignored`()
    
    @Test
    fun `unknown phone number returns success`()
}
```

---

## 18. Security Considerations

### 18.1 Phone Number Validation

- Validate phone format before sending SMS
- Store phone numbers in E.164 format (+15551234567)
- Never expose full phone numbers in logs

### 18.2 Webhook Security

- Validate Twilio request signature
- Use HTTPS for all webhook endpoints
- Rate limit webhook endpoint

### 18.3 SMS Content

- Never include sensitive information in SMS
- Keep message content minimal
- Don't include pricing in SMS (visible to anyone with phone)

### 18.4 Email Security

- Use verified sending domain
- Include unsubscribe link (CAN-SPAM compliance)
- Don't include auth tokens in email links

---

## 19. Future Considerations

### 19.1 Potential Enhancements

- Two-way SMS conversation
- Custom SMS templates
- Reminder preview before sending
- Batch reminder scheduling
- Analytics dashboard for reminder effectiveness

### 19.2 Not in Scope

- WhatsApp integration
- Voice call reminders
- Client-initiated scheduling via SMS
- Multiple language support
