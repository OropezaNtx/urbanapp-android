package com.oropeza.urbanapp.core.security

import java.security.MessageDigest

/**
 * Lightweight operator-build gate for local export controls.
 *
 * This is intentionally not treated as a DRM/security boundary. The APK remains
 * reverse-engineerable. Its purpose is to prevent casual access to internal
 * export tooling on field devices while server/cloud controls remain authoritative.
 */
object FieldOperatorAccess {
    private const val EXPORT_PIN_SHA256 = "88ebd6c70963ea6f0bd12514fb24d4de6abbd5f1447f555da8be93c54ab24224"

    fun verifyExportPin(input: String): Boolean {
        val normalized = input.trim()
        if (normalized.isEmpty()) return false
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return constantTimeEquals(digest, EXPORT_PIN_SHA256)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
