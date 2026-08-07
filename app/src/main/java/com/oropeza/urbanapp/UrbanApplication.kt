package com.oropeza.urbanapp

import android.app.Application
import com.oropeza.urbanapp.asd.AsdGraph

class UrbanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AsdGraph.init(this)
    }
}
