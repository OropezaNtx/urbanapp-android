package com.oropeza.urbanapp.core.identity

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.oropeza.urbanapp.BuildConfig
import java.util.UUID

object UrbanIdentityProvider {

    private const val PREFS_NAME = "urban_identity_prefs"
    private const val KEY_INSTALLATION_ID = "installation_id"
    private const val KEY_CREATED_AT = "installation_created_at"

    fun getIdentity(context: Context): UrbanDeviceIdentity {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        var installationId = prefs.getString(KEY_INSTALLATION_ID, null)
        var createdAt = prefs.getLong(KEY_CREATED_AT, 0L)
        
        if (installationId == null) {
            installationId = UUID.randomUUID().toString()
            createdAt = System.currentTimeMillis()
            prefs.edit()
                .putString(KEY_INSTALLATION_ID, installationId)
                .putLong(KEY_CREATED_AT, createdAt)
                .apply()
        }

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }

        return UrbanDeviceIdentity(
            installationId = installationId,
            androidId = androidId,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            } else null,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            packageName = context.packageName,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }
}
