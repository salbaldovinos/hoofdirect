package com.hoofdirect.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,

    val email: String,

    @ColumnInfo(name = "email_verified")
    val emailVerified: Boolean = false,

    @ColumnInfo(name = "business_name")
    val businessName: String? = null,

    val phone: String? = null,

    val address: String? = null,

    @ColumnInfo(name = "home_latitude")
    val homeLatitude: Double? = null,

    @ColumnInfo(name = "home_longitude")
    val homeLongitude: Double? = null,

    @ColumnInfo(name = "service_radius_miles")
    val serviceRadiusMiles: Int = 50,

    @ColumnInfo(name = "default_duration_minutes")
    val defaultDurationMinutes: Int = 45,

    @ColumnInfo(name = "default_cycle_weeks")
    val defaultCycleWeeks: Int = 6,

    @ColumnInfo(name = "working_hours")
    val workingHours: String? = null, // JSON: WorkingHours

    @ColumnInfo(name = "payment_preferences")
    val paymentPreferences: String? = null, // JSON: PaymentPreferences

    @ColumnInfo(name = "subscription_tier")
    val subscriptionTier: String = "FREE",

    @ColumnInfo(name = "subscription_status")
    val subscriptionStatus: String? = null,

    @ColumnInfo(name = "stripe_customer_id")
    val stripeCustomerId: String? = null,

    @ColumnInfo(name = "stripe_subscription_id")
    val stripeSubscriptionId: String? = null,

    @ColumnInfo(name = "billing_period")
    val billingPeriod: String? = null,

    @ColumnInfo(name = "current_period_start")
    val currentPeriodStart: Instant? = null,

    @ColumnInfo(name = "current_period_end")
    val currentPeriodEnd: Instant? = null,

    @ColumnInfo(name = "trial_ends_at")
    val trialEndsAt: Instant? = null,

    @ColumnInfo(name = "cancel_at_period_end")
    val cancelAtPeriodEnd: Boolean = false,

    @ColumnInfo(name = "profile_photo_url")
    val profilePhotoUrl: String? = null,

    @ColumnInfo(name = "profile_completed")
    val profileCompleted: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "SYNCED"
)
