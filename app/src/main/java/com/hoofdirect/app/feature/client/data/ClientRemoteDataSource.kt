package com.hoofdirect.app.feature.client.data

import com.hoofdirect.app.core.database.entity.ClientEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ClientDto(
    val id: String,
    val user_id: String,
    val name: String,
    val first_name: String,
    val last_name: String? = null,
    val business_name: String? = null,
    val phone: String,
    val email: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val state: String? = null,
    val zip_code: String? = null,
    val access_notes: String? = null,
    val notes: String? = null,
    val custom_prices: String? = null,
    val reminder_preference: String = "SMS",
    val reminder_hours: Int = 24,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val updated_at: String? = null
) {
    fun toEntity(): ClientEntity = ClientEntity(
        id = id,
        userId = user_id,
        name = name,
        firstName = first_name,
        lastName = last_name,
        businessName = business_name,
        phone = phone,
        email = email,
        address = address,
        latitude = latitude,
        longitude = longitude,
        city = city,
        state = state,
        zipCode = zip_code,
        accessNotes = access_notes,
        notes = notes,
        customPrices = custom_prices,
        reminderPreference = reminder_preference,
        reminderHours = reminder_hours,
        isActive = is_active,
        createdAt = created_at?.let { parseSupabaseTimestamp(it) } ?: Instant.now(),
        updatedAt = updated_at?.let { parseSupabaseTimestamp(it) } ?: Instant.now(),
        syncStatus = "SYNCED"
    )

    companion object {
        private fun parseSupabaseTimestamp(timestamp: String): Instant {
            return try {
                Instant.parse(timestamp)
            } catch (e: DateTimeParseException) {
                try {
                    OffsetDateTime.parse(timestamp).toInstant()
                } catch (e2: DateTimeParseException) {
                    Instant.now()
                }
            }
        }

        fun fromEntity(entity: ClientEntity): ClientDto = ClientDto(
            id = entity.id,
            user_id = entity.userId,
            name = entity.name,
            first_name = entity.firstName,
            last_name = entity.lastName,
            business_name = entity.businessName,
            phone = entity.phone,
            email = entity.email,
            address = entity.address,
            latitude = entity.latitude,
            longitude = entity.longitude,
            city = entity.city,
            state = entity.state,
            zip_code = entity.zipCode,
            access_notes = entity.accessNotes,
            notes = entity.notes,
            custom_prices = entity.customPrices,
            reminder_preference = entity.reminderPreference,
            reminder_hours = entity.reminderHours,
            is_active = entity.isActive,
            created_at = entity.createdAt.toString(),
            updated_at = entity.updatedAt.toString()
        )
    }
}

@Singleton
class ClientRemoteDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val tableName = "clients"

    suspend fun fetchAllClients(userId: String): Result<List<ClientEntity>> {
        return try {
            val response = supabaseClient.postgrest[tableName]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<ClientDto>()

            Result.success(response.map { it.toEntity() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchClient(clientId: String): Result<ClientEntity?> {
        return try {
            val response = supabaseClient.postgrest[tableName]
                .select {
                    filter {
                        eq("id", clientId)
                    }
                }
                .decodeSingleOrNull<ClientDto>()

            Result.success(response?.toEntity())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createClient(client: ClientEntity): Result<ClientEntity> {
        return try {
            val dto = ClientDto.fromEntity(client)
            supabaseClient.postgrest[tableName]
                .insert(dto)

            Result.success(client)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateClient(client: ClientEntity): Result<ClientEntity> {
        return try {
            val dto = ClientDto.fromEntity(client)
            supabaseClient.postgrest[tableName]
                .update(dto) {
                    filter {
                        eq("id", client.id)
                    }
                }

            Result.success(client)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteClient(clientId: String): Result<Unit> {
        return try {
            supabaseClient.postgrest[tableName]
                .delete {
                    filter {
                        eq("id", clientId)
                    }
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchClientsSince(userId: String, since: Instant): Result<List<ClientEntity>> {
        return try {
            val response = supabaseClient.postgrest[tableName]
                .select {
                    filter {
                        eq("user_id", userId)
                        gte("updated_at", since.toString())
                    }
                }
                .decodeList<ClientDto>()

            Result.success(response.map { it.toEntity() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
