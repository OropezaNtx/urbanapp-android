package com.oropeza.urbanapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.rememberNavController
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.navigation.AppNavHost
import com.oropeza.urbanapp.ui.theme.UrbanAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AsdGraph.init(applicationContext)
        setContent {
            UrbanAppTheme {
                val navController = rememberNavController()
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavHost(navController = navController)
                }
            }
        }
    }
}
