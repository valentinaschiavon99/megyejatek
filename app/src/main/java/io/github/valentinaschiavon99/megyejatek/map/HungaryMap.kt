package io.github.valentinaschiavon99.megyejatek.map

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import io.github.valentinaschiavon99.megyejatek.core.geo.Projection
import io.github.valentinaschiavon99.megyejatek.core.ui.theme.LocalMapColors
import io.github.valentinaschiavon99.megyejatek.core.ui.theme.MapColors

/**
 * Blind map of Hungary drawn on a Canvas.
 *
 * Pinch to zoom (around the fingers), drag to pan, double-tap to reset. A tap first looks for a
 * city dot within [cityTapTolerance] (only if [onCityTap] is set and cities are shown), otherwise
 * it reports the county under the finger.
 */
@Composable
fun HungaryMap(
    data: MapData,
    modifier: Modifier = Modifier,
    countyStates: Map<String, CountyVisual> = emptyMap(),
    cityStates: Map<String, CountyVisual> = emptyMap(),
    showCities: Boolean = false,
    visibleCityIds: Set<String>? = null,
    blindMap: Boolean = false,
    /** County names. Off in every game mode, where they would give the answer away. */
    showLabels: Boolean = !blindMap,
    onCountyTap: ((countyId: String) -> Unit)? = null,
    onCityTap: ((cityId: String) -> Unit)? = null,
    onMapTap: ((lon: Double, lat: Double, countyId: String?) -> Unit)? = null,
    guess: MapGuess? = null,
    cityTapTolerance: Dp = 22.dp,
) {
    val colors = LocalMapColors.current
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var viewport by remember { mutableStateOf(MapViewport.Identity) }

    val paddingPx = with(density) { HungaryMapDefaults.ContentPadding.toPx() }
    val projection = remember(data, size) {
        if (size.width > 0 && size.height > 0) {
            Projection(data.bounds, size.width.toFloat(), size.height.toFloat(), paddingPx)
        } else null
    }
    val paths = remember(projection) { projection?.let { MapPaths.build(data, it) } }
    val hitTester = remember(data) { MapHitTester(data) }
    val textMeasurer = rememberTextMeasurer()

    val fills = HashMap<String, State<Color>>(data.counties.size)
    for (c in data.counties) {
        key(c.id) {
            fills[c.id] = animateColorAsState(
                targetValue = colors.fillFor(countyStates[c.id]),
                animationSpec = tween(220),
                label = "fill-${c.id}",
            )
        }
    }

    val currentProjection by rememberUpdatedState(projection)
    val onCounty by rememberUpdatedState(onCountyTap)
    val onCity by rememberUpdatedState(onCityTap)
    val onMap by rememberUpdatedState(onMapTap)
    val citiesTappable by rememberUpdatedState(showCities && onCityTap != null)
    val tappableCities by rememberUpdatedState(visibleCityIds)
    val tolerancePx = with(density) { cityTapTolerance.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("hungary-map")
            .onSizeChanged {
                size = it
                viewport = viewport.clamped(it.width.toFloat(), it.height.toFloat())
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    viewport = viewport.transformed(
                        zoom, pan.x, pan.y, centroid.x, centroid.y,
                        size.width.toFloat(), size.height.toFloat(),
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { viewport = MapViewport.Identity },
                    onTap = { pos ->
                        val proj = currentProjection ?: return@detectTapGestures
                        if (citiesTappable) {
                            val city = hitTester.cityAt(proj, viewport, pos.x, pos.y, tolerancePx, tappableCities)
                            if (city != null) {
                                onCity?.invoke(city)
                                return@detectTapGestures
                            }
                        }
                        val cx = viewport.toContentX(pos.x)
                        val cy = viewport.toContentY(pos.y)
                        val county = hitTester.countyAt(proj, cx, cy)
                        onMap?.invoke(proj.lon(cx), proj.lat(cy), county)
                        if (county != null) onCounty?.invoke(county)
                    },
                )
            },
    ) {
        val p = paths ?: return@Canvas
        val proj = projection ?: return@Canvas
        val s = viewport.scale
        val thin = 0.8.dp.toPx() / s
        val river = 1.3.dp.toPx() / s
        val border = 1.8.dp.toPx() / s

        withTransform({
            translate(viewport.offsetX, viewport.offsetY)
            scale(s, s, pivot = Offset.Zero)
        }) {
            for (c in data.counties) {
                drawPath(p.counties.getValue(c.id), fills.getValue(c.id).value)
            }
            drawPath(p.lakes, colors.water)
            drawPath(p.rivers, colors.water, style = Stroke(width = river, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (!blindMap) {
                for (c in data.counties) {
                    drawPath(p.counties.getValue(c.id), colors.countyBorder, style = Stroke(width = thin, join = StrokeJoin.Round))
                }
            }
            drawPath(p.outline, colors.nationalBorder, style = Stroke(width = border, join = StrokeJoin.Round))
        }

        // Dots and labels are drawn in screen space so they keep their size while zooming.
        if (showCities) {
            val r = 3.5.dp.toPx()
            val ring = 1.2.dp.toPx()
            for (city in data.cities) {
                if (visibleCityIds != null && city.id !in visibleCityIds) continue
                val center = Offset(viewport.toScreenX(proj.x(city.lon)), viewport.toScreenY(proj.y(city.lat)))
                val radius = if (city.isSeat) r * 1.3f else r
                drawCircle(colors.land, radius + ring, center)
                drawCircle(colors.dotFor(cityStates[city.id]), radius, center)
            }
        }
        if (guess != null) {
            val from = Offset(viewport.toScreenX(proj.x(guess.tapLon)), viewport.toScreenY(proj.y(guess.tapLat)))
            val to = Offset(viewport.toScreenX(proj.x(guess.targetLon)), viewport.toScreenY(proj.y(guess.targetLat)))
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            drawLine(colors.label, from, to, strokeWidth = 1.5.dp.toPx(), pathEffect = dash, cap = StrokeCap.Round)
            drawCircle(colors.land, 6.dp.toPx(), from)
            drawCircle(colors.wrong, 4.5.dp.toPx(), from)
            drawCircle(colors.land, 7.dp.toPx(), to)
            drawCircle(colors.correct, 5.5.dp.toPx(), to)
        }
        if (showLabels && !blindMap) {
            val style = TextStyle(color = colors.label, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            for (c in data.counties) {
                val layout = textMeasurer.measure(c.name, style)
                val x = viewport.toScreenX(proj.x(c.info.label[0])) - layout.size.width / 2f
                val y = viewport.toScreenY(proj.y(c.info.label[1])) - layout.size.height / 2f
                drawText(layout, topLeft = Offset(x, y))
            }
        }
    }
}

/** Városok: where the player tapped and where the city really is. */
data class MapGuess(val tapLon: Double, val tapLat: Double, val targetLon: Double, val targetLat: Double)

object HungaryMapDefaults {
    /** Space between the canvas edge and the country at zoom 1. */
    val ContentPadding: Dp = 12.dp
}

private fun MapColors.fillFor(state: CountyVisual?): Color = when (state) {
    null, CountyVisual.Neutral -> land
    CountyVisual.Highlighted -> highlighted
    CountyVisual.Correct -> correct
    CountyVisual.Wrong -> wrong
    CountyVisual.Hinted -> hinted
}

private fun MapColors.dotFor(state: CountyVisual?): Color = when (state) {
    null, CountyVisual.Neutral -> label
    else -> fillFor(state)
}
