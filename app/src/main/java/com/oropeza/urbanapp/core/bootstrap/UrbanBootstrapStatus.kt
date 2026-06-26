package com.oropeza.urbanapp.core.bootstrap

data class UrbanBootstrapStatus(
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: String = "NOT_STARTED", // NOT_STARTED, RUNNING, SUCCESS, WARNING, FAILED
    val identityReady: Boolean = false,
    val workspaceReady: Boolean = false,
    val permissionsReady: Boolean = false,
    val licenseReady: Boolean = false,
    val configurationReady: Boolean = false,
    val syncReady: Boolean = false,
    val diagnosticsReady: Boolean = false,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)
