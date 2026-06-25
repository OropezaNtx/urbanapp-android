package com.oropeza.urbanapp.core.diagnostics

data class UrbanHealthStatus(
    val status: String, // OK | WARNING | ERROR
    val message: String,
    val details: List<String>
)
