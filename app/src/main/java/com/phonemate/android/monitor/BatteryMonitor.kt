package com.phonemate.android.monitor

import android.content.Context
import android.os.BatteryManager

class BatteryMonitor(context: Context) {
    private val batteryManager = context.getSystemService(BatteryManager::class.java)

    // Returns the actual remaining charge percentage - this is what gets displayed to the user,
    // so it must read the same as the system battery indicator.
    fun getUsagePercent(): Int {
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level.coerceIn(0, 100)
    }
}
