package com.bachir.khutwa.mobile

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bachir.khutwa.shared.LiveSession
import com.bachir.khutwa.shared.WorkoutType
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val palette = if (isSystemInDarkTheme()) DarkPalette else LightPalette
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalPalette provides palette,
            ) {
                MaterialTheme(
                    colorScheme = if (palette.dark) darkColorScheme(primary = WalkBlue, background = palette.bg)
                    else lightColorScheme(primary = palette.text, background = palette.bg),
                ) { App() }
            }
        }
    }
}

private enum class Screen { HOME, START }

private fun runtimePermissions() = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
private fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val live by LiveSession.stats.collectAsState()
    val workouts by WorkoutStore.workouts.collectAsState()
    val today by TodayReader.today.collectAsState()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var openId by remember { mutableStateOf<String?>(null) }
    var pendingType by remember { mutableStateOf(WorkoutType.WALK) }
    var hcGranted by remember { mutableStateOf(true) }

    fun refresh() = scope.launch {
        WorkoutStore.refresh(ctx)
        hcGranted = HealthSync.granted(ctx)
        TodayReader.refresh(ctx)
        if (hcGranted) WorkoutStore.syncPending(ctx)
    }

    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { refresh() }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            PhoneWorkoutService.start(ctx, pendingType)
            screen = Screen.HOME
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val opened = openId?.let { WorkoutStore.get(it) }
    val s = live
    when {
        s != null -> LiveScreen(s)
        opened != null -> {
            BackHandler { openId = null }
            DetailScreen(opened, onBack = { openId = null }, onDelete = { WorkoutStore.delete(ctx, opened.id); openId = null })
        }
        screen == Screen.START -> {
            BackHandler { screen = Screen.HOME }
            StartScreen(
                onBack = { screen = Screen.HOME },
                onStart = { type -> pendingType = type; permLauncher.launch(runtimePermissions()) },
            )
        }
        else -> HomeScreen(
            today = today,
            workouts = workouts,
            showConnect = HealthSync.available(ctx) && !hcGranted,
            onConnect = { hcLauncher.launch(HealthSync.PERMISSIONS) },
            onStart = { screen = Screen.START },
            onOpen = { openId = it },
        )
    }
}
