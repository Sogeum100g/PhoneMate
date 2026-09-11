package com.phonemate.android.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkMonitorTest {
    @Test
    fun convertsByteDeltaToMbps() {
        // 1,000,000 bytes = 8,000,000 bits over 1000ms = 8Mbps
        assertEquals(8, NetworkMonitor.mbpsFromBytesAndMillis(1_000_000, 1_000))
        // Same byte delta over a 3000ms poll interval should read a third of the rate
        assertEquals(3, NetworkMonitor.mbpsFromBytesAndMillis(1_000_000, 3_000))
    }

    @Test
    fun zeroDeltaIsZeroMbps() {
        assertEquals(0, NetworkMonitor.mbpsFromBytesAndMillis(0, 3_000))
    }

    @Test
    fun nonPositiveElapsedTimeIsZeroMbps() {
        assertEquals(0, NetworkMonitor.mbpsFromBytesAndMillis(1_000_000, 0))
        assertEquals(0, NetworkMonitor.mbpsFromBytesAndMillis(1_000_000, -1))
    }
}
