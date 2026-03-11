package com.family.phototransfer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.family.phototransfer.ui.dashboard.SenderDashboardScreen
import com.family.phototransfer.ui.history.HistoryScreen
import com.family.phototransfer.ui.receiver.ReceiverScreen
import com.family.phototransfer.ui.settings.SettingsScreen
import com.family.phototransfer.ui.theme.PhotoTransferTheme
import com.family.phototransfer.ui.upload.UploadScreen
import dagger.hilt.android.AndroidEntryPoint

// ── 네비게이션 라우트 ──────────────────────────────────────────
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Home",     Icons.Default.Home)
    object Upload    : Screen("upload",    "Upload",   Icons.Default.Upload)
    object Receiver  : Screen("receiver",  "Receiver", Icons.Default.PhoneAndroid)
    object History   : Screen("history",   "History",  Icons.Default.History)
    object Settings  : Screen("settings",  "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Upload,
    Screen.Receiver,
    Screen.History,
    Screen.Settings
)

// ── 컬러 ──────────────────────────────────────────────────────
private val NavBarBg      = Color(0xFF1C2333)
private val NavSelected   = Color(0xFF2D6BE4)
private val NavUnselected = Color(0xFF8B9BB4)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoTransferTheme {
                PhotoTransferNavHost()
            }
        }
    }
}

@Composable
fun PhotoTransferNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = NavBarBg
            ) {
                bottomNavItems.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        icon = {
                            Icon(
                                screen.icon,
                                contentDescription = screen.label,
                                tint = if (isSelected) NavSelected else NavUnselected
                            )
                        },
                        label = {
                            Text(
                                screen.label,
                                color = if (isSelected) NavSelected else NavUnselected
                            )
                        },
                        selected = isSelected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = NavSelected.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) { SenderDashboardScreen() }
            composable(Screen.Upload.route)    { UploadScreen() }
            composable(Screen.Receiver.route)  { ReceiverScreen() }
            composable(Screen.History.route)   { HistoryScreen() }
            composable(Screen.Settings.route)  { SettingsScreen() }
        }
    }
}
