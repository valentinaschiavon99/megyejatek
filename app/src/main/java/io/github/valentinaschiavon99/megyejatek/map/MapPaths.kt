package io.github.valentinaschiavon99.megyejatek.map

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import io.github.valentinaschiavon99.megyejatek.core.geo.GeoShape
import io.github.valentinaschiavon99.megyejatek.core.geo.Projection

/** Projected paths in content space. Built once per canvas size, then only transformed. */
class MapPaths(
    val counties: Map<String, Path>,
    val outline: Path,
    val rivers: Path,
    val lakes: Path,
) {
    companion object {
        fun build(data: MapData, projection: Projection): MapPaths = MapPaths(
            counties = data.counties.associate { it.id to shapePath(it.shape, projection) },
            outline = shapePath(data.outline, projection),
            rivers = Path().apply {
                for (line in data.rivers) {
                    if (line.lons.isEmpty()) continue
                    moveTo(projection.x(line.lons[0]), projection.y(line.lats[0]))
                    for (i in 1 until line.lons.size) lineTo(projection.x(line.lons[i]), projection.y(line.lats[i]))
                }
            },
            lakes = Path().apply {
                fillType = PathFillType.EvenOdd
                for (lake in data.lakes) addShape(lake.shape, projection)
            },
        )

        private fun shapePath(shape: GeoShape, projection: Projection) = Path().apply {
            fillType = PathFillType.EvenOdd // holes (Budapest inside Pest) stay empty
            addShape(shape, projection)
        }

        private fun Path.addShape(shape: GeoShape, projection: Projection) {
            for (polygon in shape.polygons) for (ring in polygon.rings) {
                if (ring.size < 3) continue
                moveTo(projection.x(ring.lons[0]), projection.y(ring.lats[0]))
                for (i in 1 until ring.size) lineTo(projection.x(ring.lons[i]), projection.y(ring.lats[i]))
                close()
            }
        }
    }
}
