package com.bluescope.ai.di

import android.content.Context
import com.bluescope.ai.ai.OfflineIdentificationEngine
import com.bluescope.ai.bluetooth.gatt.AndroidGattExplorer
import com.bluescope.ai.bluetooth.scanner.AndroidBluetoothScanner
import com.bluescope.ai.data.preferences.DataStorePreferencesRepository
import com.bluescope.ai.data.repository.RoomDeviceRepository
import com.bluescope.ai.data.security.AndroidIdentifierProtector
import com.bluescope.ai.data.security.IdentifierProtector
import com.bluescope.ai.database.BlueScopeDatabase
import com.bluescope.ai.domain.repository.BluetoothScanner
import com.bluescope.ai.domain.repository.DeviceRepository
import com.bluescope.ai.domain.repository.GattExplorer
import com.bluescope.ai.domain.repository.IdentificationEngine
import com.bluescope.ai.domain.repository.PreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BlueScopeDatabase =
        BlueScopeDatabase.build(context)

    @Provides
    @Singleton
    fun provideIdentifierProtector(): IdentifierProtector = AndroidIdentifierProtector()

    @Provides
    @Singleton
    fun provideDeviceRepository(
        database: BlueScopeDatabase,
        protector: IdentifierProtector,
    ): DeviceRepository = RoomDeviceRepository(database, protector)

    @Provides
    @Singleton
    fun providePreferencesRepository(
        @ApplicationContext context: Context,
    ): PreferencesRepository = DataStorePreferencesRepository(context)

    @Provides
    @Singleton
    fun provideBluetoothScanner(@ApplicationContext context: Context): BluetoothScanner =
        AndroidBluetoothScanner(context)

    @Provides
    @Singleton
    fun provideGattExplorer(
        @ApplicationContext context: Context,
        scanner: BluetoothScanner,
    ): GattExplorer = AndroidGattExplorer(context, scanner)

    @Provides
    @Singleton
    fun provideIdentificationEngine(): IdentificationEngine = OfflineIdentificationEngine()
}
