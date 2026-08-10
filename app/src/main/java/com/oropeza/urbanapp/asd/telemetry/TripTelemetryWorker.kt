package com.oropeza.urbanapp.asd.telemetry

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.sync.AsdCloudSyncWorker
import java.util.concurrent.TimeUnit

class TripTelemetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "TripTelemetry"
        private const val INPUT_TRIP_ID = "telemetry_trip_id"
        private const val INPUT_GENERATION = "telemetry_generation"
        private const val SAMPLE_INTERVAL_MS = 60_000L
        private const val CLOUD_FLUSH_EVERY_GENERATIONS = 5

        private fun workName(tripId: Long) = "ASD_TRIP_TELEMETRY_$tripId"

        fun arm(context: Context, tripId: Long) {
            if (tripId <= 0L) return
            enqueue(context.applicationContext, tripId, 0, 0L)
            Log.i(TAG, "TELEMETRY_ARMED trip=$tripId")
        }

        fun disarm(context: Context, tripId: Long) {
            if (tripId > 0L) WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(tripId))
            Log.i(TAG, "TELEMETRY_DISARMED trip=$tripId")
        }

        private fun enqueue(context: Context, tripId: Long, generation: Int, delayMs: Long) {
            val input = Data.Builder()
                .putLong(INPUT_TRIP_ID, tripId)
                .putInt(INPUT_GENERATION, generation)
                .build()
            val request = OneTimeWorkRequestBuilder<TripTelemetryWorker>()
                .setInputData(input)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag("ASD_TRIP_TELEMETRY")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(tripId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): ListenableWorker.Result {
        val tripId = inputData.getLong(INPUT_TRIP_ID, -1L)
        val generation = inputData.getInt(INPUT_GENERATION, 0)
        if (tripId <= 0L) return ListenableWorker.Result.success()

        return try {
            AsdGraph.init(applicationContext)
            val trip = AsdGraph.db.tripDao().getByIdOnce(tripId) ?: return ListenableWorker.Result.success()
            TripTelemetryRecorder.ensureStarted(applicationContext, tripId)
            val battery = readBattery(applicationContext)
            val network = readNetwork(applicationContext)
            val finished = trip.endTime != null
            TripTelemetryRecorder.sampleDevice(
                context = applicationContext,
                tripId = tripId,
                batteryPct = battery.first,
                charging = battery.second,
                networkConnected = network.first,
                networkType = network.second,
                finished = finished
            )

            if (finished || generation % CLOUD_FLUSH_EVERY_GENERATIONS == 0) {
                TripTelemetryRecorder.enqueueCloudSnapshot(applicationContext, tripId)
                AsdCloudSyncWorker.enqueue(applicationContext)
            }

            if (!finished) {
                enqueue(applicationContext, tripId, generation + 1, SAMPLE_INTERVAL_MS)
            } else {
                Log.i(TAG, "TELEMETRY_FINALIZED trip=$tripId generation=$generation")
            }
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "TELEMETRY_SAMPLE_FAILED trip=$tripId generation=$generation", e)
            if (tripId > 0L) enqueue(applicationContext, tripId, generation + 1, SAMPLE_INTERVAL_MS)
            ListenableWorker.Result.success()
        }
    }

    private fun readBattery(context: Context): Pair<Int?, Boolean> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else null
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return pct to charging
    }

    private fun readNetwork(context: Context): Pair<Boolean, String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false to "NONE"
        val caps = cm.getNetworkCapabilities(network) ?: return false to "UNKNOWN"
        val connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "OTHER"
        }
        return connected to type
    }
}
