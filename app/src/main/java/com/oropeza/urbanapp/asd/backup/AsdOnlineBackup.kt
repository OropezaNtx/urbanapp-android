package com.oropeza.urbanapp.asd.backup

import android.util.Log
import com.oropeza.urbanapp.asd.data.local.StopEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Legacy compatibility hook.
 *
 * Event durability is now owned by Room + sync_queue + the project-scoped cloud path.
 * The former implementation wrote directly to the top-level `urbanapp_asd_backups`
 * collection, bypassing the durable queue and current Firestore security model. Those
 * writes were rejected with PERMISSION_DENIED and added unnecessary network traffic.
 *
 * Keep this hook as a no-op for now so older repository call sites remain binary/source
 * compatible while field RC validation continues. It must never be treated as a source
 * of truth or as part of the delivery contract.
 */
object AsdOnlineBackup {
    private const val TAG = "AsdOnlineBackup"
    private val noticeLogged = AtomicBoolean(false)

    fun backupStopEvent(event: StopEvent) {
        if (noticeLogged.compareAndSet(false, true)) {
            Log.i(
                TAG,
                "LEGACY_DIRECT_BACKUP_DISABLED source=Room+sync_queue trip=${event.tripId}"
            )
        }
    }
}
