package com.bachir.khutwa.mobile

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
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
            val set by Settings.state.collectAsState()
            val sysDark = isSystemInDarkTheme()
            val dark = when (set.theme) { 1 -> false; 2 -> true; else -> sysDark }
            val palette = if (dark) DarkPalette else LightPalette
            LaunchedEffect(dark) {
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark
            }
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalPalette provides palette,
            ) {
                MaterialTheme(
                    colorScheme = if (dark) darkColorScheme(primary = WalkBlue, background = palette.bg, surface = palette.card)
                    else lightColorScheme(primary = palette.text, background = palette.bg, surface = palette.card),
                ) { App(::keepScreen) }
            }
        }
    }

    private fun keepScreen(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

private enum class Screen { HOME, START, COUNTDOWN, STATS, SETTINGS, DETAIL, LIVE }

private fun runtimePermissions() = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
private fun App(keepScreen: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val live by LiveSession.stats.collectAsState()
    val workouts by WorkoutStore.workouts.collectAsState()
    val today by TodayReader.today.collectAsState()
    val week by TodayReader.week.collectAsState()
    val activeDays by TodayReader.activeDays.collectAsState()
    val set by Settings.state.collectAsState()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var openId by remember { mutableStateOf<String?>(null) }
    var pendingType by remember { mutableStateOf(WorkoutType.WALK) }
    var pendingGoal by remember { mutableIntStateOf(0) }
    var hcGranted by remember { mutableStateOf(true) }

    fun refresh() = scope.launch {
        WorkoutStore.refresh(ctx)
        hcGranted = HealthSync.allGranted(ctx)
        TodayReader.refresh(ctx)
        if (HealthSync.granted(ctx)) WorkoutStore.syncPending(ctx)
    }

    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { refresh() }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            if (set.countdown) screen = Screen.COUNTDOWN
            else { PhoneWorkoutService.start(ctx, pendingType, pendingGoal); screen = Screen.HOME }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    LaunchedEffect(live != null, set.keepScreenOn) { keepScreen(live != null && set.keepScreenOn) }
    // after a workout ends, refresh the numbers
    LaunchedEffect(live == null) { if (live == null) refresh() }

    val current = when {
        live != null -> Screen.LIVE
        screen == Screen.DETAIL && openId?.let { WorkoutStore.get(it) } == null -> Screen.HOME
        else -> screen
    }

    Box(Modifier.fillMaxSize().background(LocalPalette.current.bg)) {
        AnimatedContent(
            current,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                if (targetState == Screen.COUNTDOWN || targetState == Screen.LIVE)
                    (fadeIn(tween(350)) + scaleIn(initialScale = 1.08f)) togetherWith fadeOut(tween(250))
                else (slideInHorizontally(tween(380)) { w -> if (forward) -w / 4 else w / 4 } + fadeIn(tween(380))) togetherWith
                    (slideOutHorizontally(tween(300)) { w -> if (forward) w / 6 else -w / 6 } + fadeOut(tween(250)))
            },
            label = "nav",
        ) { sc ->
            when (sc) {
                Screen.LIVE -> live?.let { LiveScreen(it) }
                Screen.COUNTDOWN -> CountdownScreen(pendingType) {
                    PhoneWorkoutService.start(ctx, pendingType, pendingGoal)
                    screen = Screen.HOME
                }
                Screen.START -> {
                    BackHandler { screen = Screen.HOME }
                    StartScreen(
                        onBack = { screen = Screen.HOME },
                        onStart = { type, goal -> pendingType = type; pendingGoal = goal; permLauncher.launch(runtimePermissions()) },
                    )
                }
                Screen.STATS -> {
                    BackHandler { screen = Screen.HOME }
                    StatsScreen(workouts, week, activeDays, onBack = { screen = Screen.HOME }, onOpen = { openId = it; screen = Screen.DETAIL })
                }
                Screen.SETTINGS -> {
                    BackHandler { screen = Screen.HOME }
                    SettingsScreen(onBack = { screen = Screen.HOME }, onConnectHealth = { hcLauncher.launch(HealthSync.PERMISSIONS) })
                }
                Screen.DETAIL -> {
                    val w = openId?.let { WorkoutStore.get(it) }
                    if (w != null) {
                        BackHandler { screen = Screen.HOME }
                        DetailScreen(w, onBack = { screen = Screen.HOME }, onDelete = { WorkoutStore.delete(ctx, w.id); screen = Screen.HOME })
                    }
                }
                Screen.HOME -> HomeScreen(
                    today = today,
                    workouts = workouts,
                    streak = TodayReader.streak(activeDays),
                    showConnect = HealthSync.available(ctx) && !hcGranted,
                    onConnect = { hcLauncher.launch(HealthSync.PERMISSIONS) },
                    onStart = { screen = Screen.START },
                    onOpen = { openId = it; screen = Screen.DETAIL },
                    onStats = { screen = Screen.STATS },
                    onSettings = { screen = Screen.SETTINGS },
                )
            }
        }
    }
}
