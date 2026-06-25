package com.oropeza.urbanapp.core.runtime

import android.content.Context
import com.oropeza.urbanapp.core.identity.UrbanDeviceIdentity
import com.oropeza.urbanapp.core.identity.UrbanIdentityProvider

object UrbanIdentityManager {
    fun getIdentity(context: Context): UrbanDeviceIdentity {
        return UrbanIdentityProvider.getIdentity(context)
    }

    fun getShortInstallationId(context: Context): String {
        return getIdentity(context).installationId.take(8).uppercase()
    }
}
