from pathlib import Path

path = Path("app/src/main/java/com/oropeza/urbanapp/asd/location/TrackingService.kt")
text = path.read_text(encoding="utf-8")

old = '''            ACTION_START -> {
                val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
                if (tripId > 0L) {
                    if (isRedelivery) {
                        Log.w(TAG, "TRACK_START redeliverado por Android para trip=$tripId")
                        recoverAfterProcessRecreation(
                            expectedTripId = tripId,
                            source = "REDELIVERED_INTENT"
                        )
                    } else {
                        if (currentTripId != null && currentTripId != tripId) resetTrackingState()
                        startTracking(tripId, recovered = false, recoveryGapMs = 0L)
                    }
'''

new = '''            ACTION_START -> {
                val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
                val isSupervisorRecovery = intent.getBooleanExtra(EXTRA_RECOVERY_SUPERVISOR, false)
                if (tripId > 0L) {
                    when {
                        isRedelivery -> {
                            Log.w(TAG, "TRACK_START redeliverado por Android para trip=$tripId")
                            recoverAfterProcessRecreation(
                                expectedTripId = tripId,
                                source = "REDELIVERED_INTENT"
                            )
                        }

                        isSupervisorRecovery -> {
                            Log.w(TAG, "TRACK_START solicitado por supervisor para trip=$tripId")
                            recoverAfterProcessRecreation(
                                expectedTripId = tripId,
                                source = "COLD_START_SUPERVISOR"
                            )
                        }

                        else -> {
                            if (currentTripId != null && currentTripId != tripId) resetTrackingState()
                            startTracking(tripId, recovered = false, recoveryGapMs = 0L)
                        }
                    }
'''

if new in text:
    print("Integración del supervisor ya estaba aplicada.")
elif old not in text:
    raise RuntimeError(
        "No se encontró el bloque esperado de ACTION_START. "
        "No se modificó TrackingService.kt para evitar sobrescribir cambios incompatibles."
    )
else:
    text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")
    print("Aplicado: supervisor de cold start integrado con reconciliación segura.")
