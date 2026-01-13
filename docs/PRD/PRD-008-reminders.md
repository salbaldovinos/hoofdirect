# PRD-008: Reminders & Confirmations

**Priority**: P1  
**Phase**: 2 - Scheduling Core  
**Estimated Duration**: 1 week

---

## Overview

### Purpose
Automate appointment reminders via SMS and email, with confirmation tracking to reduce no-shows and improve client communication.

### Business Value
- Reduces no-shows by 30%+
- Saves time on manual reminder calls
- Tracks confirmation status
- Professional client communication

### Success Metrics
| Metric | Target |
|--------|--------|
| Reminder delivery rate | > 98% |
| Confirmation response rate | > 60% |
| No-show reduction | > 30% |
| SMS cost per reminder | < $0.02 |

---

## User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|--------------|------------|----------|
| US-008-01 | Farrier | Send automatic reminders | Clients don't forget | P0 |
| US-008-02 | Farrier | Configure reminder timing | Some clients need more notice | P1 |
| US-008-03 | Farrier | Track confirmations | I know who's ready | P0 |
| US-008-04 | Client | Confirm via SMS reply | I confirm easily | P0 |
| US-008-05 | Farrier | See who hasn't confirmed | I can follow up | P1 |
| US-008-06 | Farrier | Get morning digest | I see my day at a glance | P1 |
| US-008-07 | Farrier | Control SMS usage | I manage costs | P1 |

---

## Functional Requirements

### FR-008-01: Reminder Scheduling
- Default: 24 hours before appointment
- Configurable per client: 12hr, 24hr, 48hr, 72hr, none
- Schedule via WorkManager
- Skip if appointment already confirmed
- Skip if appointment cancelled

### FR-008-02: SMS Reminders (Twilio)
```
From: Hoof Direct
To: +1 (555) 123-4567

Hi Sarah! Reminder: Your farrier appointment is tomorrow 
(Jan 20) at 8:00 AM.

Horses: Midnight, Dusty
Location: Johnson Ranch

Reply YES to confirm or call (555) 987-6543 to reschedule.
```

### FR-008-03: Email Reminders
- HTML template with branding
- Include all appointment details
- One-click confirm button (deep link)
- Calendar attachment (.ics file)
- Unsubscribe link

### FR-008-04: Confirmation Tracking
```kotlin
enum class ConfirmationStatus {
    AWAITING,    // Reminder sent, no response
    CONFIRMED,   // Client replied YES or clicked confirm
    DECLINED,    // Client declined or requested reschedule
    NOT_REQUIRED // Client preference is no confirmation
}
```

### FR-008-05: SMS Reply Handling
- Twilio webhook to Edge Function
- Parse replies: YES, CONFIRM, Y → Confirmed
- Parse replies: NO, CANCEL → Declined (flag for follow-up)
- Update appointment confirmation status
- Push notification to farrier

### FR-008-06: Daily Digest
- Push notification at configurable time (default 6 AM)
- Summary:
  - Appointment count
  - Confirmed vs. unconfirmed
  - First appointment time
  - Estimated total time
- Tap opens Schedule screen

### FR-008-07: SMS Usage Tracking
- Count against tier limit
- Soft warning at 80%
- Hard stop at 100% (show upgrade prompt)
- Usage visible in settings

---

## Technical Implementation

```kotlin
// ReminderScheduler.kt
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appointmentDao: AppointmentDao
) {
    private val workManager = WorkManager.getInstance(context)
    
    fun scheduleReminder(appointment: Appointment) {
        if (appointment.reminderPreference == ReminderPreference.NONE) return
        
        val reminderTime = appointment.dateTime
            .minusHours(appointment.reminderHoursBefore.toLong())
        
        val delay = Duration.between(Instant.now(), reminderTime)
        if (delay.isNegative) return // Already past reminder time
        
        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                "appointment_id" to appointment.id,
                "client_phone" to appointment.clientPhone,
                "client_email" to appointment.clientEmail
            ))
            .addTag("reminder_${appointment.id}")
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
}

// ReminderWorker.kt
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val twilioService: TwilioService,
    private val emailService: EmailService,
    private val appointmentDao: AppointmentDao,
    private val usageLimits: UsageLimitsManager
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val appointmentId = inputData.getString("appointment_id") ?: return Result.failure()
        val appointment = appointmentDao.getById(appointmentId) ?: return Result.failure()
        
        // Skip if already confirmed or cancelled
        if (appointment.status == AppointmentStatus.CONFIRMED ||
            appointment.status == AppointmentStatus.CANCELLED) {
            return Result.success()
        }
        
        val clientPhone = inputData.getString("client_phone")
        val clientEmail = inputData.getString("client_email")
        
        return try {
            when (appointment.reminderPreference) {
                ReminderPreference.SMS -> sendSmsReminder(appointment, clientPhone!!)
                ReminderPreference.EMAIL -> sendEmailReminder(appointment, clientEmail!!)
                ReminderPreference.BOTH -> {
                    sendSmsReminder(appointment, clientPhone!!)
                    sendEmailReminder(appointment, clientEmail!!)
                }
                else -> {}
            }
            
            appointmentDao.updateReminderSent(appointmentId, Instant.now())
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
    
    private suspend fun sendSmsReminder(appointment: AppointmentEntity, phone: String) {
        if (!usageLimits.canSendSms()) {
            throw SmsLimitExceededException()
        }
        
        val message = buildSmsMessage(appointment)
        twilioService.sendSms(
            to = phone,
            body = message,
            statusCallback = "${BuildConfig.API_BASE_URL}/webhooks/twilio/status"
        )
        usageLimits.incrementSmsCount()
    }
    
    private fun buildSmsMessage(appointment: AppointmentEntity): String {
        return """
            Hi ${appointment.clientFirstName}! Reminder: Your farrier appointment is ${appointment.formattedDate} at ${appointment.formattedTime}.
            
            Horses: ${appointment.horseSummary}
            Location: ${appointment.locationName}
            
            Reply YES to confirm or call ${appointment.farrierPhone} to reschedule.
        """.trimIndent()
    }
}

// TwilioWebhookHandler.kt (Edge Function)
suspend fun handleTwilioWebhook(request: TwilioWebhookRequest) {
    val from = request.from
    val body = request.body.uppercase().trim()
    
    // Find appointment by phone number
    val appointment = appointmentDao.findPendingByClientPhone(from)
        ?: return
    
    val status = when {
        body in listOf("YES", "Y", "CONFIRM", "CONFIRMED") -> ConfirmationStatus.CONFIRMED
        body in listOf("NO", "N", "CANCEL") -> ConfirmationStatus.DECLINED
        else -> return // Unknown response, ignore
    }
    
    appointmentDao.updateConfirmationStatus(
        id = appointment.id,
        status = status,
        confirmedAt = Instant.now()
    )
    
    // Send push notification to farrier
    pushNotificationService.send(
        userId = appointment.userId,
        title = if (status == ConfirmationStatus.CONFIRMED) "Appointment Confirmed" else "Appointment Declined",
        body = "${appointment.clientName} ${if (status == ConfirmationStatus.CONFIRMED) "confirmed" else "declined"} tomorrow's appointment"
    )
}

// DailyDigestWorker.kt
@HiltWorker
class DailyDigestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val appointmentDao: AppointmentDao,
    private val notificationManager: NotificationManagerCompat
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        val appointments = appointmentDao.getAppointmentsForDate(today)
        
        if (appointments.isEmpty()) return Result.success()
        
        val confirmed = appointments.count { it.confirmationStatus == ConfirmationStatus.CONFIRMED }
        val total = appointments.size
        val firstTime = appointments.minOf { it.time }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_DIGEST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Today's Schedule")
            .setContentText("$total appointments ($confirmed confirmed). First at $firstTime")
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_DIGEST, notification)
        return Result.success()
    }
}
```

---

## Data Model

```kotlin
enum class ReminderPreference {
    SMS, EMAIL, BOTH, NONE
}

enum class ConfirmationStatus {
    AWAITING,
    CONFIRMED,
    DECLINED,
    NOT_REQUIRED
}

data class SmsUsage(
    val used: Int,
    val limit: Int,
    val resetDate: LocalDate
) {
    val remaining: Int get() = limit - used
    val percentUsed: Float get() = used.toFloat() / limit
    val isAtLimit: Boolean get() = used >= limit
}
```

---

## UI Specifications

### Confirmation Status in Appointment Card
```
┌─────────────────────────────────────────┐
│  8:00 AM                    ✓ Confirmed │
│  Johnson Ranch                          │
│  3 horses · Full set, 2 trims          │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│  10:30 AM                    ⏳ Awaiting │
│  Williams Farm                          │
│  2 horses · Trims                      │
└─────────────────────────────────────────┘
```

### SMS Usage in Settings
```
┌─────────────────────────────────────────┐
│  SMS Usage                              │
│  ─────────────────────────────────────  │
│  ████████████░░░░░░░░  38/50 used      │
│  Resets Feb 1                          │
│                                         │
│  [Upgrade for More SMS]                │
└─────────────────────────────────────────┘
```

---

## Testing Requirements

```kotlin
class ReminderSchedulerTest {
    @Test
    fun `scheduleReminder creates work request`() = runTest {
        scheduler.scheduleReminder(appointment)
        
        val workInfo = workManager.getWorkInfosForUniqueWork("reminder_${appointment.id}")
            .await()
        assertEquals(1, workInfo.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfo[0].state)
    }
}

class TwilioWebhookTest {
    @Test
    fun `YES reply confirms appointment`() = runTest {
        handler.handleTwilioWebhook(TwilioWebhookRequest(
            from = "+15551234567",
            body = "YES"
        ))
        
        val apt = appointmentDao.getById(appointmentId)
        assertEquals(ConfirmationStatus.CONFIRMED, apt.confirmationStatus)
    }
}
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-008-01 | Reminders sent at configured time | Integration test |
| AC-008-02 | SMS delivery succeeds | Manual test |
| AC-008-03 | YES reply confirms appointment | Integration test |
| AC-008-04 | Usage tracked against limit | Unit test |
| AC-008-05 | Daily digest received | Manual test |
| AC-008-06 | Hard stop at SMS limit | Integration test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-005 (Appointments) | Internal | Required |
| Twilio API | External | Required |
| Supabase Edge Functions | External | Required |
| Firebase Cloud Messaging | External | Required |

---

## Cost Considerations

| Service | Cost | Monthly Estimate (Solo tier) |
|---------|------|------------------------------|
| Twilio SMS | $0.0079/msg | $0.40 (50 msgs) |
| Twilio Phone Number | $1.15/mo | $1.15 |
| SendGrid Email | Free tier | $0 |
| **Total** | | **~$1.55/user** |
