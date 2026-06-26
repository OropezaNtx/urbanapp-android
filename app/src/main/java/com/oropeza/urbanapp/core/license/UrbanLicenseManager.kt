package com.oropeza.urbanapp.core.license

import android.content.Context
import com.oropeza.urbanapp.core.runtime.UrbanRuntime

object UrbanLicenseManager {

    fun currentLicense(context: Context): UrbanLicense {
        return UrbanLicenseRepository(context).getCurrentLicense()
    }

    fun canUseModule(context: Context, module: String): Boolean {
        return UrbanLicenseRepository(context).canUseModule(module)
    }

    fun canUseFeature(context: Context, feature: String): Boolean {
        return UrbanLicenseRepository(context).canUseFeature(feature)
    }

    fun status(context: Context): UrbanLicenseStatus {
        return UrbanLicenseRepository(context).licenseStatus()
    }
}
