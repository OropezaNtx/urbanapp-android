package com.oropeza.urbanapp.core.license

data class UrbanLicensePolicy(
    val allowOfflineUsage: Boolean = true,
    val maxOfflineDays: Int = 30,
    val enforceDeviceLimit: Boolean = true,
    val enforceUserLimit: Boolean = true,
    val defaultGracePeriodDays: Int = 7
)
