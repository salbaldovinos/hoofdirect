package com.hoofdirect.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hoofdirect.app.core.database.dao.AppointmentDao
import com.hoofdirect.app.core.database.dao.ClientDao
import com.hoofdirect.app.core.database.dao.HorseDao
import com.hoofdirect.app.core.database.dao.InvoiceDao
import com.hoofdirect.app.core.database.dao.MileageLogDao
import com.hoofdirect.app.core.database.dao.RoutePlanDao
import com.hoofdirect.app.core.database.dao.ServicePriceDao
import com.hoofdirect.app.core.database.dao.SmsUsageDao
import com.hoofdirect.app.core.database.dao.SyncQueueDao
import com.hoofdirect.app.core.database.dao.UserDao
import com.hoofdirect.app.core.database.entity.AppointmentEntity
import com.hoofdirect.app.core.database.entity.AppointmentHorseEntity
import com.hoofdirect.app.core.database.entity.ClientEntity
import com.hoofdirect.app.core.database.entity.HorseEntity
import com.hoofdirect.app.core.database.entity.InvoiceEntity
import com.hoofdirect.app.core.database.entity.InvoiceItemEntity
import com.hoofdirect.app.core.database.entity.MileageLogEntity
import com.hoofdirect.app.core.database.entity.RoutePlanEntity
import com.hoofdirect.app.core.database.entity.ServicePriceEntity
import com.hoofdirect.app.core.database.entity.SmsUsageEntity
import com.hoofdirect.app.core.database.entity.SyncQueueEntity
import com.hoofdirect.app.core.database.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        ClientEntity::class,
        HorseEntity::class,
        AppointmentEntity::class,
        AppointmentHorseEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        ServicePriceEntity::class,
        SyncQueueEntity::class,
        MileageLogEntity::class,
        SmsUsageEntity::class,
        RoutePlanEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HoofDirectDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun clientDao(): ClientDao
    abstract fun horseDao(): HorseDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun servicePriceDao(): ServicePriceDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun mileageLogDao(): MileageLogDao
    abstract fun smsUsageDao(): SmsUsageDao
    abstract fun routePlanDao(): RoutePlanDao

    companion object {
        const val DATABASE_NAME = "hoof_direct_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add mileage_logs table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS mileage_logs (
                        id TEXT PRIMARY KEY NOT NULL,
                        user_id TEXT NOT NULL,
                        date TEXT NOT NULL,
                        start_address TEXT,
                        end_address TEXT,
                        miles REAL NOT NULL,
                        purpose TEXT NOT NULL DEFAULT 'CLIENT_VISIT',
                        appointment_id TEXT,
                        notes TEXT,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        sync_status TEXT NOT NULL DEFAULT 'SYNCED'
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mileage_logs_user_id ON mileage_logs(user_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mileage_logs_date ON mileage_logs(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mileage_logs_appointment_id ON mileage_logs(appointment_id)")

                // Add columns to service_prices if they don't exist
                try {
                    db.execSQL("ALTER TABLE service_prices ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 45")
                } catch (e: Exception) {
                    // Column may already exist
                }
                try {
                    db.execSQL("ALTER TABLE service_prices ADD COLUMN is_built_in INTEGER NOT NULL DEFAULT 1")
                } catch (e: Exception) {
                    // Column may already exist
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add subscription columns to users table
                db.execSQL("ALTER TABLE users ADD COLUMN subscription_status TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN stripe_customer_id TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN stripe_subscription_id TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN billing_period TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN current_period_start INTEGER")
                db.execSQL("ALTER TABLE users ADD COLUMN current_period_end INTEGER")
                db.execSQL("ALTER TABLE users ADD COLUMN trial_ends_at INTEGER")
                db.execSQL("ALTER TABLE users ADD COLUMN cancel_at_period_end INTEGER NOT NULL DEFAULT 0")

                // Create sms_usage table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sms_usage (
                        id TEXT PRIMARY KEY NOT NULL,
                        user_id TEXT NOT NULL,
                        year_month TEXT NOT NULL,
                        sms_count INTEGER NOT NULL DEFAULT 0,
                        last_sent_at INTEGER,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sms_usage_user_id ON sms_usage(user_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sms_usage_year_month ON sms_usage(year_month)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sms_usage_user_id_year_month ON sms_usage(user_id, year_month)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create route_plans table for route optimization
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS route_plans (
                        id TEXT PRIMARY KEY NOT NULL,
                        user_id TEXT NOT NULL,
                        date TEXT NOT NULL,
                        start_lat REAL NOT NULL,
                        start_lng REAL NOT NULL,
                        start_name TEXT NOT NULL,
                        end_lat REAL NOT NULL,
                        end_lng REAL NOT NULL,
                        end_name TEXT NOT NULL,
                        stops TEXT NOT NULL,
                        polyline_points TEXT,
                        total_distance_miles REAL NOT NULL,
                        total_drive_minutes INTEGER NOT NULL,
                        original_distance_miles REAL,
                        original_drive_minutes INTEGER,
                        optimized_at INTEGER NOT NULL,
                        is_manually_reordered INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        sync_status TEXT NOT NULL DEFAULT 'SYNCED'
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_route_plans_user_id ON route_plans(user_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_route_plans_date ON route_plans(date)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_route_plans_user_id_date ON route_plans(user_id, date)")
            }
        }
    }
}
