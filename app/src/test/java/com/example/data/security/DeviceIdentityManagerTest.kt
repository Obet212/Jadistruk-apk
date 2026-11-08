package com.example.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceIdentityManagerTest {

  @Test
  fun testDeviceIdGenerationAndFormat() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = DeviceIdentityManager(context)

    val deviceId = manager.getOrCreateDeviceId()
    assertNotNull(deviceId)
    assertTrue("DeviceId must not be blank", deviceId.isNotBlank())

    // Verify UUID validity
    val parsedUuid = UUID.fromString(deviceId)
    assertEquals(deviceId, parsedUuid.toString())

    // Consecutive calls should return the identical persisted device ID
    val secondCallId = manager.getOrCreateDeviceId()
    assertEquals("Consecutive calls must return the same Device ID", deviceId, secondCallId)
  }

  @Test
  fun testRegenerateDeviceIdProducesNewValidId() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = DeviceIdentityManager(context)

    val initialId = manager.getOrCreateDeviceId()
    val newId = manager.regenerateDeviceId()

    assertNotNull(newId)
    assertTrue("Regenerated ID must not be blank", newId.isNotBlank())
    assertNotEquals("Regenerated ID must be different from initial ID", initialId, newId)

    val parsedNewUuid = UUID.fromString(newId)
    assertEquals(newId, parsedNewUuid.toString())

    // Consecutive call must match newId
    assertEquals(newId, manager.getOrCreateDeviceId())
  }
}
