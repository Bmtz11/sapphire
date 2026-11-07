package com.bachir.khutwa.mobile

import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.bachir.khutwa.shared.Mode
import com.bachir.khutwa.shared.Segment
import com.bachir.khutwa.shared.Workout
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.File

const val WALK_ARGB = 0xFF4FA3F7.toInt()
const val RUN_ARGB = 0xFFFF7A1A.toInt()

/** CARTO basemaps: real streets, a dark and a light style, no API key. */
private fun cartoTiles(dark: Boolean) = XYTileSource(
    if (dark) "CartoDark" else "CartoLight", 1, 20, 256, "@2x.png",
    arrayOf("a", "b", "c", "d").map { "https://$it.basemaps.cartocdn.com/${if (dark) "dark_all" else "light_all"}/" }.toTypedArray(),
    "© OpenStreetMap © CARTO",
)

/** Route drawn per segment: blue = walk, orange = run. */
@Composable
fun RouteMap(w: Workout, dark: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val map = remember(dark) {
        Configuration.getInstance().apply {
            userAgentValue = "Sapphire/1.0 (${ctx.packageName})"
            osmdroidBasePath = File(ctx.cacheDir, "osm")
            osmdroidTileCache = File(ctx.cacheDir, "osm/tiles")
        }
        MapView(ctx).apply {
            setTileSource(cartoTiles(dark))
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            // keep the list from stealing the map's drag gestures
            setOnTouchListener { v, e ->
                v.parent?.requestDisallowInterceptTouchEvent(e.action != MotionEvent.ACTION_UP && e.action != MotionEvent.ACTION_CANCEL)
                false
            }
        }
    }
    DisposableEffect(map) { map.onResume(); onDispose { map.onPause(); map.onDetach() } }

    AndroidView(factory = { map }, modifier = modifier, update = { mv ->
        mv.overlays.clear()
        val all = w.points.map { GeoPoint(it.lat, it.lon) }
        if (all.size < 2) return@AndroidView

        val segs = w.segments.ifEmpty { listOf(Segment(Mode.WALK, w.startMs, w.endMs, w.distanceM)) }
        // soft glow under the line
        mv.overlays.add(Polyline(mv).apply {
            setPoints(all)
            outlinePaint.color = if (dark) 0x33FFFFFF else 0x33000000
            outlinePaint.strokeWidth = 22f
            outlinePaint.strokeCap = Paint.Cap.ROUND
        })
        segs.forEach { s ->
            val firstIdx = w.points.indexOfFirst { it.t >= s.startMs }.let { if (it > 0) it - 1 else 0 }
            val pts = w.points.drop(firstIdx).takeWhile { it.t <= s.endMs }.map { GeoPoint(it.lat, it.lon) }
            if (pts.size < 2) return@forEach
            mv.overlays.add(Polyline(mv).apply {
                setPoints(pts)
                outlinePaint.color = if (s.mode == Mode.RUN) RUN_ARGB else WALK_ARGB
                outlinePaint.strokeWidth = 11f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                outlinePaint.isAntiAlias = true
            })
        }
        val box = BoundingBox.fromGeoPoints(all)
        mv.post { mv.zoomToBoundingBox(box.increaseByScale(1.35f), false) }
        mv.invalidate()
    })
}
