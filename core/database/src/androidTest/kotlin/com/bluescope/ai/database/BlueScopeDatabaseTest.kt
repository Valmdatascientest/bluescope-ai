package com.bluescope.ai.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class BlueScopeDatabaseTest {
    private lateinit var database: BlueScopeDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BlueScopeDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deviceAndMetadata_areObservedTogether() = runBlocking {
        database.deviceDao().upsert(
            DeviceEntity(
                id = "device-1",
                displayName = "Capteur cuisine",
                advertisedName = "Sensor",
                transport = "BLE",
                category = "Capteur",
                manufacturerId = 76,
                manufacturerName = "Exemple",
                addressCiphertext = "encrypted",
                addressLookupHash = "hash",
                bondState = "NONE",
                deviceClass = null,
                appearance = 1344,
                latestRssi = -62,
                firstSeenAt = 1L,
                lastSeenAt = 2L,
                lastVerifiedAt = 2L,
            ),
        )
        database.deviceDao().upsertMetadata(
            UserMetadataEntity("device-1", isFavorite = true, note = "Cuisine", updatedAt = 3L),
        )

        val result = database.deviceDao().observeById("device-1").first()

        assertThat(result?.device?.displayName).isEqualTo("Capteur cuisine")
        assertThat(result?.metadata?.isFavorite).isTrue()
    }

    @Test
    fun deletingDevice_cascadesObservations() = runBlocking {
        database.scanDao().insert(ScanSessionEntity("scan-1", 1L, null, "BLE", "RUNNING", null))
        database.deviceDao().upsert(
            DeviceEntity(
                id = "device-1",
                displayName = null,
                advertisedName = null,
                transport = "BLE",
                category = null,
                manufacturerId = null,
                manufacturerName = null,
                addressCiphertext = null,
                addressLookupHash = null,
                bondState = "NONE",
                deviceClass = null,
                appearance = null,
                latestRssi = null,
                firstSeenAt = 1L,
                lastSeenAt = 1L,
                lastVerifiedAt = null,
            ),
        )
        database.observationDao().insert(
            ObservationEntity(
                deviceId = "device-1",
                sessionId = "scan-1",
                observedAt = 2L,
                transport = "BLE",
                rssi = -80,
                txPower = null,
                name = null,
                addressCiphertext = null,
                addressLookupHash = null,
                serviceUuidsJson = "[]",
                manufacturerDataJson = "{}",
                serviceDataJson = "{}",
                payloadFingerprint = null,
                connectable = null,
            ),
        )

        database.deviceDao().delete("device-1")

        assertThat(database.observationDao().count()).isEqualTo(0)
    }
}
