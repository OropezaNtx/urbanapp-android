package com.oropeza.urbanapp.core.license

sealed class LicenseResult {
    object Allowed : LicenseResult()
    data class Denied(val reason: String) : LicenseResult()
}
