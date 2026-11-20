package io.github.valentinaschiavon99.megyejatek.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LinearScale
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.valentinaschiavon99.megyejatek.core.ui.theme.LocalMapColors
import io.github.valentinaschiavon99.megyejatek.game.engine.GameMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

val GameMode.icon: ImageVector
    get() = when (this) {
        GameMode.Megyek -> Icons.Outlined.Map
        GameMode.Szekhelyek -> Icons.Outlined.LocationCity
        GameMode.Szomszedok -> Icons.Outlined.Groups
        GameMode.Varosok -> Icons.Outlined.PinDrop
        GameMode.Felsorolas -> Icons.Outlined.LinearScale
        GameMode.Attekintes -> Icons.Outlined.Explore
    }

/** A rounded pill with an icon and a short value, used in the game header. */
@Composable
fun StatPill(
    icon: ImageVector?,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    text: @Composable () -> Unit,
) {
    Row(
        modifier
            .semantics(mergeDescendants = true) {}
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        text()
    }
}

/** A number that counts up or down to its new value instead of jumping. */
@Composable
fun AnimatedNumber(value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier, suffix: String = "") {
    val shown by animateIntAsState(value, animationSpec = tween(450, easing = FastOutSlowInEasing), label = "number")
    Text("$shown$suffix", style = style, color = color, modifier = modifier)
}

/** "×3" badge that pops when the multiplier grows. */
@Composable
fun MultiplierBadge(multiplier: Int, label: String, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(multiplier) {
        if (multiplier > 1) {
            scale.snapTo(1.35f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    val hot = multiplier > 1
    StatPill(
        icon = null,
        container = if (hot) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondary,
        modifier = modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (hot) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** "+200" that floats up and fades whenever a new award arrives. */
@Composable
fun PointsPopup(points: Int?, key: Long, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(key) {
        if (points != null && points > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(900, easing = LinearEasing))
        }
    }
    if (points == null || points <= 0 || progress.value >= 1f) return
    Text(
        "+$points",
        style = MaterialTheme.typography.titleMedium,
        color = LocalMapColors.current.correct,
        modifier = modifier.graphicsLayer {
            translationY = -28.dp.toPx() * progress.value
            alpha = 1f - progress.value * progress.value
            val s = 0.8f + 0.4f * (1f - (progress.value - 0.25f).coerceAtLeast(0f))
            scaleX = s
            scaleY = s
        },
    )
}

/** 0–3 stars, popping in one after the other. */
@Composable
fun StarRating(stars: Int, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val gold = MaterialTheme.colorScheme.secondary
    val empty = MaterialTheme.colorScheme.outlineVariant
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until 3) {
            val scale = remember { Animatable(0f) }
            LaunchedEffect(stars) {
                delay(250L + i * 220L)
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
            Icon(
                Icons.Rounded.Star,
                contentDescription = null,
                tint = if (i < stars) gold else empty,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        rotationZ = if (i == 1) 0f else if (i == 0) -12f else 12f
                    },
            )
        }
    }
}

/** A short burst of falling paper confetti in the map colours. */
@Composable
fun Confetti(modifier: Modifier = Modifier, pieces: Int = 70) {
    val colors = LocalMapColors.current
    val palette = listOf(colors.correct, colors.highlighted, colors.hinted, colors.water, MaterialTheme.colorScheme.secondary)
    val seeds = remember {
        List(pieces) {
            floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 360f, Random.nextFloat())
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { launch { t.animateTo(1f, tween(2600, easing = LinearEasing)) } }
    if (t.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        for ((i, s) in seeds.withIndex()) {
            val start = -0.1f - s[1] * 0.3f
            val y = (start + t.value * (1.2f + s[2] * 0.4f)) * size.height
            val x = (s[0] + 0.05f * sin((t.value * 6f + s[4] * 6f).toDouble()).toFloat()) * size.width
            val w = 6.dp.toPx() + s[2] * 6.dp.toPx()
            rotate(s[3] + t.value * 540f * (if (i % 2 == 0) 1 else -1), pivot = Offset(x, y)) {
                drawRect(
                    palette[i % palette.size].copy(alpha = 1f - t.value * 0.6f),
                    topLeft = Offset(x - w / 2, y - w / 4),
                    size = Size(w, w / 2),
                )
            }
        }
    }
}

/** A round icon badge, e.g. on the mode start card. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size * 0.5f))
    }
}

/** Slides the new text in from below whenever [text] changes (next question). */
@Composable
fun SlidingText(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 2) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(260))) togetherWith
                (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(160)))
        },
        label = "prompt",
        // Merged so tests (and TalkBack) read the current prompt as one node.
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) { t ->
        Text(t, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** Hearts for the lives left in the round; the one just lost shakes and fades. */
@Composable
fun Lives(left: Int, max: Int, label: String, modifier: Modifier = Modifier) {
    val red = LocalMapColors.current.wrong
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (i in 0 until max) {
            val alive = i < left
            val shake = remember { Animatable(0f) }
            LaunchedEffect(alive) {
                if (!alive) {
                    for (x in listOf(1f, -1f, 0.6f, -0.6f, 0f)) shake.animateTo(x, tween(55))
                }
            }
            val scale by animateFloatAsState(if (alive) 1f else 0.85f, label = "heart")
            Icon(
                if (alive) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                tint = if (alive) red else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        translationX = shake.value * 4.dp.toPx()
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
    }
}
