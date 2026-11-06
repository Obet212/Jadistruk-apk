package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

  @Test
  fun testVersionBelowMinimumCases() {
    // Current is 1.5, Minimum required is 1.6 -> Must update (true)
    assertTrue(VersionComparator.isVersionBelowMinimum("1.5", "1.6"))

    // Current is 1.5, Minimum required is 2.0 -> Must update (true)
    assertTrue(VersionComparator.isVersionBelowMinimum("1.5", "2.0"))

    // Current is 1.5, Minimum required is 1.5.1 -> Must update (true)
    assertTrue(VersionComparator.isVersionBelowMinimum("1.5", "1.5.1"))

    // Current is 1.6, Minimum required is 1.7 -> Must update (true)
    assertTrue(VersionComparator.isVersionBelowMinimum("1.6", "1.7"))

    // Current is 1.6, Minimum required is 2.0 -> Must update (true)
    assertTrue(VersionComparator.isVersionBelowMinimum("1.6", "2.0"))

    // Current is v1.5, Minimum required is v1.6 -> Must update (true)
    assertTrue(VersionComparator.isVersionBelowMinimum("v1.5", "v1.6"))
  }

  @Test
  fun testVersionSatisfiesMinimumCases() {
    // Current is 1.6, Minimum required is 1.6 -> Satisfied (false)
    assertFalse(VersionComparator.isVersionBelowMinimum("1.6", "1.6"))

    // Current is 1.6, Minimum required is 1.5 -> Satisfied (false)
    assertFalse(VersionComparator.isVersionBelowMinimum("1.6", "1.5"))

    // Current is 1.6, Minimum required is 1.0 -> Satisfied (false)
    assertFalse(VersionComparator.isVersionBelowMinimum("1.6", "1.0"))

    // Current is 1.6.2, Minimum required is 1.6.1 -> Satisfied (false)
    assertFalse(VersionComparator.isVersionBelowMinimum("1.6.2", "1.6.1"))

    // Current is 1.7, Minimum required is 1.7 -> Satisfied (false)
    assertFalse(VersionComparator.isVersionBelowMinimum("1.7", "1.7"))

    // Current is 1.7, Minimum required is 1.6 -> Satisfied (false)
    assertFalse(VersionComparator.isVersionBelowMinimum("1.7", "1.6"))

    // Current is 1.8, Minimum required is 1.8 -> Satisfied (false)
    assertFalse(VersionComparator.isVersionBelowMinimum("1.8", "1.8"))

    // Current is 1.8, Minimum required is 1.7 -> Satisfied (false)
    assertFalse(VersionComparator.isVersionBelowMinimum("1.8", "1.7"))

    // Current is 2.0, Minimum required is 2.0 -> Satisfied (false)
    assertFalse(VersionComparator.isVersionBelowMinimum("2.0", "2.0"))

    // Current is 2.0, Minimum required is 1.9 -> Satisfied (false)
    assertFalse(VersionComparator.isVersionBelowMinimum("2.0", "1.9"))

    // Current is 1.9, Minimum required is 2.0 -> Must update (true)
    assertTrue(VersionComparator.isVersionBelowMinimum("1.9", "2.0"))
  }

  @Test
  fun testVersionParser() {
    assertEquals(listOf(1, 5), VersionComparator.parseVersion("1.5"))
    assertEquals(listOf(1, 5, 0), VersionComparator.parseVersion("v1.5.0"))
    assertEquals(listOf(2, 0, 1), VersionComparator.parseVersion("2.0.1-rc1"))
  }

  @Test
  fun testRemoteConfigKeys() {
    assertEquals("latest_version", RemoteConfigManager.KEY_LATEST_VERSION)
    assertEquals("minimum_version", RemoteConfigManager.KEY_MINIMUM_VERSION)
    assertEquals("update_title", RemoteConfigManager.KEY_UPDATE_TITLE)
    assertEquals("update_message", RemoteConfigManager.KEY_UPDATE_MESSAGE)
    assertEquals("update_url", RemoteConfigManager.KEY_UPDATE_URL)
  }
}
