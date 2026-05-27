package com.oropeza.urbanapp.asd

import android.content.Context
import com.oropeza.urbanapp.asd.data.local.AppDatabase
import com.oropeza.urbanapp.asd.data.local.DbProvider
import com.oropeza.urbanapp.asd.data.repository.AsdRepository

object AsdGraph {

    lateinit var db: AppDatabase
        private set

    lateinit var repo: AsdRepository
        private set

    fun init(context: Context) {
        db = DbProvider.getInstance(context.applicationContext)
        repo = AsdRepository(db)
    }
}
