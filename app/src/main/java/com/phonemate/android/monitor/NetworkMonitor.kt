package com.phonemate.android.monitor

import android.net.TrafficStats
import kotlin.math.roundToInt

// Measures actual downstream throughput as a delta between consecutive polls, not the link's
// rated capacity - a device sitting on a fast, idle Wi-Fi link reads near zero here until data is
// actually flowing. The reading is in Mbps, not a 0-100% value: it is shown to the user as-is and
// compared directly against the (also Mbps) state thresholds.
class NetworkMonitor {
    private var previousRxBytes: Long? = null
    private var previousTimestampMillis: Long = 0L

    fun getUsagePercent(): Int {
        val rxBytes = TrafficStats.getTotalRxBytes()
        val now = System.currentTimeMillis()
        if (rxBytes < 0) {
            return 0
        }

        val previousBytes = previousRxBytes
        val previousTimestamp = previousTimestampMillis
        previousRxBytes = rxBytes
        previousTimestampMillis = now

        // No baseline yet, or the counter went backwards (device reboot between polls) - skip
        // this sample rather than report a bogus negative-delta rate.
        if (previousBytes == null || rxBytes < previousBytes) {
            return 0
        }

        return mbpsFromBytesAndMillis(rxBytes - previousBytes, now - previousTimestamp)
    }

    companion object {
        fun mbpsFromBytesAndMillis(deltaBytes: Long, elapsedMillis: Long): Int {
            if (elapsedMillis <= 0) {
                return 0
            }

            val bitsPerSecond = deltaBytes * 8.0 * 1000.0 / elapsedMillis
            return (bitsPerSecond / 1_000_000.0).roundToInt().coerceAtLeast(0)
        }
    }
}
