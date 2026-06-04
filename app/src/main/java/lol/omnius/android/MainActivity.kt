package lol.omnius.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import lol.omnius.android.ui.components.Sidebar
import lol.omnius.android.ui.navigation.AppNavGraph
import lol.omnius.android.ui.navigation.SidebarItem
import lol.omnius.android.ui.theme.OmniusDark
import lol.omnius.android.ui.theme.OmniusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeVolumeKeys()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            OmniusTheme {
                MainScreen()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (handleVolumeKey(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(OmniusDark),
    ) {
        // Sidebar — always collapsed (icons only)
        Sidebar(
            currentRoute = currentRoute,
            expanded = false,
            onItemClick = { item ->
                navController.navigate(item.route) {
                    popUpTo(SidebarItem.HOME.route) {
                        inclusive = (item == SidebarItem.HOME)
                    }
                    launchSingleTop = true
                }
            },
        )

        // Content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            AppNavGraph(navController = navController)
        }
    }
}
