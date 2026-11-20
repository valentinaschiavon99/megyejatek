package io.github.valentinaschiavon99.megyejatek.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.valentinaschiavon99.megyejatek.R
import kotlinx.serialization.Serializable

@Serializable data object PlayRoute
@Serializable data object ResultsRoute
@Serializable data object SettingsRoute

/** The Tanulj tab is a nested graph, so its sub-screens keep the tab selected. */
@Serializable data object LearnGraph
@Serializable data object LearnRoute
@Serializable data object CountyListRoute
@Serializable data class CountyDetailRoute(val id: String)
@Serializable data object NeighboursRoute
@Serializable data object CitiesRoute
@Serializable data class InfoRoute(val page: String)

enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    Play(PlayRoute, R.string.nav_play, Icons.Outlined.Map),
    Learn(LearnGraph, R.string.nav_learn, Icons.AutoMirrored.Outlined.MenuBook),
    Results(ResultsRoute, R.string.nav_results, Icons.Outlined.EmojiEvents),
    Settings(SettingsRoute, R.string.nav_settings, Icons.Outlined.Settings),
}
