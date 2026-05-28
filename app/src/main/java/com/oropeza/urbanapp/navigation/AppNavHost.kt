package com.oropeza.urbanapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.oropeza.urbanapp.ui.screens.LoginScreen
import com.oropeza.urbanapp.ui.screens.SignUpScreen
import com.oropeza.urbanapp.asd.ui.viewmodel.AsdNewTripScreen
import com.oropeza.urbanapp.asd.ui.viewmodel.AsdTripDetailScreen
import com.oropeza.urbanapp.asd.ui.viewmodel.AsdTripListScreen
import com.oropeza.urbanapp.cc.ui.CcSessionListScreen
import com.oropeza.urbanapp.cc.ui.CcNewSessionScreen
import com.oropeza.urbanapp.cc.ui.CcSessionDetailScreen

// ✅ FOV
import com.oropeza.urbanapp.fov.ui.FovNewSessionScreen
import com.oropeza.urbanapp.fov.ui.FovSessionDetailScreenV2
import com.oropeza.urbanapp.fov.ui.FovSessionListScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "login") {

        composable("login") { LoginScreen(navController) }
        composable("signup") { SignUpScreen(navController) }

        composable("home") {
            HomeScreen(
                onOpenAsd = { navController.navigate("asd_list") },
                onOpenCc = { navController.navigate("cc_list") },
                onOpenFov = { navController.navigate("fov_list") }
            )
        }

        // ASD routes
        composable("asd_list") {
            AsdTripListScreen(
                onNewTrip = { navController.navigate("asd_new") },
                onOpenTrip = { tripId -> navController.navigate("asd_detail/$tripId") },
                onBackHome = { navController.popBackStack("home", inclusive = false) }
            )
        }

        composable("asd_new") {
            AsdNewTripScreen(
                onCreated = { tripId ->
                    navController.popBackStack()
                    navController.navigate("asd_detail/$tripId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "asd_detail/{tripId}",
            arguments = listOf(navArgument("tripId") { type = NavType.LongType })
        ) { backStack ->
            val tripId = backStack.arguments?.getLong("tripId") ?: 0L
            AsdTripDetailScreen(tripId = tripId, onBack = { navController.popBackStack() })
        }

        // CC routes
        composable("cc_list") {
            CcSessionListScreen(
                onNew = { navController.navigate("cc_new") },
                onOpen = { sessionId -> navController.navigate("cc_detail/$sessionId") },
                onBackHome = { navController.popBackStack("home", inclusive = false) }
            )
        }

        composable("cc_new") {
            CcNewSessionScreen(
                onCreated = { sessionId ->
                    navController.popBackStack()
                    navController.navigate("cc_detail/$sessionId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "cc_detail/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStack ->
            val sessionId = backStack.arguments?.getLong("sessionId") ?: 0L
            CcSessionDetailScreen(sessionId = sessionId, onBack = { navController.popBackStack() })
        }

        // ✅ FOV routes
        composable("fov_list") {
            FovSessionListScreen(
                onNew = { navController.navigate("fov_new") },
                onOpen = { sessionId -> navController.navigate("fov_detail/$sessionId") },
                onBackHome = { navController.popBackStack("home", inclusive = false) }
            )
        }

        composable("fov_new") {
            FovNewSessionScreen(
                onCreated = { sessionId ->
                    navController.popBackStack()
                    navController.navigate("fov_detail/$sessionId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "fov_detail/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStack ->
            val sessionId = backStack.arguments?.getLong("sessionId") ?: 0L
            FovSessionDetailScreenV2(
                sessionId = sessionId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
