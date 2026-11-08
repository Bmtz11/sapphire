package com.bachir.khutwa.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bachir.khutwa.shared.Mode
import com.bachir.khutwa.shared.Segment
import com.bachir.khutwa.shared.Workout
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Free vector maps — no API key, no account. */
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
/** Satellite: Esri World Imagery raster (free, attribution shown). */
private const val STYLE_SAT = """{"version":8,"sources":{"sat":{"type":"raster","tileSize":256,
"tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
"attribution":"Esri, Maxar, Earthstar Geographics"}},"layers":[{"id":"sat","type":"raster","source":"sat"}]}"""

private fun hex(c: Color) = String.format("#%06X", 0xFFFFFF and c.toArgb())

/**
 * The workout route on a real map, drawn per segment (blue walk / orange run).
 * In the list it's a still preview; [interactive] = full gestures (used full-screen).
 */
@Composable
fun RouteMap(w: Workout, dark: Boolean, modifier: Modifier = Modifier, interactive: Boolean = false, satellite: Boolean = false) {
    val ctx = LocalContext.current
    if (w.points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("ما فيه مسار GPS لهذا التمرين", style = txt(14, Color(0xFFA3A6AD))) }
        return
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember(dark, satellite, w.id) {
        MapLibre.getInstance(ctx)
        MapView(ctx).apply { onCreate(null) }
    }
    DisposableEffect(map, lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> map.onStart()
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                Lifecycle.Event.ON_STOP -> map.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        map.onStart(); map.onResume()
        onDispose { lifecycle.removeObserver(obs); map.onPause(); map.onStop(); map.onDestroy() }
    }

    key(map) { AndroidView(factory = {
        map.getMapAsync { m ->
            m.uiSettings.setAllGesturesEnabled(interactive)
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isCompassEnabled = interactive
            val builder = if (satellite) Style.Builder().fromJson(STYLE_SAT) else Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)
            m.setStyle(builder) { style ->
                val all = w.points.map { Point.fromLngLat(it.lon, it.lat) }
                val segs = w.segments.ifEmpty { listOf(Segment(Mode.WALK, w.startMs, w.endMs, w.distanceM)) }
                val features = mutableListOf<Feature>()
                segs.forEach { s ->
                    val first = w.points.indexOfFirst { it.t >= s.startMs }.let { if (it > 0) it - 1 else 0 }
                    val pts = w.points.drop(first).takeWhile { it.t <= s.endMs }.map { Point.fromLngLat(it.lon, it.lat) }
                    if (pts.size >= 2) features += Feature.fromGeometry(LineString.fromLngLats(pts)).apply {
                        addStringProperty("c", hex(if (s.mode == Mode.RUN) RunOrange else WalkBlue))
                    }
                }
                style.addSource(GeoJsonSource("halo", Feature.fromGeometry(LineString.fromLngLats(all))))
                style.addSource(GeoJsonSource("route", FeatureCollection.fromFeatures(features)))
                style.addSource(GeoJsonSource("ends", FeatureCollection.fromFeatures(listOf(
                    Feature.fromGeometry(all.first()).apply { addStringProperty("f", "#FFFFFF"); addStringProperty("s", hex(WalkBlue)) },
                    Feature.fromGeometry(all.last()).apply { addStringProperty("f", "#0B0B0C"); addStringProperty("s", "#FFFFFF") },
                ))))
                style.addLayer(LineLayer("halo", "halo").withProperties(
                    PropertyFactory.lineColor(if (dark || satellite) "#FFFFFF" else "#000000"),
                    PropertyFactory.lineOpacity(0.22f), PropertyFactory.lineWidth(11f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ))
                style.addLayer(LineLayer("route", "route").withProperties(
                    PropertyFactory.lineColor(Expression.get("c")), PropertyFactory.lineWidth(5.5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ))
                style.addLayer(CircleLayer("ends", "ends").withProperties(
                    PropertyFactory.circleRadius(6.5f), PropertyFactory.circleColor(Expression.get("f")),
                    PropertyFactory.circleStrokeColor(Expression.get("s")), PropertyFactory.circleStrokeWidth(3f),
                ))

                val latLngs = w.points.map { LatLng(it.lat, it.lon) }.distinct()
                if (latLngs.size >= 2) {
                    val bounds = LatLngBounds.Builder().includes(latLngs).build()
                    m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                } else m.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngs.first(), 16.0))
            }
        }
        map
    }, modifier = modifier) }
}

/** Full-screen map with gestures and a satellite toggle. */
@Composable
fun FullMap(w: Workout, dark: Boolean, onClose: () -> Unit) {
    var sat by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            RouteMap(w, dark, Modifier.fillMaxSize(), interactive = true, satellite = sat)
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(48.dp).clip(CircleShape)
                    .background(Color(0xE6141518)).bounceClick(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "إغلاق", tint = Color.White) }
            Box(
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp).size(48.dp).clip(CircleShape)
                    .background(if (sat) Color.White else Color(0xE6141518)).bounceClick { sat = !sat },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Layers, "قمر صناعي", tint = if (sat) Color(0xFF0B0B0C) else Color.White) }
        }
    }
}
