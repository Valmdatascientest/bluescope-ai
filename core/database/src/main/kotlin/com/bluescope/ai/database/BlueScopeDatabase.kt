package com.bluescope.ai.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        DeviceEntity::class,
        DeviceAliasEntity::class,
        ScanSessionEntity::class,
        ObservationEntity::class,
        GattServiceEntity::class,
        GattCharacteristicEntity::class,
        GattDescriptorEntity::class,
        AnalysisEntity::class,
        ClaimEntity::class,
        EvidenceEntity::class,
        UserMetadataEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BlueScopeDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun scanDao(): ScanDao
    abstract fun observationDao(): ObservationDao
    abstract fun gattDao(): GattDao
    abstract fun analysisDao(): AnalysisDao
    abstract fun aliasDao(): AliasDao

    companion object {
        const val FILE_NAME = "bluescope.db"
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun build(context: Context): BlueScopeDatabase = Room.databaseBuilder(
            context.applicationContext,
            BlueScopeDatabase::class.java,
            FILE_NAME,
        ).addMigrations(*MIGRATIONS).build()
    }
}
