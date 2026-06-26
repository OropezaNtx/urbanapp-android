package com.oropeza.urbanapp.core.events

object UrbanEventFactory {

    fun platform(type: String, module: String? = "PLATFORM", payload: Map<String, Any?> = emptyMap()): UrbanEvent {
        return UrbanEvent(type = type, source = "PLATFORM", module = module, payload = payload)
    }

    fun sync(type: String, payload: Map<String, Any?> = emptyMap()): UrbanEvent {
        return UrbanEvent(type = type, source = "SYNC_ENGINE", module = "SYNC", payload = payload)
    }

    fun diagnostics(type: String, payload: Map<String, Any?> = emptyMap()): UrbanEvent {
        return UrbanEvent(type = type, source = "DIAGNOSTICS", module = "CORE", payload = payload)
    }

    fun asd(type: String, payload: Map<String, Any?> = emptyMap()): UrbanEvent {
        return UrbanEvent(type = type, source = "ASD_OPERATIONAL", module = "ASD", payload = payload)
    }

    fun warning(type: String, source: String, module: String?, payload: Map<String, Any?> = emptyMap()): UrbanEvent {
        return UrbanEvent(type = type, source = source, module = module, payload = payload, severity = "WARNING")
    }

    fun error(type: String, source: String, module: String?, payload: Map<String, Any?> = emptyMap()): UrbanEvent {
        return UrbanEvent(type = type, source = source, module = module, payload = payload, severity = "ERROR")
    }
}
