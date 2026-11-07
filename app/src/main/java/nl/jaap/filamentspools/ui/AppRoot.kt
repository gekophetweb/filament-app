package nl.jaap.filamentspools.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nl.jaap.filamentspools.ui.pairing.PairingScreen
import nl.jaap.filamentspools.ui.settings.SettingsScreen

object Routes {
    const val PRINTER = "printer"
    const val CATALOG = "catalog"
    const val SETTINGS = "settings"
    const val PRODUCT = "product/{id}"
    const val SPOOL = "spool/{uuid}"
    fun product(id: Long) = "product/$id"
    fun spool(uuid: String) = "spool/$uuid"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.PRINTER, "Printer", Icons.Filled.Print),
    Tab(Routes.CATALOG, "Catalog", Icons.Filled.Inventory2),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun AppRoot(vm: AppViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        AppState.Loading -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        is AppState.NeedsPairing -> PairingScreen(message = s.message)
        AppState.Ready -> MainNav()
    }
}

@Composable
private fun MainNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.PRINTER, modifier = Modifier.padding(padding)) {
            composable(Routes.PRINTER) { PrinterRoute(nav) }
            composable(Routes.CATALOG) { CatalogRoute(nav) }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.PRODUCT, listOf(navArgument("id") { type = NavType.LongType })) { ProductRoute(nav) }
            composable(Routes.SPOOL, listOf(navArgument("uuid") { type = NavType.StringType })) { SpoolRoute(nav) }
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

// Screens wired in later tasks.
@Composable private fun PrinterRoute(nav: NavHostController) = Text("Printer")
@Composable private fun CatalogRoute(nav: NavHostController) = Text("Catalog")
@Composable private fun ProductRoute(nav: NavHostController) = Text("Product")
@Composable private fun SpoolRoute(nav: NavHostController) = Text("Spool")
