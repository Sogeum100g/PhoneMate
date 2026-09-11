package com.phonemate.android.monitor

import com.phonemate.android.domain.ResourceType

interface ResourceMonitor {
    suspend fun getUsagePercent(type: ResourceType): Int
}

class AndroidResourceMonitor(
    private val batteryMonitor: BatteryMonitor,
    private val networkMonitor: NetworkMonitor
) : ResourceMonitor {
    override suspend fun getUsagePercent(type: ResourceType): Int {
        return when (type) {
            ResourceType.BATTERY -> batteryMonitor.getUsagePercent()
            ResourceType.NETWORK -> networkMonitor.getUsagePercent()
        }
    }
}

