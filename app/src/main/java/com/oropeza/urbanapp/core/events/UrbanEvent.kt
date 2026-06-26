package com.oropeza.urbanapp.core.events

import java.util.UUID

data class UrbanEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val type: String,
    val source: String,
    val module: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: Map<String, Any?> = emptyMap(),
    val severity: String = "INFO", // INFO, WARNING, ERROR
    val correlationId: String? = null
)
