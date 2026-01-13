package com.hoofdirect.app.feature.appointment.data

import com.hoofdirect.app.core.database.entity.AppointmentEntity
import com.hoofdirect.app.core.database.entity.AppointmentHorseEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AppointmentDto(
    val id: String,
    val user_id: String,
    val client_id: String,
    val date: String,
    val start_time: String,
    val end_time: String? = null,
    val duration_minutes: Int = 45,
    val status: String = "SCHEDULED",
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String? = null,
    val total_price: String = "0.00",
    val reminder_sent: Boolean = false,
    val confirmation_received: Boolean = false,
    val completed_at: String? = null,
    val cancelled_at: String? = null,
    val cancellation_reason: String? = null,
    val route_order: Int? = null,
    val travel_time_minutes: Int? = null,
    val travel_distance_miles: Double? = null,
    val created_at: String? = null,
    val updated_at: String? = null
) {
    fun toEntity(): AppointmentEntity = AppointmentEntity(
        id = id,
        userId = user_id,
        clientId = client_id,
        date = LocalDate.parse(date),
        startTime = LocalTime.parse(start_time),
        endTime = end_time?.let { LocalTime.parse(it) },
        durationMinutes = duration_minutes,
        status = status,
        address = address,
        latitude = latitude,
        longitude = longitude,
        notes = notes,
        totalPrice = total_price,
        reminderSent = reminder_sent,
        confirmationReceived = confirmation_received,
        completedAt = completed_at?.let { Instant.parse(it) },
        cancelledAt = cancelled_at?.let { Instant.parse(it) },
        cancellationReason = cancellation_reason,
        routeOrder = route_order,
        travelTimeMinutes = travel_time_minutes,
        travelDistanceMiles = travel_distance_miles,
        createdAt = created_at?.let { Instant.parse(it) } ?: Instant.now(),
        updatedAt = updated_at?.let { Instant.parse(it) } ?: Instant.now(),
        syncStatus = "SYNCED"
    )

    companion object {
        fun fromEntity(entity: AppointmentEntity): AppointmentDto = AppointmentDto(
            id = entity.id,
            user_id = entity.userId,
            client_id = entity.clientId,
            date = entity.date.toString(),
            start_time = entity.startTime.toString(),
            end_time = entity.endTime?.toString(),
            duration_minutes = entity.durationMinutes,
            status = entity.status,
            address = entity.address,
            latitude = entity.latitude,
            longitude = entity.longitude,
            notes = entity.notes,
            total_price = entity.totalPrice,
            reminder_sent = entity.reminderSent,
            confirmation_received = entity.confirmationReceived,
            completed_at = entity.completedAt?.toString(),
            cancelled_at = entity.cancelledAt?.toString(),
            cancellation_reason = entity.cancellationReason,
            route_order = entity.routeOrder,
            travel_time_minutes = entity.travelTimeMinutes,
            travel_distance_miles = entity.travelDistanceMiles,
            created_at = entity.createdAt.toString(),
            updated_at = entity.updatedAt.toString()
        )
    }
}

@Serializable
data class AppointmentHorseDto(
    val appointment_id: String,
    val horse_id: String,
    val service_type: String,
    val price: String = "0.00",
    val notes: String? = null,
    val created_at: String? = null
) {
    fun toEntity(): AppointmentHorseEntity = AppointmentHorseEntity(
        appointmentId = appointment_id,
        horseId = horse_id,
        serviceType = service_type,
        price = price,
        notes = notes,
        createdAt = created_at?.let { Instant.parse(it) } ?: Instant.now()
    )

    companion object {
        fun fromEntity(entity: AppointmentHorseEntity): AppointmentHorseDto = AppointmentHorseDto(
            appointment_id = entity.appointmentId,
            horse_id = entity.horseId,
            service_type = entity.serviceType,
            price = entity.price,
            notes = entity.notes,
            created_at = entity.createdAt.toString()
        )
    }
}

@Singleton
class AppointmentRemoteDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val appointmentsTable = "appointments"
    private val appointmentHorsesTable = "appointment_horses"

    suspend fun fetchAllAppointments(userId: String): Result<List<AppointmentEntity>> {
        return try {
            val response = supabaseClient.postgrest[appointmentsTable]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<AppointmentDto>()

            Result.success(response.map { it.toEntity() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAppointmentsForDate(
        userId: String,
        date: LocalDate
    ): Result<List<AppointmentEntity>> {
        return try {
            val response = supabaseClient.postgrest[appointmentsTable]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("date", date.toString())
                    }
                }
                .decodeList<AppointmentDto>()

            Result.success(response.map { it.toEntity() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAppointment(appointmentId: String): Result<AppointmentEntity?> {
        return try {
            val response = supabaseClient.postgrest[appointmentsTable]
                .select {
                    filter {
                        eq("id", appointmentId)
                    }
                }
                .decodeSingleOrNull<AppointmentDto>()

            Result.success(response?.toEntity())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createAppointment(
        appointment: AppointmentEntity,
        horses: List<AppointmentHorseEntity>
    ): Result<AppointmentEntity> {
        return try {
            // Insert appointment
            val appointmentDto = AppointmentDto.fromEntity(appointment)
            supabaseClient.postgrest[appointmentsTable]
                .insert(appointmentDto)

            // Insert appointment horses
            if (horses.isNotEmpty()) {
                val horseDtos = horses.map { AppointmentHorseDto.fromEntity(it) }
                supabaseClient.postgrest[appointmentHorsesTable]
                    .insert(horseDtos)
            }

            Result.success(appointment)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAppointment(
        appointment: AppointmentEntity,
        horses: List<AppointmentHorseEntity>?
    ): Result<AppointmentEntity> {
        return try {
            // Update appointment
            val dto = AppointmentDto.fromEntity(appointment)
            supabaseClient.postgrest[appointmentsTable]
                .update(dto) {
                    filter {
                        eq("id", appointment.id)
                    }
                }

            // Update horses if provided
            if (horses != null) {
                // Delete existing horses
                supabaseClient.postgrest[appointmentHorsesTable]
                    .delete {
                        filter {
                            eq("appointment_id", appointment.id)
                        }
                    }

                // Insert new horses
                if (horses.isNotEmpty()) {
                    val horseDtos = horses.map { AppointmentHorseDto.fromEntity(it) }
                    supabaseClient.postgrest[appointmentHorsesTable]
                        .insert(horseDtos)
                }
            }

            Result.success(appointment)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAppointment(appointmentId: String): Result<Unit> {
        return try {
            // Horses are deleted via cascade
            supabaseClient.postgrest[appointmentsTable]
                .delete {
                    filter {
                        eq("id", appointmentId)
                    }
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAppointmentHorses(appointmentId: String): Result<List<AppointmentHorseEntity>> {
        return try {
            val response = supabaseClient.postgrest[appointmentHorsesTable]
                .select {
                    filter {
                        eq("appointment_id", appointmentId)
                    }
                }
                .decodeList<AppointmentHorseDto>()

            Result.success(response.map { it.toEntity() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
