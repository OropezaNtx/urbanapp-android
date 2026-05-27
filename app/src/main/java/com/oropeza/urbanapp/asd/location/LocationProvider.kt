package com.oropeza.urbanapp.asd.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.tasks.await
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt




/**
 * LocationFix esperado por tu código:
 * lat, lon, accM, provider, fixTime, status
 *
 * status: FIX_OK | FIX_USABLE | NO_FIX
 */
class LocationProvider(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    // ✅ Filtro más “real” para lat/lon (adaptativo)
    private val kalmanEvent = KalmanLatLonFilter()

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    // =========================
    // Quick Fix (puede usar cache)
    // =========================
    @SuppressLint("MissingPermission")
    suspend fun getQuickFix(highAccuracy: Boolean = true): android.location.Location? {
        if (!hasPermission()) return null

        // 1) lastLocation (cache)
        val last = try { client.lastLocation.await() } catch (_: Exception) { null }
        if (last != null) return last

        // 2) getCurrentLocation (fresco)
        val prio = if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY
        else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        val cts = CancellationTokenSource()
        return try {
            client.getCurrentLocation(prio, cts.token).await()
        } catch (_: Exception) {
            null
        } finally {
            cts.cancel()
        }
    }

    // =========================
    // Fresh Fix (NO cache primero)
    // =========================
    @SuppressLint("MissingPermission")
    suspend fun getFreshFixOnce(highAccuracy: Boolean = true): android.location.Location? {
        if (!hasPermission()) return null

        val prio = if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY
        else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        val cts = CancellationTokenSource()
        return try {
            client.getCurrentLocation(prio, cts.token).await()
        } catch (_: Exception) {
            null
        } finally {
            cts.cancel()
        }
    }

    // =========================
    // Strict Fix: intenta <= maxAccM en ventana corta
    // (sirve como "sample window" para quedarte con el mejor punto)
    // =========================
    @SuppressLint("MissingPermission")
    suspend fun getBestFixStrict(
        maxAccM: Double = 10.0,
        timeoutMs: Long = 2_000L,
        highAccuracy: Boolean = true
    ): LocationFix = withContext(Dispatchers.Default) {

        if (!hasPermission()) return@withContext noFix()

        val priority = if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY
        else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        // ✅ Ventana corta: muestreo agresivo para capturar el mejor fix en 2s
        val intervalMsLocal = 700L
        val minUpdateMsLocal = 350L
        val minDistanceMLocal = 0f
        val maxWaitTimeMsLocal = 0L

        val request = LocationRequest.Builder(priority, intervalMsLocal)
            .setMinUpdateIntervalMillis(minUpdateMsLocal)
            .setMinUpdateDistanceMeters(minDistanceMLocal)
            .setMaxUpdateDelayMillis(maxWaitTimeMsLocal)
            .setWaitForAccurateLocation(true)              // ✅ clave
            .setGranularity(Granularity.GRANULARITY_FINE)  // ✅ clave
            .build()



        var best: android.location.Location? = null
        val start = System.currentTimeMillis()

        val flow = callbackFlow<android.location.Location> {
            val cb = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.locations.minByOrNull { it.accuracy } ?: return
                    trySend(loc)

                }
            }
            try {
                client.requestLocationUpdates(request, cb, Looper.getMainLooper())
            } catch (e: SecurityException) {
                close(e); return@callbackFlow
            }

            awaitClose { client.removeLocationUpdates(cb) }
        }

        try {
            flow.collect { loc ->
                if (best == null || loc.accuracy < (best?.accuracy ?: Float.MAX_VALUE)) best = loc

                if (loc.accuracy.toDouble() <= maxAccM) throw StopCollect
                if (System.currentTimeMillis() - start >= timeoutMs) throw StopCollect
            }
        } catch (_: Throwable) {
            // usamos best
        }

        val b = best ?: return@withContext noFix()
        val status = if (b.accuracy.toDouble() <= maxAccM) "FIX_OK" else "FIX_USABLE"
        return@withContext b.toFix(status = status)
    }

    private object StopCollect : Throwable()

    // =========================
    // Soft Fix (bundle): raw + filtered
    // - Incluye etapas + outliers + kalman condicional
    // =========================
    @SuppressLint("MissingPermission")
    suspend fun getBestFixSoftBundle(
        targetAccM: Double = 10.0,
        fallbackAccM: Double = 15.0,
        timeoutMs: Long = 10_000L,
        highAccuracy: Boolean = true,
        preferFresh: Boolean = true,
        allowEarlyUsable: Boolean = false,     // ✅ por defecto NO: prioridad exactitud
        kalmanMaxAccM: Double = 20.0           // ✅ si accuracy > esto, no aplicar Kalman
    ): EventFixBundle = withContext(Dispatchers.Default) {

        if (!hasPermission()) return@withContext EventFixBundle(noFix(), noFix())

        val start = System.currentTimeMillis()
        var bestRaw: LocationFix? = null

        // Reset del filtro para que no arrastre cosas viejas en un evento nuevo
        kalmanEvent.reset()

        // 1) Primer intento: current location
        val firstLoc = try {
            if (preferFresh) getFreshFixOnce(highAccuracy) else getQuickFix(highAccuracy)
        } catch (_: Exception) { null }

        if (firstLoc != null) {
            val raw = firstLoc.toFix(
                status = if (firstLoc.accuracy.toDouble() <= targetAccM) "FIX_OK" else "FIX_USABLE"
            )
            bestRaw = raw

            // Si ya cumple target, regresamos
            if (raw.accM <= targetAccM) {
                val filtered = raw.kalmanFilteredIfUseful(kalmanMaxAccM, isStationary = true)
                    .copy(status = "FIX_OK")
                return@withContext EventFixBundle(raw = raw.copy(status = "FIX_OK"), filtered = filtered)
            }

            // Si está decente (fallback) y te permiten early usable (ej. arranque UI)
            if (allowEarlyUsable && raw.accM <= fallbackAccM) {
                val filtered = raw.kalmanFilteredIfUseful(kalmanMaxAccM, isStationary = true)
                    .copy(status = "FIX_USABLE")
                return@withContext EventFixBundle(raw = raw.copy(status = "FIX_USABLE"), filtered = filtered)
            }
        }

        // 2) Ventanas cortas buscando el mejor fix, con etapa "acquire" al inicio
        while (System.currentTimeMillis() - start < timeoutMs) {

            val elapsed = System.currentTimeMillis() - start
            val remaining = timeoutMs - elapsed
            val window = min(2_000L, remaining)

            // Etapas:
            // - primeros ~2.5s: permitimos una ventana un poco menos estricta para no “congelar” UI
            // - después: ya buscamos target real
            val stageMax = if (elapsed < 2_500L) fallbackAccM else targetAccM

            val raw = getBestFixStrict(
                maxAccM = stageMax,
                timeoutMs = window,
                highAccuracy = highAccuracy
            )

            // Outlier rejection vs best actual
            if (bestRaw != null && isOutlier(bestRaw!!, raw)) {
                delay(120L)
                continue
            }

            if (bestRaw == null || raw.accM < bestRaw!!.accM) bestRaw = raw

            // Si ya logramos target: listo
            if (raw.accM <= targetAccM) {
                val filtered = raw.kalmanFilteredIfUseful(kalmanMaxAccM, isStationary = true)
                    .copy(status = "FIX_OK")
                return@withContext EventFixBundle(raw = raw.copy(status = "FIX_OK"), filtered = filtered)
            }

            // Early usable solo si el caller lo permite
            if (allowEarlyUsable && elapsed < 2_500L && raw.accM <= fallbackAccM) {
                val filtered = raw.kalmanFilteredIfUseful(kalmanMaxAccM, isStationary = true)
                    .copy(status = "FIX_USABLE")
                return@withContext EventFixBundle(raw = raw.copy(status = "FIX_USABLE"), filtered = filtered)
            }

            delay(220L)
        }

        // 3) Se acabó el tiempo: regresamos lo mejor que hay
        val raw = bestRaw ?: return@withContext EventFixBundle(noFix(), noFix())
        val finalStatus = when {
            raw.accM <= targetAccM -> "FIX_OK"
            raw.accM <= fallbackAccM -> "FIX_USABLE"
            else -> "NO_FIX"
        }

        val filtered = raw.kalmanFilteredIfUseful(kalmanMaxAccM, isStationary = true)
            .copy(status = finalStatus)

        return@withContext EventFixBundle(
            raw = raw.copy(status = finalStatus),
            filtered = filtered
        )
    }

    // =========================
    // API simples para UI
    // =========================

    /**
     * ✅ EVENTOS (Stop/Delay): prioridad exactitud.
     * - target: 10m
     * - fallback: 15m
     * - NO allowEarlyUsable
     */
    suspend fun getBestFixForEvent(
        targetAccM: Double = 10.0,
        fallbackAccM: Double = 15.0,
        timeoutMs: Long = 10_000L,
        highAccuracy: Boolean = true
    ): LocationFix {
        return getBestFixSoftBundle(
            targetAccM = targetAccM,
            fallbackAccM = fallbackAccM,
            timeoutMs = timeoutMs,
            highAccuracy = highAccuracy,
            preferFresh = true,
            allowEarlyUsable = false
        ).filtered
    }

    /**
     * ✅ INICIO DE VIAJE (si lo necesitas): rápido pero controlado.
     * Aquí sí permitimos early usable en los primeros 2.5s si cae <= fallback.
     * OJO: tu TrackingService ya hace “arming”, así que esto es solo para UI.
     */
    suspend fun getBestFixForStartTrip(
        targetAccM: Double = 10.0,
        fallbackAccM: Double = 25.0,
        timeoutMs: Long = 6_000L,
        highAccuracy: Boolean = true
    ): LocationFix {
        return getBestFixSoftBundle(
            targetAccM = targetAccM,
            fallbackAccM = fallbackAccM,
            timeoutMs = timeoutMs,
            highAccuracy = highAccuracy,
            preferFresh = true,
            allowEarlyUsable = true
        ).filtered
    }

    // =========================
    // Stream para TrackingService
    // =========================
    @SuppressLint("MissingPermission")
    fun locationUpdates(
        intervalMs: Long,
        minUpdateMs: Long,
        minDistanceM: Float,
        maxWaitTimeMs: Long,
        highAccuracy: Boolean
    ): Flow<android.location.Location> = callbackFlow {


        if (!hasPermission()) {
            close(IllegalStateException("Missing location permission"))
            return@callbackFlow
        }

        val priority = if (highAccuracy)
            Priority.PRIORITY_HIGH_ACCURACY
        else
            Priority.PRIORITY_BALANCED_POWER_ACCURACY

        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(minUpdateMs)
            .setMinUpdateDistanceMeters(minDistanceM)
            .setMaxUpdateDelayMillis(maxWaitTimeMs)
            .setWaitForAccurateLocation(true) // 🔥 clave
            .setGranularity(Granularity.GRANULARITY_FINE) // 🔥 clave
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val best = result.locations.minByOrNull { it.accuracy } ?: return
                trySend(best)
            }
        }

        try {
            client.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }

        awaitClose {
            client.removeLocationUpdates(callback)
        }
    }

    // ✅ PUBLIC: convertir Location -> LocationFix con status por umbral
    fun locationToFixWithStatus(
        loc: android.location.Location,
        targetAccM: Double = 10.0,
        fallbackAccM: Double = 25.0
    ): LocationFix {
        val acc = loc.accuracy.toDouble()
        val status = when {
            acc <= targetAccM -> "FIX_OK"
            acc <= fallbackAccM -> "FIX_USABLE"
            else -> "NO_FIX"
        }
        return LocationFix(
            lat = loc.latitude,
            lon = loc.longitude,
            accM = acc,
            provider = loc.provider ?: "fused",
            fixTime = if (loc.time > 0L) loc.time else System.currentTimeMillis(),
            status = status
        )
    }




    // =========================
    // Helpers
    // =========================

    private fun android.location.Location.toFix(status: String): LocationFix {
        return LocationFix(
            lat = latitude,
            lon = longitude,
            accM = accuracy.toDouble(),
            provider = provider ?: "fused",
            fixTime = if (time > 0L) time else System.currentTimeMillis(),
            status = status
        )
    }

    private fun LocationFix.kalmanFilteredIfUseful(kalmanMaxAccM: Double, isStationary: Boolean): LocationFix {
        if (lat == 0.0 && lon == 0.0) return this

        // Si la accuracy es mala, filtrar puede “inventar” estabilidad => no lo hacemos
        if (accM > kalmanMaxAccM) return this

        val (latF, lonF) = kalmanEvent.update(
            lat = lat,
            lon = lon,
            accM = accM,
            timeMs = fixTime,
            isStationary = isStationary
        )
        return copy(lat = latF, lon = lonF, provider = "$provider+kalman")
    }

    private fun noFix(): LocationFix {
        return LocationFix(
            lat = 0.0,
            lon = 0.0,
            accM = 9999.0,
            provider = "none",
            fixTime = System.currentTimeMillis(),
            status = "NO_FIX"
        )
    }

    // =========================
    // Outliers (teleport/salto)
    // =========================
    private fun isOutlier(prev: LocationFix, cand: LocationFix): Boolean {
        if (prev.lat == 0.0 && prev.lon == 0.0) return false

        val dtSec = ((cand.fixTime - prev.fixTime).coerceAtLeast(1L)).toDouble() / 1000.0
        val distM = haversineMeters(prev.lat, prev.lon, cand.lat, cand.lon)
        val speedMs = distM / dtSec

        // Teleport: velocidad absurda
        if (speedMs > 45.0) return true

        // Salto grande + accuracy mediocre
        if (distM > 40.0 && cand.accM > 20.0) return true

        return false
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
