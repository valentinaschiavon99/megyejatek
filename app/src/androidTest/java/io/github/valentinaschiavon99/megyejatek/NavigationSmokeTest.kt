package io.github.valentinaschiavon99.megyejatek

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.valentinaschiavon99.megyejatek.core.ui.theme.MegyejatekTheme
import io.github.valentinaschiavon99.megyejatek.navigation.AppNavHost
import org.junit.Rule
import org.junit.Test

class NavigationSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun bottomTabsSwitchScreens() {
        compose.setContent { MegyejatekTheme { AppNavHost() } }

        compose.onNodeWithText("Tanulj").performClick()
        compose.onNodeWithText("Tanulj, mielőtt játszol: hamarosan.").assertExists()

        compose.onNodeWithText("Eredmények").performClick()
        compose.onNodeWithText("Még nincs eredmény.").assertExists()
    }
}
