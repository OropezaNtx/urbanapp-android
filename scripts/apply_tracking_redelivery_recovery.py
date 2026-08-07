from pathlib import Path

PATH = Path("app/src/main/java/com/oropeza/urbanapp/asd/location/TrackingService.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Se esperaba exactamente una coincidencia para {label}; encontradas: {count}")
    return text.replace(old, new, 1)


text = PATH.read_text(encoding="utf-8")

if "START_FLAG_REDELIVERY" in text and "REDELIVERED_INTENT" in text:
    print("La recuperación por redelivery ya está aplicada.")
    raise SystemExit(0)

old_on_start = '''    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            Log.w(TAG, "Servicio recreado por Android sin Intent; iniciando reconciliación local")
            recoverAfterProcessRecreation()
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
                if (tripId > 0L) {
                    if (currentTripId != null && currentTripId != tripId) resetTrackingState()
                    startTracking(tripId, recovered = false, recoveryGapMs = 0L)
                } else {
                    Log.w(TAG, "ACTION_START sin tripId válido")
                    stopTracking(clearRecoveryMarker = true)
                }
            }

            ACTION_STOP -> stopTracking(clearRecoveryMarker = true)
            else -> {
                Log.w(TAG, "Acción desconocida; se detiene el servicio")
                stopTracking(clearRecoveryMarker = true)
            }
        }

        return START_STICKY
    }
'''

new_on_start = '''    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isRedelivery = flags and START_FLAG_REDELIVERY != 0

        if (intent == null) {
            Log.w(TAG, "Servicio recreado por Android sin Intent; iniciando reconciliación local")
            recoverAfterProcessRecreation(expectedTripId = null, source = "NULL_INTENT")
            return START_REDELIVER_INTENT
        }

        when (intent.action) {
            ACTION_START -> {
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
                } else {
                    Log.w(TAG, "ACTION_START sin tripId válido")
                    stopTracking(clearRecoveryMarker = true)
                }
            }

            ACTION_STOP -> stopTracking(clearRecoveryMarker = true)
            else -> {
                Log.w(TAG, "Acción desconocida; se detiene el servicio")
                stopTracking(clearRecoveryMarker = true)
            }
        }

        return START_REDELIVER_INTENT
    }
'''

text = replace_once(text, old_on_start, new_on_start, "onStartCommand con redelivery")

text = replace_once(
    text,
    "    private fun recoverAfterProcessRecreation() {\n",
    "    private fun recoverAfterProcessRecreation(expectedTripId: Long?, source: String) {\n",
    "firma de recuperación"
)

old_can_recover = '''            val canRecover = markerActive &&
                markedTripId > 0L &&
                activeTrip != null &&
                activeTrip.tripId == markedTripId
'''

new_can_recover = '''            val expectedTripMatches = expectedTripId == null || expectedTripId == markedTripId
            val canRecover = markerActive &&
                markedTripId > 0L &&
                expectedTripMatches &&
                activeTrip != null &&
                activeTrip.tripId == markedTripId
'''

text = replace_once(text, old_can_recover, new_can_recover, "validación del trip redeliverado")

old_reject_log = '''                    "Recuperación rechazada: markerActive=$markerActive markedTripId=$markedTripId " +
                        "activeTripCount=${activeTrips.size} activeTripId=${activeTrip?.tripId}"
'''

new_reject_log = '''                    "Recuperación rechazada: source=$source markerActive=$markerActive " +
                        "markedTripId=$markedTripId expectedTripId=$expectedTripId " +
                        "activeTripCount=${activeTrips.size} activeTripId=${activeTrip?.tripId}"
'''

text = replace_once(text, old_reject_log, new_reject_log, "diagnóstico de recuperación rechazada")

old_recovery_log = '''            Log.w(TAG, "Recuperando tracking trip=${activeTrip.tripId} suspensiónMs=$gapMs")
'''

new_recovery_log = '''            Log.w(
                TAG,
                "Recuperando tracking source=$source trip=${activeTrip.tripId} suspensiónMs=$gapMs"
            )
'''

text = replace_once(text, old_recovery_log, new_recovery_log, "diagnóstico de recuperación aceptada")

PATH.write_text(text, encoding="utf-8", newline="\n")
print("Aplicado: START_REDELIVER_INTENT con reconciliación segura contra Room y marcador local.")
