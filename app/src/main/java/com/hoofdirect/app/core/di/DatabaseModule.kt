package com.hoofdirect.app.core.di

import android.content.Context
import androidx.room.Room
import com.hoofdirect.app.core.database.HoofDirectDatabase
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): HoofDirectDatabase {
        return Room.databaseBuilder(
            context,
            HoofDirectDatabase::class.java,
            HoofDirectDatabase.DATABASE_NAME
        )
            .addMigrations(
                HoofDirectDatabase.MIGRATION_1_2,
                HoofDirectDatabase.MIGRATION_2_3,
                HoofDirectDatabase.MIGRATION_3_4
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideUserDao(database: HoofDirectDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    @Singleton
    fun provideClientDao(database: HoofDirectDatabase): ClientDao {
        return database.clientDao()
    }

    @Provides
    @Singleton
    fun provideHorseDao(database: HoofDirectDatabase): HorseDao {
        return database.horseDao()
    }

    @Provides
    @Singleton
    fun provideAppointmentDao(database: HoofDirectDatabase): AppointmentDao {
        return database.appointmentDao()
    }

    @Provides
    @Singleton
    fun provideInvoiceDao(database: HoofDirectDatabase): InvoiceDao {
        return database.invoiceDao()
    }

    @Provides
    @Singleton
    fun provideServicePriceDao(database: HoofDirectDatabase): ServicePriceDao {
        return database.servicePriceDao()
    }

    @Provides
    @Singleton
    fun provideSyncQueueDao(database: HoofDirectDatabase): SyncQueueDao {
        return database.syncQueueDao()
    }

    @Provides
    @Singleton
    fun provideMileageLogDao(database: HoofDirectDatabase): MileageLogDao {
        return database.mileageLogDao()
    }

    @Provides
    @Singleton
    fun provideSmsUsageDao(database: HoofDirectDatabase): SmsUsageDao {
        return database.smsUsageDao()
    }

    @Provides
    @Singleton
    fun provideRoutePlanDao(database: HoofDirectDatabase): RoutePlanDao {
        return database.routePlanDao()
    }
}
