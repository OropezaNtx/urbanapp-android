package com.oropeza.urbanapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.rememberNavController
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.core.platform.UrbanSyncStatusProvider
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import com.oropeza.urbanapp.navigation.AppNavHost
import com.oropeza.urbanapp.ui.theme.UrbanAppTheme
import kotlinx.coroutines.flow.Flow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AsdGraph.init(applicationContext)

        UrbanRuntime.setSyncStatusProvider(object : UrbanSyncStatusProvider {
            override fun pendingSyncCountFlow(): Flow<Int> = AsdGraph.repo.syncQueuePendingCountFlow()
            override fun failedSyncCountFlow(): Flow<Int> = AsdGraph.repo.syncQueueFailedCountFlow()
            override fun lastSyncTimeFlow(): Flow<Long?> = AsdGraph.repo.lastSyncTimeFlow()
            override suspend fun lastSyncError(): String? = AsdGraph.repo.getLastFailedSyncItem()?.lastError
            override suspend fun lastSyncFailedPath(): String? = AsdGraph.repo.getLastFailedSyncItem()?.cloudPath
        })

        setContent {
            LaunchedEffect(Unit) {
                UrbanRuntime.bootstrap(applicationContext)
            }
            UrbanAppTheme {
                val navController = rememberNavController()
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavHost(navController = navController)
                }
            }
        }
    }
}
