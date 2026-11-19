package io.github.valentinaschiavon99.megyejatek

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.valentinaschiavon99.megyejatek.core.ui.theme.MegyejatekTheme
import io.github.valentinaschiavon99.megyejatek.intro.IntroScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IntroScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tapSkipsIntro() {
        var finished = false
        compose.setContent { MegyejatekTheme { IntroScreen(onFinished = { finished = true }) } }
        compose.onNodeWithTag("intro").performClick()
        assertTrue(finished)
    }

    @Test
    fun introFinishesOnItsOwn() {
        var finished = false
        compose.setContent { MegyejatekTheme { IntroScreen(onFinished = { finished = true }) } }
        compose.mainClock.advanceTimeBy(5_000)
        assertTrue(finished)
    }
}
