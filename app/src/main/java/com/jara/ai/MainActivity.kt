package com.jara.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jara.ai.ui.*
import com.jara.ai.vm.ChatViewModel
import com.jara.ai.vm.SettingsViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { JaraTheme { JaraApp() } }
    }
}

@Composable
fun JaraApp() {
    val nav = rememberNavController()
    val chat: ChatViewModel = viewModel()
    val settings: SettingsViewModel = viewModel()
    val ctx = LocalContext.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val tabs = listOf("home" to (T.navHome to Icons.Default.Home), "history" to (T.navChat to Icons.Default.ChatBubble),
        "permissions" to (T.navPerm to Icons.Default.Security), "settings" to (T.navSet to Icons.Default.Settings))
    fun go(r: String) { scope.launch { drawer.close() }; nav.navigate(r) { launchSingleTop = true } }
    ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = route == "home", drawerContent = {
        DrawerContent(go = { r -> if (r == "share") { scope.launch { drawer.close() }; shareApp(ctx, settings.prefs.shareText) } else if (r == "voice") { scope.launch { drawer.close() }; nav.navigate("home") { launchSingleTop = true } } else go(r) },
            newChat = { chat.newChat(); scope.launch { drawer.close() }; nav.navigate("home") { launchSingleTop = true } })
    }) {
        Scaffold(containerColor = Navy, bottomBar = {
            if (route in tabs.map { it.first }) NavigationBar(containerColor = Card) {
                tabs.forEach { (r, li) ->
                    NavigationBarItem(selected = route == r, onClick = { nav.navigate(r) { launchSingleTop = true; popUpTo("home") } },
                        icon = { Icon(li.second, null) }, label = { Text(li.first) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan, selectedTextColor = Cyan, indicatorColor = Line, unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }) { pad ->
            NavHost(nav, startDestination = "splash", modifier = Modifier.padding(bottom = if (route in tabs.map { it.first }) pad.calculateBottomPadding() else androidx.compose.ui.unit.Dp.Hairline)) {
                composable("splash") { SplashScreen { nav.navigate("home") { popUpTo("splash") { inclusive = true } } } }
                composable("home") { HomeScreen(chat, onMenu = { scope.launch { drawer.open() } }, onAbout = { nav.navigate("about") }) }
                composable("history") { HistoryScreen(chat, { nav.popBackStack() }) { nav.navigate("home") } }
                composable("permissions") { PermissionsScreen({ nav.popBackStack() }) { nav.navigate("accessibility") } }
                composable("settings") { SettingsScreen(settings) { nav.popBackStack() } }
                composable("api") { ApiKeyScreen(settings) { nav.popBackStack() } }
                composable("accessibility") { AccessibilityScreen(settings) { nav.popBackStack() } }
                composable("voicemodel") { VoiceModelScreen(settings) { nav.popBackStack() } }
                composable("about") { AboutScreen { nav.popBackStack() } }
            }
        }
    }
}
