package com.magick.gsm2sip

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.magick.gsm2sip.ui.MainViewModel
import com.magick.gsm2sip.ui.screens.HistoryScreen
import com.magick.gsm2sip.ui.screens.LogScreen
import com.magick.gsm2sip.ui.screens.SettingsScreen
import com.magick.gsm2sip.ui.screens.StatusScreen
import com.magick.gsm2sip.ui.theme.MagickGsm2SipTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MagickGsm2SipTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GatewayApp()
                }
            }
        }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    STATUS("status", "Status", Icons.Filled.Wifi),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
    LOGS("logs", "Logs", Icons.Filled.Article),
    HISTORY("history", "History", Icons.Filled.History),
}

@Composable
private fun GatewayApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()

    // Runtime permissions + default-dialer role.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results surfaced in UI as capabilities; nothing to do here */ }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* user may or may not have granted default-dialer; app still functions */ }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(runtimePermissions())
        requestDefaultDialer(navController.context, roleLauncher::launch)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.STATUS.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.STATUS.route) {
                StatusScreen(
                    state = state,
                    accountUri = if (config.isComplete) config.accountUri else "",
                    audioBridgeSupported = viewModel.audioBridgeSupported,
                    onStart = viewModel::startGateway,
                    onStop = viewModel::stopGateway,
                )
            }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    config = config,
                    onSave = viewModel::saveConfig,
                    onApplyVoBizPreset = viewModel::applyVoBizPreset,
                )
            }
            composable(Tab.LOGS.route) {
                LogScreen(logs = logs, onClear = viewModel::clearLogs)
            }
            composable(Tab.HISTORY.route) {
                HistoryScreen(history = history, onClear = viewModel::clearHistory)
            }
        }
    }
}

private fun runtimePermissions(): Array<String> = buildList {
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.READ_PHONE_NUMBERS)
    add(Manifest.permission.RECORD_AUDIO)
    add(Manifest.permission.ANSWER_PHONE_CALLS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun requestDefaultDialer(context: android.content.Context, launch: (Intent) -> Unit) {
    val roleManager = context.getSystemService(RoleManager::class.java) ?: return
    if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
        !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    ) {
        launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
    }
}
