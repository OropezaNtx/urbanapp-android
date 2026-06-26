package com.oropeza.urbanapp.core.license

sealed class UrbanLicenseResult {
    object Allowed : UrbanLicenseResult()
    data class Denied(val reason: String) : UrbanLicenseResult()
}
