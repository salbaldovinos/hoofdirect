package com.hoofdirect.app.feature.horse.data

import com.hoofdirect.app.core.database.entity.HorseEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class HorseDto(
    val id: String,
    val user_id: String,
    val client_id: String,
    val name: String,
    val breed: String? = null,
    val color: String? = null,
    val age: Int? = null,
    val temperament: String? = null,
    val default_service_type: String? = null,
    val shoeing_cycle_weeks: Int? = null,
    val last_service_date: String? = null,
    val next_due_date: String? = null,
    val medical_notes: String? = null,
    val primary_photo_id: String? = null,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val updated_at: String? = null
) {
    fun toEntity(): HorseEntity = HorseEntity(
        id = id,
        userId = user_id,
        clientId = client_id,
        name = name,
        breed = breed,
        color = color,
        age = age,
        temperament = temperament,
        defaultServiceType = default_service_type,
        shoeingCycleWeeks = shoeing_cycle_weeks,
        lastServiceDate = last_service_date?.let { LocalDate.parse(it) },
        nextDueDate = next_due_date?.let { LocalDate.parse(it) },
        medicalNotes = medical_notes,
        primaryPhotoId = primary_photo_id,
        isActive = is_active,
        createdAt = created_at?.let { Instant.parse(it) } ?: Instant.now(),
        updatedAt = updated_at?.let { Instant.parse(it) } ?: Instant.now(),
        syncStatus = "SYNCED"
    )

    companion object {
        fun fromEntity(entity: HorseEntity): HorseDto = HorseDto(
            id = entity.id,
            user_id = entity.userId,
            client_id = entity.clientId,
            name = entity.name,
            breed = entity.breed,
            color = entity.color,
            age = entity.age,
            temperament = entity.temperament,
            default_service_type = entity.defaultServiceType,
            shoeing_cycle_weeks = entity.shoeingCycleWeeks,
            last_service_date = entity.lastServiceDate?.toString(),
            next_due_date = entity.nextDueDate?.toString(),
            medical_notes = entity.medicalNotes,
            primary_photo_id = entity.primaryPhotoId,
            is_active = entity.isActive,
            created_at = entity.createdAt.toString(),
            updated_at = entity.updatedAt.toString()
        )
    }
}

@Singleton
class HorseRemoteDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val tableName = "horses"

    suspend fun fetchAllHorses(userId: String): Result<List<HorseEntity>> {
        return try {
            val response = supabaseClient.postgrest[tableName]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<HorseDto>()

            Result.success(response.map { it.toEntity() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchHorsesForClient(clientId: String): Result<List<HorseEntity>> {
        return try {
            val response = supabaseClient.postgrest[tableName]
                .select {
                    filter {
                        eq("client_id", clientId)
                    }
                }
                .decodeList<HorseDto>()

            Result.success(response.map { it.toEntity() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchHorse(horseId: String): Result<HorseEntity?> {
        return try {
            val response = supabaseClient.postgrest[tableName]
                .select {
                    filter {
                        eq("id", horseId)
                    }
                }
                .decodeSingleOrNull<HorseDto>()

            Result.success(response?.toEntity())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createHorse(horse: HorseEntity): Result<HorseEntity> {
        return try {
            val dto = HorseDto.fromEntity(horse)
            supabaseClient.postgrest[tableName]
                .insert(dto)

            Result.success(horse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateHorse(horse: HorseEntity): Result<HorseEntity> {
        return try {
            val dto = HorseDto.fromEntity(horse)
            supabaseClient.postgrest[tableName]
                .update(dto) {
                    filter {
                        eq("id", horse.id)
                    }
                }

            Result.success(horse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteHorse(horseId: String): Result<Unit> {
        return try {
            supabaseClient.postgrest[tableName]
                .delete {
                    filter {
                        eq("id", horseId)
                    }
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
