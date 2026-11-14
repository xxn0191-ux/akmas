package com.aistudio.akmessenger.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aistudio.akmessenger.admin.ui.screens.AdminDashboardScreen
import com.aistudio.akmessenger.admin.ui.screens.AdminSplashScreen
import com.aistudio.akmessenger.admin.ui.theme.AkAdminTheme
import com.aistudio.akmessenger.admin.ui.viewmodel.AdminViewModel
import kotlinx.serialization.Serializable

@Serializable data object AdminSplashRoute
@Serializable data object AdminHomeRoute

class AdminMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AdminViewModel = viewModel()
            val navController = rememberNavController()

            AkAdminTheme {
                NavHost(navController = navController, startDestination = AdminSplashRoute) {
                    composable<AdminSplashRoute> {
                        AdminSplashScreen(
                            onFinished = {
                                navController.navigate(AdminHomeRoute) {
                                    popUpTo(AdminSplashRoute) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable<AdminHomeRoute> {
                        AdminDashboardScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
