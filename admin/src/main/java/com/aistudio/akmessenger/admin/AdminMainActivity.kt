package com.aistudio.akmessenger.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aistudio.akmessenger.admin.ui.screens.AdminDashboardScreen
import com.aistudio.akmessenger.admin.ui.screens.AdminLoginScreen
import com.aistudio.akmessenger.admin.ui.screens.AdminSplashScreen
import com.aistudio.akmessenger.admin.ui.theme.AkAdminTheme
import com.aistudio.akmessenger.admin.ui.viewmodel.AdminViewModel
import kotlinx.serialization.Serializable

@Serializable data object AdminSplashRoute
@Serializable data object AdminLoginRoute
@Serializable data object AdminHomeRoute

class AdminMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AdminViewModel = viewModel()
            val currentAdmin by viewModel.currentAdmin.collectAsStateWithLifecycle()
            val navController = rememberNavController()

            AkAdminTheme {
                NavHost(navController = navController, startDestination = AdminSplashRoute) {
                    composable<AdminSplashRoute> {
                        AdminSplashScreen(
                            isAuthenticated = currentAdmin != null,
                            onFinished = { authed ->
                                navController.navigate(if (authed) AdminHomeRoute else AdminLoginRoute) {
                                    popUpTo(AdminSplashRoute) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable<AdminLoginRoute> {
                        AdminLoginScreen(
                            viewModel = viewModel,
                            onAuthenticated = {
                                navController.navigate(AdminHomeRoute) {
                                    popUpTo(AdminLoginRoute) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable<AdminHomeRoute> {
                        AdminDashboardScreen(
                            viewModel = viewModel,
                            onLoggedOut = {
                                navController.navigate(AdminLoginRoute) {
                                    popUpTo(AdminHomeRoute) { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
