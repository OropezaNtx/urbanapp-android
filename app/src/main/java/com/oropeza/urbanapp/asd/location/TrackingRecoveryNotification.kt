package com.oropeza.urbanapp.asd.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Puente de recuperación asistida cuando Android impide que WorkManager inicie
 * silenciosamente un foreground service de ubicación desde background.
 *
 * El toque del operador sobre la notificación constituye una interacción
 * explícita y entrega el mismo tripId a TrackingService, que vuelve a validar
 * marcador + Room antes de reanudar.
 */
object TrackingRecoveryNotification {

    private const val TAG = "TrackingRecovery"
    private const val CHANNEL_ID = "tracking_recovery_channel"
    private const val NOTIFICATION_ID = 1102

    fun show(context: Context, tripId: Long, suspensionMs: Long) {
        if (tripId <= 0L) return

        val appContext = context.applicationContext
        createChannel(appContext)

        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            Log.e(
                TAG,
                "RECOVERY_NOTIFICATION_BLOCKED trip=$tripId reason=notifications_disabled"
            )
            return
        }

        val serviceIntent = Intent(appContext, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
            putExtra(TrackingService.EXTRA_TRIP_ID, tripId)
            putExtra(EXTRA_RECOVERY_SUPERVISOR, true)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val requestCode = (tripId xor (tripId ushr 32)).toInt()
        val resumeIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(appContext, requestCode, serviceIntent, flags)
        } else {
            PendingIntent.getService(appContext, requestCode, serviceIntent, flags)
        }

        val gapText = formatGap(suspensionMs)
        val message = "Hay un levantamiento activo sin registrar GPS desde $gapText. Toca para reanudarlo."

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Rastreo interrumpido")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(resumeIntent)
            .addAction(android.R.drawable.ic_media_play, "REANUDAR RASTREO", resumeIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        Log.w(
            TAG,
            "RECOVERY_NOTIFICATION_POSTED trip=$tripId suspensionMs=$suspensionMs"
        )
    }

    fun cancel(context: Context, reason: String) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
        Log.i(TAG, "RECOVERY_NOTIFICATION_CLEARED reason=$reason")
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recuperación de rastreo",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas cuando un levantamiento activo necesita reanudar el GPS"
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    private fun formatGap(gapMs: Long): String {
        val seconds = (gapMs.coerceAtLeast(0L) / 1_000L)
        return if (seconds < 60L) {
            "${seconds}s"
        } else {
            "${seconds / 60L}m ${seconds % 60L}s"
        }
    }
}
