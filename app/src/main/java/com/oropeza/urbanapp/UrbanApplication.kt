package com.oropeza.urbanapp

import android.app.Application
import android.util.Log
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.location.TrackingRecoveryWorker

class UrbanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AsdGraph.init(this)

        runCatching {
            TrackingRecoveryWorker.enqueueForColdStart(this)
        }.onFailure { error ->
            Log.e("UrbanApplication", "No se pudo programar supervisor de tracking", error)
        }
    }
}
