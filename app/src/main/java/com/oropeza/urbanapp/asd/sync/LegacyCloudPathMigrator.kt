package com.oropeza.urbanapp.asd.sync

import android.content.Context
import android.util.Log
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.core.runtime.UrbanRuntime

/**
 * Sprint 2.2.4A — Legacy Cloud Path Migration.
 *
 * Repara exclusivamente filas no sincronizadas creadas con el workspace/proyecto
 * demo histórico. No modifica filas SYNCED ni rutas distintas al prefijo exacto
 * esperado. Es idempotente: una vez migrada una ruta deja de ser elegible.
 */
object LegacyCloudPathMigrator {

    private const val TAG = "LegacyCloudMigration"
    private const val LEGACY_PREFIX = "asd_organizations/demo_workspace/projects/demo_project/"
    private const val TARGET_WORKSPACE = "afora"
    private const val TARGET_PROJECT = "urban_operations"
    private const val TARGET_PREFIX = "asd_organizations/$TARGET_WORKSPACE/projects/$TARGET_PROJECT/"

    data class Result(
        val eligible: Int,
        val migrated: Int,
        val skipped: Boolean,
        val reason: String? = null
    )

    fun migrateIfNeeded(context: Context): Result {
        val appContext = context.applicationContext
        val workspace = UrbanRuntime.workspace(appContext)
        val workspaceId = workspace.organization.workspaceId
        val projectId = workspace.project.projectId

        if (workspaceId != TARGET_WORKSPACE || projectId != TARGET_PROJECT) {
            Log.w(
                TAG,
                "LEGACY_PATH_MIGRATION_SKIPPED runtimeWorkspace=$workspaceId runtimeProject=$projectId " +
                    "requiredWorkspace=$TARGET_WORKSPACE requiredProject=$TARGET_PROJECT"
            )
            return Result(
                eligible = 0,
                migrated = 0,
                skipped = true,
                reason = "RUNTIME_TARGET_MISMATCH"
            )
        }

        val db = AsdGraph.db.openHelper.writableDatabase
        val likePattern = "$LEGACY_PREFIX%"

        val eligible = db.query(
            "SELECT COUNT(*) FROM sync_queue " +
                "WHERE cloudPath LIKE ? AND status IN ('PENDING','FAILED','DEAD_LETTER')",
            arrayOf(likePattern)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

        if (eligible == 0) {
            Log.i(
                TAG,
                "LEGACY_PATH_MIGRATION_NOOP eligible=0 from=$LEGACY_PREFIX to=$TARGET_PREFIX"
            )
            return Result(eligible = 0, migrated = 0, skipped = false)
        }

        val samples = mutableListOf<String>()
        db.query(
            "SELECT id, cloudPath, status FROM sync_queue " +
                "WHERE cloudPath LIKE ? AND status IN ('PENDING','FAILED','DEAD_LETTER') " +
                "ORDER BY id ASC LIMIT 5",
            arrayOf(likePattern)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val oldPath = cursor.getString(1)
                val status = cursor.getString(2)
                val newPath = oldPath.replaceFirst(LEGACY_PREFIX, TARGET_PREFIX)
                samples += "id=$id status=$status old=$oldPath new=$newPath"
            }
        }

        Log.w(
            TAG,
            "LEGACY_PATH_MIGRATION_STARTED eligible=$eligible from=$LEGACY_PREFIX to=$TARGET_PREFIX"
        )
        samples.forEach { Log.i(TAG, "LEGACY_PATH_MIGRATION_SAMPLE $it") }

        val now = System.currentTimeMillis()
        db.beginTransaction()
        val migrated = try {
            db.execSQL(
                "UPDATE sync_queue SET " +
                    "cloudPath = ? || substr(cloudPath, ?), " +
                    "status = 'PENDING', attempts = 0, lastError = NULL, nextAttemptAt = 0, updatedAt = ? " +
                    "WHERE cloudPath LIKE ? AND status IN ('PENDING','FAILED','DEAD_LETTER')",
                arrayOf(
                    TARGET_PREFIX,
                    LEGACY_PREFIX.length + 1,
                    now,
                    likePattern
                )
            )

            val remaining = db.query(
                "SELECT COUNT(*) FROM sync_queue " +
                    "WHERE cloudPath LIKE ? AND status IN ('PENDING','FAILED','DEAD_LETTER')",
                arrayOf(likePattern)
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }

            val updated = eligible - remaining
            db.setTransactionSuccessful()
            updated
        } finally {
            db.endTransaction()
        }

        Log.w(
            TAG,
            "LEGACY_PATH_MIGRATION_COMPLETED eligible=$eligible migrated=$migrated " +
                "remaining=${eligible - migrated} target=$TARGET_PREFIX"
        )

        return Result(eligible = eligible, migrated = migrated, skipped = false)
    }
}
