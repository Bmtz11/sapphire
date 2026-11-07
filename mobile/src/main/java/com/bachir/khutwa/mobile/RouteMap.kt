package com.bachir.khutwa.mobile

import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.bachir.khutwa.shared.Mode
import com.bachir.khutwa.shared.Workout
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

const val WALK_ARGB = 0xFF4FC3F7.toInt()
const val RUN_ARGB = 0xFFFF7A1A.toInt()

/** Route drawn per segment: blue = walk, orange = run. */
@Composable
fun RouteMap(w: Workout, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val map = remember {
        Configuration.getInstance().userAgentValue = ctx.packageName
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        }
    }
    DisposableEffect(Unit) { map.onResume(); onDispose { map.onPause(); map.onDetach() } }

    AndroidView(factory = { map }, modifier = modifier, update = { mv ->
        mv.overlays.clear()
        val all = w.points.map { GeoPoint(it.lat, it.lon) }
        if (all.size < 2) return@AndroidView

        val segs = w.segments.ifEmpty { listOf(com.bachir.khutwa.shared.Segment(Mode.WALK, w.startMs, w.endMs, w.distanceM)) }
        segs.forEach { s ->
            // include the last point before the segment so lines connect without gaps
            val firstIdx = w.points.indexOfFirst { it.t >= s.startMs }.let { if (it > 0) it - 1 else 0 }
            val pts = w.points.drop(firstIdx).takeWhile { it.t <= s.endMs }.map { GeoPoint(it.lat, it.lon) }
            if (pts.size < 2) return@forEach
            mv.overlays.add(Polyline(mv).apply {
                setPoints(pts)
                outlinePaint.color = if (s.mode == Mode.RUN) RUN_ARGB else WALK_ARGB
                outlinePaint.strokeWidth = 12f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                outlinePaint.isAntiAlias = true
            })
        }
        val box = BoundingBox.fromGeoPoints(all)
        mv.post { mv.zoomToBoundingBox(box.increaseByScale(1.2f), false) }
        mv.invalidate()
    })
}
