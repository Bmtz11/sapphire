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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bachir.khutwa.shared.Mode
import com.bachir.khutwa.shared.Segment
import com.bachir.khutwa.shared.Workout
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Google Map with the route drawn per segment (blue walk / orange run).
 * In the list it's a still preview; [interactive] = full gestures (used full-screen).
 */
@Composable
fun RouteMap(w: Workout, dark: Boolean, modifier: Modifier = Modifier, interactive: Boolean = false, satellite: Boolean = false) {
    val ctx = LocalContext.current
    val all = remember(w.id) { w.points.map { LatLng(it.lat, it.lon) } }
    if (all.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("ما فيه مسار GPS لهذا التمرين", style = txt(14, Color(0xFFA3A6AD))) }
        return
    }
    val bounds = remember(w.id) { LatLngBounds.builder().apply { all.forEach { include(it) } }.build() }
    val camera = rememberCameraPositionState()
    val style = remember(dark) { if (dark) MapStyleOptions.loadRawResourceStyle(ctx, R.raw.map_dark) else null }
    val segs = remember(w.id) {
        w.segments.ifEmpty { listOf(Segment(Mode.WALK, w.startMs, w.endMs, w.distanceM)) }.mapNotNull { s ->
            val first = w.points.indexOfFirst { it.t >= s.startMs }.let { if (it > 0) it - 1 else 0 }
            val pts = w.points.drop(first).takeWhile { it.t <= s.endMs }.map { LatLng(it.lat, it.lon) }
            if (pts.size < 2) null else (if (s.mode == Mode.RUN) RunOrange else WalkBlue) to pts
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        properties = MapProperties(
            mapType = if (satellite) MapType.HYBRID else MapType.NORMAL,
            mapStyleOptions = if (satellite) null else style,
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false, mapToolbarEnabled = false, compassEnabled = interactive,
            scrollGesturesEnabled = interactive, zoomGesturesEnabled = interactive,
            rotationGesturesEnabled = interactive, tiltGesturesEnabled = interactive,
        ),
        onMapLoaded = { camera.move(CameraUpdateFactory.newLatLngBounds(bounds, 140)) },
    ) {
        // halo under the route
        Polyline(points = all, color = if (dark || satellite) Color(0x55FFFFFF) else Color(0x33000000), width = 26f,
            jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap())
        segs.forEach { (c, pts) ->
            Polyline(points = pts, color = c, width = 13f, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap())
        }
        Circle(center = all.first(), radius = 7.0, fillColor = Color.White, strokeColor = WalkBlue, strokeWidth = 6f)
        Circle(center = all.last(), radius = 7.0, fillColor = Color(0xFF0B0B0C), strokeColor = Color.White, strokeWidth = 6f)
    }
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
