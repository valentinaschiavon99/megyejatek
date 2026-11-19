package io.github.valentinaschiavon99.megyejatek

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
/** Runs against a build without google-services.json, where no login is required. */
class NavigationSmokeTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun introThenBottomTabsSwitchScreens() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("intro").fetchSemanticsNodes().isEmpty() }

        compose.waitUntil(10_000) { compose.onAllNodesWithTag("hungary-map").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Tanulj").performClick()
        compose.onNodeWithText("Tanulj, mielőtt játszol: hamarosan.").assertExists()

        compose.onNodeWithText("Eredmények").performClick()
        compose.onNodeWithText("Saját rekordjaim").assertExists()
    }
}
