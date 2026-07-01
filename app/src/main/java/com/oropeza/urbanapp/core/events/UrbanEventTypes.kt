package com.oropeza.urbanapp.core.events

object UrbanEventTypes {
    const val PLATFORM_BOOTSTRAP_STARTED = "platform.bootstrap.started"
    const val PLATFORM_BOOTSTRAP_SUCCESS = "platform.bootstrap.success"
    const val PLATFORM_BOOTSTRAP_WARNING = "platform.bootstrap.warning"
    const val PLATFORM_BOOTSTRAP_FAILED = "platform.bootstrap.failed"

    const val IDENTITY_READY = "identity.ready"
    const val WORKSPACE_READY = "workspace.ready"

    const val LICENSE_STATUS_CHANGED = "license.status.changed"
    const val PERMISSION_CHECKED = "permission.checked"

    const val SYNC_REQUESTED = "sync.requested"
    const val SYNC_COMPLETED = "sync.completed"
    const val SYNC_FAILED = "sync.failed"

    const val CONFIGURATION_FETCH_STARTED = "configuration.fetch.started"
    const val CONFIGURATION_FETCH_SUCCESS = "configuration.fetch.success"
    const val CONFIGURATION_FETCH_FAILED = "configuration.fetch.failed"
    const val CONFIGURATION_LOADED = "configuration.loaded"
    const val CONFIGURATION_WARNING = "configuration.warning"

    const val HEARTBEAT_ENQUEUED = "heartbeat.enqueued"
    const val DIAGNOSTICS_REQUESTED = "diagnostics.requested"

    const val ASD_TRIP_CREATED = "asd.trip.created"
    const val ASD_TRIP_CLOSED = "asd.trip.closed"
    const val ASD_EVENT_CREATED = "asd.event.created"

    const val ASD_TRIP_ENQUEUED_FOR_SYNC = "asd.trip.sync.enqueued"
    const val ASD_EVENT_ENQUEUED_FOR_SYNC = "asd.event.sync.enqueued"
    const val ASD_TRACK_CHUNK_ENQUEUED = "asd.track.chunk.enqueued"
    const val ASD_TRIP_SYNC_READY = "asd.trip.sync.ready"

    const val ASD_TRIP_SYNC_REQUESTED = "asd.trip.sync.requested"
    const val ASD_TRIP_SYNC_STATUS_VIEWED = "asd.trip.sync.status.viewed"

    // Operational Recovery Events
    const val RECOVERY_REQUIRED = "operational.recovery.required"
    const val RECOVERY_RESUMED = "operational.recovery.resumed"
}
