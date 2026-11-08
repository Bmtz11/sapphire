package com.bachir.khutwa.mobile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bachir.khutwa.shared.Fmt
import com.bachir.khutwa.shared.Mode
import com.bachir.khutwa.shared.Segment
import com.bachir.khutwa.shared.TrackPoint
import com.bachir.khutwa.shared.Workout
import com.bachir.khutwa.shared.WorkoutRecorder
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
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
import java.util.Locale

/** Free vector maps — no API key, no account. */
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
/** Satellite: Esri World Imagery raster (free, attribution shown). */
private const val STYLE_SAT = """{"version":8,"sources":{"sat":{"type":"raster","tileSize":256,
"tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
"attribution":"Esri, Maxar, Earthstar Geographics"}},"layers":[{"id":"sat","type":"raster","source":"sat"}]}"""

private fun hex(c: Color) = String.format("#%06X", 0xFFFFFF and c.toArgb())

/** Slow → fast: blue, green, red. */
val SpeedSlow = Color(0xFF3D7BFF)
val SpeedMid = Color(0xFF2FD47A)
val SpeedFast = Color(0xFFFF3B30)
fun speedColor(t: Float): Color = if (t < 0.5f) lerp(SpeedSlow, SpeedMid, t * 2) else lerp(SpeedMid, SpeedFast, (t - 0.5f) * 2)

/** Smoothed speed (m/s) per point + the 10th/90th percentile range used for colouring. */
data class SpeedProfile(val speeds: List<Double>, val lo: Double, val hi: Double)

fun speedProfile(pts: List<TrackPoint>): SpeedProfile {
    if (pts.size < 2) return SpeedProfile(emptyList(), 0.0, 1.0)
    val raw = pts.indices.map { i ->
        if (i == 0) 0.0 else {
            val dt = (pts[i].t - pts[i - 1].t) / 1000.0
            if (dt <= 0) 0.0 else WorkoutRecorder.haversine(pts[i - 1], pts[i]) / dt
        }
    }
    val smooth = raw.indices.map { i -> raw.subList((i - 3).coerceAtLeast(0), (i + 4).coerceAtMost(raw.size)).average() }
    val sorted = smooth.filter { it > 0.3 }.sorted()
    if (sorted.isEmpty()) return SpeedProfile(smooth, 0.0, 1.0)
    val lo = sorted[(sorted.size * 0.1).toInt()]
    val hi = sorted[(sorted.size * 0.9).toInt().coerceAtMost(sorted.size - 1)].coerceAtLeast(lo + 0.3)
    return SpeedProfile(smooth, lo, hi)
}

/** Holds the live map + the sources we update for the replay. */
private class MapHolder {
    var map: MapLibreMap? = null
    var trail: GeoJsonSource? = null
    var dot: GeoJsonSource? = null
}

/**
 * The workout route on a real map.
 * [bySpeed] colours by speed (blue slow → green → red fast), else by walk/run.
 * [replay] 0..1 moves a marker along the route and draws the trail behind it.
 */
@Composable
fun RouteMap(
    w: Workout, dark: Boolean, modifier: Modifier = Modifier,
    interactive: Boolean = false, satellite: Boolean = false, bySpeed: Boolean = true, replay: Float? = null,
) {
    val ctx = LocalContext.current
    if (w.points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("ما فيه مسار GPS لهذا التمرين", style = txt(14, Color(0xFFA3A6AD))) }
        return
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember(dark, satellite, w.id, bySpeed) { MapHolder() }
    val map = remember(dark, satellite, w.id, bySpeed) {
        MapLibre.getInstance(ctx)
        MapView(ctx).apply { onCreate(null) }
    }
    val profile = remember(w.id) { speedProfile(w.points) }

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

    // replay: move the dot + grow the trail
    LaunchedEffect(replay, holder.trail) {
        val r = replay ?: return@LaunchedEffect
        val trail = holder.trail ?: return@LaunchedEffect
        val n = w.points.size
        val f = r.coerceIn(0f, 1f) * (n - 1)
        val i = f.toInt().coerceAtMost(n - 2)
        val k = f - i
        val a = w.points[i]; val b = w.points[i + 1]
        val lat = a.lat + (b.lat - a.lat) * k
        val lon = a.lon + (b.lon - a.lon) * k
        val upTo = w.points.take(i + 1).map { Point.fromLngLat(it.lon, it.lat) } + Point.fromLngLat(lon, lat)
        trail.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(if (upTo.size >= 2) upTo else upTo + upTo)))
        holder.dot?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
        if (interactive) holder.map?.moveCamera(CameraUpdateFactory.newLatLng(LatLng(lat, lon)))
    }

    key(map) {
        AndroidView(factory = {
            map.getMapAsync { m ->
                m.uiSettings.setAllGesturesEnabled(interactive)
                m.uiSettings.isLogoEnabled = false
                m.uiSettings.isCompassEnabled = interactive
                val builder = if (satellite) Style.Builder().fromJson(STYLE_SAT) else Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)
                m.setStyle(builder) { style -> drawRoute(style, w, profile, bySpeed, dark || satellite, holder); holder.map = m; fit(m, w) }
            }
            map
        }, modifier = modifier)
    }
}

private fun fit(m: MapLibreMap, w: Workout) {
    val latLngs = w.points.map { LatLng(it.lat, it.lon) }.distinct()
    if (latLngs.size >= 2) m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(latLngs).build(), 110))
    else m.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngs.first(), 16.0))
}

private fun drawRoute(style: Style, w: Workout, sp: SpeedProfile, bySpeed: Boolean, onDark: Boolean, holder: MapHolder) {
    val pts = w.points
    val all = pts.map { Point.fromLngLat(it.lon, it.lat) }
    val features = mutableListOf<Feature>()
    if (bySpeed && sp.speeds.size == pts.size) {
        // group runs of points that fall in the same colour step (12 steps)
        var startIdx = 0
        fun bucket(i: Int) = (((sp.speeds[i] - sp.lo) / (sp.hi - sp.lo)).coerceIn(0.0, 1.0) * 11).toInt()
        var cur = bucket(1.coerceAtMost(pts.size - 1))
        for (i in 1 until pts.size) {
            val b = bucket(i)
            if (b != cur || i == pts.size - 1) {
                val slice = all.subList(startIdx, i + 1)
                if (slice.size >= 2) features += Feature.fromGeometry(LineString.fromLngLats(slice)).apply { addStringProperty("c", hex(speedColor(cur / 11f))) }
                startIdx = i; cur = b
            }
        }
    } else {
        val segs = w.segments.ifEmpty { listOf(Segment(Mode.WALK, w.startMs, w.endMs, w.distanceM)) }
        segs.forEach { s ->
            val first = pts.indexOfFirst { it.t >= s.startMs }.let { if (it > 0) it - 1 else 0 }
            val slice = pts.drop(first).takeWhile { it.t <= s.endMs }.map { Point.fromLngLat(it.lon, it.lat) }
            if (slice.size >= 2) features += Feature.fromGeometry(LineString.fromLngLats(slice)).apply {
                addStringProperty("c", hex(if (s.mode == Mode.RUN) RunOrange else WalkBlue))
            }
        }
    }
    style.addSource(GeoJsonSource("halo", Feature.fromGeometry(LineString.fromLngLats(all))))
    style.addSource(GeoJsonSource("route", FeatureCollection.fromFeatures(features)))
    val trail = GeoJsonSource("trail", Feature.fromGeometry(LineString.fromLngLats(listOf(all.first(), all.first()))))
    val dot = GeoJsonSource("dot", Feature.fromGeometry(all.first()))
    style.addSource(trail); style.addSource(dot)
    style.addSource(GeoJsonSource("ends", FeatureCollection.fromFeatures(listOf(
        Feature.fromGeometry(all.first()).apply { addStringProperty("f", "#FFFFFF"); addStringProperty("s", "#2FD47A") },
        Feature.fromGeometry(all.last()).apply { addStringProperty("f", "#0B0B0C"); addStringProperty("s", "#FFFFFF") },
    ))))
    style.addLayer(LineLayer("halo", "halo").withProperties(
        PropertyFactory.lineColor(if (onDark) "#FFFFFF" else "#000000"),
        PropertyFactory.lineOpacity(0.2f), PropertyFactory.lineWidth(11f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    ))
    style.addLayer(LineLayer("route", "route").withProperties(
        PropertyFactory.lineColor(Expression.get("c")), PropertyFactory.lineWidth(5.5f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    ))
    style.addLayer(LineLayer("trail", "trail").withProperties(
        PropertyFactory.lineColor("#FFFFFF"), PropertyFactory.lineWidth(3f), PropertyFactory.lineOpacity(0.9f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    ))
    style.addLayer(CircleLayer("ends", "ends").withProperties(
        PropertyFactory.circleRadius(6.5f), PropertyFactory.circleColor(Expression.get("f")),
        PropertyFactory.circleStrokeColor(Expression.get("s")), PropertyFactory.circleStrokeWidth(3f),
    ))
    style.addLayer(CircleLayer("dot", "dot").withProperties(
        PropertyFactory.circleRadius(9f), PropertyFactory.circleColor("#FFFFFF"),
        PropertyFactory.circleStrokeColor("#0B0B0C"), PropertyFactory.circleStrokeWidth(4f),
    ))
    holder.trail = trail
    holder.dot = dot
}

/** Speed legend: blue slow → red fast, with the real km/h range. */
@Composable
fun SpeedLegend(w: Workout, modifier: Modifier = Modifier) {
    val sp = remember(w.id) { speedProfile(w.points) }
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xBF070708)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(String.format(Locale.US, "%.0f", sp.lo * 3.6), style = numStyle(11.sp, Color.White, FontWeight.Bold))
        Box(Modifier.size(60.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(SpeedSlow, SpeedMid, SpeedFast))))
        Text(String.format(Locale.US, "%.0f كم/س", sp.hi * 3.6), style = numStyle(11.sp, Color.White, FontWeight.Bold))
    }
}

/** Replay state: progress 0..1 and play/pause, ~12 s for the whole route (× speed). */
class ReplayState {
    var progress by mutableFloatStateOf(0f)
    var playing by mutableStateOf(false)
    var speed by mutableStateOf(1)
    var active by mutableStateOf(false)
}

@Composable
fun rememberReplay(): ReplayState {
    val st = remember { ReplayState() }
    val anim = remember { Animatable(0f) }
    LaunchedEffect(st.playing, st.speed) {
        if (st.playing) {
            if (st.progress >= 1f) st.progress = 0f
            anim.snapTo(st.progress)
            val ms = ((1f - st.progress) * 12_000 / st.speed).toInt().coerceAtLeast(1)
            anim.animateTo(1f, tween(ms, easing = LinearEasing)) { st.progress = value }
            st.playing = false
        }
    }
    return st
}

/** Play / speed / progress bar for the replay. */
@Composable
fun ReplayBar(st: ReplayState, w: Workout, modifier: Modifier = Modifier) {
    val elapsed = (w.activeMs * st.progress).toLong()
    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xE6141518)).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color.White).bounceClick { st.active = true; st.playing = !st.playing },
            contentAlignment = Alignment.Center,
        ) { Icon(if (st.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "تشغيل المسار", tint = Color(0xFF0B0B0C)) }
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(st.progress).height(5.dp).background(Color.White))
            }
            Text(Fmt.duration(elapsed), style = numStyle(12.sp, Color(0xFFA3A6AD), FontWeight.SemiBold), modifier = Modifier.padding(top = 3.dp))
        }
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f))
                .bounceClick { st.speed = when (st.speed) { 1 -> 2; 2 -> 4; else -> 1 } }.padding(horizontal = 10.dp, vertical = 6.dp)
        ) { Text("${st.speed}x", style = numStyle(13.sp, Color.White, FontWeight.Bold)) }
    }
}

/** Full-screen map with gestures, satellite, colour mode and replay. */
@Composable
fun FullMap(w: Workout, dark: Boolean, startReplay: Boolean, onClose: () -> Unit) {
    var sat by remember { mutableStateOf(false) }
    var bySpeed by remember { mutableStateOf(Settings.current.routeColorBySpeed) }
    val replay = rememberReplay()
    LaunchedEffect(Unit) { if (startReplay) { replay.active = true; replay.playing = true } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            RouteMap(w, dark, Modifier.fillMaxSize(), interactive = true, satellite = sat, bySpeed = bySpeed,
                replay = if (replay.active) replay.progress else null)
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                MapBtn(Icons.Filled.Close, "إغلاق", false, onClose)
                Box(Modifier.weight(1f))
                ModeToggle(bySpeed) { bySpeed = it }
                MapBtn(Icons.Outlined.Layers, "قمر صناعي", sat) { sat = !sat }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (bySpeed) SpeedLegend(w)
                ReplayBar(replay, w, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MapBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(if (on) Color.White else Color(0xE6141518)).bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (on) Color(0xFF0B0B0C) else Color.White) }
}

@Composable
fun ModeToggle(bySpeed: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xE6141518)).padding(3.dp)) {
        listOf("السرعة" to true, "مشي/جري" to false).forEach { (l, v) ->
            Box(
                Modifier.clip(RoundedCornerShape(13.dp)).background(if (bySpeed == v) Color.White else Color.Transparent)
                    .bounceClick { onChange(v) }.padding(horizontal = 12.dp, vertical = 8.dp)
            ) { Text(l, style = txt(12, if (bySpeed == v) Color(0xFF0B0B0C) else Color.White, FontWeight.Medium)) }
        }
    }
}
