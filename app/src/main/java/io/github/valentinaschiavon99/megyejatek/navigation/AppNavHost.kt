package io.github.valentinaschiavon99.megyejatek.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import io.github.valentinaschiavon99.megyejatek.learn.CitiesScreen
import io.github.valentinaschiavon99.megyejatek.learn.CountyDetailScreen
import io.github.valentinaschiavon99.megyejatek.learn.CountyListScreen
import io.github.valentinaschiavon99.megyejatek.learn.InfoPage
import io.github.valentinaschiavon99.megyejatek.learn.InfoScreen
import io.github.valentinaschiavon99.megyejatek.learn.LearnHomeScreen
import io.github.valentinaschiavon99.megyejatek.learn.NeighboursScreen
import io.github.valentinaschiavon99.megyejatek.settings.SettingsScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.valentinaschiavon99.megyejatek.play.PlayScreen
import io.github.valentinaschiavon99.megyejatek.results.ResultsScreen

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val navigate: (TopLevelDestination) -> Unit = { dest ->
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ImmersiveInLandscape(landscape)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (!landscape) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentDestination.isOn(dest),
                            onClick = { navigate(dest) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(Modifier.fillMaxSize().padding(innerPadding)) {
            if (landscape) {
                NavigationRail {
                    TopLevelDestination.entries.forEach { dest ->
                        NavigationRailItem(
                            selected = currentDestination.isOn(dest),
                            onClick = { navigate(dest) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = PlayRoute,
                modifier = Modifier.weight(1f),
            ) {
                composable<PlayRoute> { PlayScreen() }
                navigation<LearnGraph>(startDestination = LearnRoute) {
                    composable<LearnRoute> {
                        LearnHomeScreen(
                            onCounties = { navController.navigate(CountyListRoute) },
                            onNeighbours = { navController.navigate(NeighboursRoute) },
                            onCities = { navController.navigate(CitiesRoute) },
                            onInfo = { navController.navigate(InfoRoute(it.name)) },
                        )
                    }
                    composable<CountyListRoute> {
                        CountyListScreen(
                            onBack = { navController.popBackStack() },
                            onCounty = { navController.navigate(CountyDetailRoute(it)) },
                        )
                    }
                    composable<CountyDetailRoute> { entry ->
                        CountyDetailScreen(
                            countyId = entry.toRoute<CountyDetailRoute>().id,
                            onBack = { navController.popBackStack() },
                            onCounty = { navController.navigate(CountyDetailRoute(it)) },
                        )
                    }
                    composable<NeighboursRoute> {
                        NeighboursScreen(
                            onBack = { navController.popBackStack() },
                            onCounty = { navController.navigate(CountyDetailRoute(it)) },
                        )
                    }
                    composable<CitiesRoute> { CitiesScreen(onBack = { navController.popBackStack() }) }
                    composable<InfoRoute> { entry ->
                        val page = runCatching { InfoPage.valueOf(entry.toRoute<InfoRoute>().page) }.getOrDefault(InfoPage.Faq)
                        InfoScreen(page, onBack = { navController.popBackStack() })
                    }
                }
                composable<ResultsRoute> { ResultsScreen() }
                composable<SettingsRoute> { SettingsScreen() }
            }
        }
    }
}

private fun NavDestination?.isOn(dest: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(dest.route::class) } == true

/** On its side the phone is a game board: hide the status and navigation bars (swipe to peek). */
@Composable
private fun ImmersiveInLandscape(landscape: Boolean) {
    val view = LocalView.current
    DisposableEffect(landscape) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (landscape) controller.hide(WindowInsetsCompat.Type.systemBars())
            else controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
