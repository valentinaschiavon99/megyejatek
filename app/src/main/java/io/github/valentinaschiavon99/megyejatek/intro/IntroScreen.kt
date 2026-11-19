package io.github.valentinaschiavon99.megyejatek.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.valentinaschiavon99.megyejatek.R
import io.github.valentinaschiavon99.megyejatek.core.ui.theme.Amber
import io.github.valentinaschiavon99.megyejatek.core.ui.theme.Ink
import io.github.valentinaschiavon99.megyejatek.core.ui.theme.Paper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Opening screen: the pin drops in, the title fades up, then "a game made by tina"
 * types itself out. Tap anywhere to skip.
 */
@Composable
fun IntroScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val finish by rememberUpdatedState(onFinished)
    val pin = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val typing = remember { Animatable(0f) }
    val byline = stringResource(R.string.intro_byline)

    LaunchedEffect(Unit) {
        launch { pin.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        delay(250)
        title.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        typing.animateTo(1f, tween(byline.length * 55, easing = LinearEasing))
        delay(900)
        finish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() }
            .semantics { contentDescription = byline }
            .testTag("intro"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer {
                        alpha = pin.value
                        translationY = (1f - pin.value) * -60.dp.toPx()
                        val s = 0.8f + 0.2f * pin.value
                        scaleX = s
                        scaleY = s
                    },
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Paper,
                modifier = Modifier.graphicsLayer {
                    alpha = title.value
                    translationY = (1f - title.value) * 16.dp.toPx()
                },
            )
            // Keep the full width reserved so the line doesn't jump while it types.
            Box(contentAlignment = Alignment.CenterStart) {
                Text(byline, style = bylineStyle(), color = Amber.copy(alpha = 0f))
                Text(
                    text = byline.take((typing.value * byline.length).roundToInt()),
                    style = bylineStyle(),
                    color = Amber,
                )
            }
        }
    }
}

@Composable
private fun bylineStyle() = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.5.sp)
