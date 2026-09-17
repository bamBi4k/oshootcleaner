package io.github.bambi4k.oshootcleaner

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class AppTab(val labelRes: Int, val icon: ImageVector) {
    DASHBOARD(R.string.tab_dashboard, Icons.Filled.Dashboard),
    ANALYSIS(R.string.tab_analysis, Icons.Filled.Search),
    PERFORMANCE(R.string.tab_performance, Icons.Filled.Speed),
    STORAGE(R.string.tab_storage, Icons.Filled.Storage),
    BATTERY(R.string.tab_battery, Icons.Filled.BatteryFull),
    PERMISSIONS(R.string.tab_permissions, Icons.Filled.Lock),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val tag = LanguageStore.get(newBase)
        if (tag == LanguageStore.SYSTEM) {
            super.attachBaseContext(newBase)
            return
        }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            installSplashScreen()
        }
        super.onCreate(savedInstanceState)
        // Register Shizuku's listeners with the system. Must run before
        // anything else touches ShizukuManager.
        ShizukuManager.init()
        window.statusBarColor = AndroidColor.BLACK
        window.navigationBarColor = AndroidColor.BLACK

        // Defer the insets adjustment until the DecorView is attached. On a
        // fresh cold start this runs immediately; on activity relaunch (e.g.
        // after a language change triggers recreate()) the DecorView isn't
        // ready yet inside onCreate, so we post it to the next frame.
        window.decorView.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    window.insetsController?.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                } catch (_: Throwable) {
                    // DecorView not ready — bars will use theme defaults.
                }
            } else {
                @Suppress("DEPRECATION")
                var flags = window.decorView.systemUiVisibility
                flags = flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                flags = flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = flags
            }
        }

        LastFullChargeStore.startMonitoring(this)

        setContent { CleanerApp() }

        if (AutoCleanStore.isEnabled(this)) {
            AutoCleanScheduler.setEnabled(this, true, AutoCleanStore.days(this))
        }

        try {
            val dataUsageWork = PeriodicWorkRequestBuilder<DataUsageCheckWorker>(
                6, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "data_usage_check",
                ExistingPeriodicWorkPolicy.KEEP,
                dataUsageWork
            )
        } catch (_: Throwable) {
            // WorkManager not ready — retries next launch.
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CleanerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var theme by remember { mutableStateOf(ThemeStore.getSelectedTheme(context)) }

    val prefs = remember {
        context.getSharedPreferences("oh_shoot_prefs", Context.MODE_PRIVATE)
    }

    val initialTab = remember {
        val seen = prefs.getBoolean("seen_permissions", false)
        if (seen) AppTab.DASHBOARD else AppTab.PERMISSIONS
    }

    val pagerState = rememberPagerState(
        initialPage = initialTab.ordinal,
        pageCount = { AppTab.entries.size }
    )

    DisposableEffect(pagerState) {
        TabNavigator.register(pagerState, scope)
        onDispose { TabNavigator.unregister() }
    }

    var pagerEnabled by remember { mutableStateOf(true) }

    var selectedTab by remember { mutableStateOf(initialTab) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                selectedTab = AppTab.entries[page]
            }
    }

    // When the accessibility service finishes an auto-clean session and
    // brings the user back to the app, jump the pager to Storage so they
    // see the completion summary in context.
    val automationState = CacheCleanAutomation.state
    LaunchedEffect(automationState.finished) {
        if (automationState.finished) {
            pagerState.animateScrollToPage(AppTab.STORAGE.ordinal)
        }
    }

    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity

        val hint = activity?.intent?.getStringExtra(EXTRA_OPEN_TAB)
        if (!hint.isNullOrEmpty()) {
            runCatching { AppTab.valueOf(hint) }.getOrNull()?.let {
                pagerState.scrollToPage(it.ordinal)
            }
            activity.intent.removeExtra(EXTRA_OPEN_TAB)
        }

        when (activity?.intent?.action) {
            "com.example.io.github.bambi4k.oshootcleaner.SHORTCUT_CLEAN" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        CleanupManager.runQuickClean(context)
                        CleanupManager.restartBackgroundApps(context) { }
                        AnalysisPrefs.markStale(context)
                        WidgetSync.refreshCleaner(context)
                    }
                }
                pagerState.scrollToPage(AppTab.DASHBOARD.ordinal)
                activity.intent.action = null
            }
            "com.example.io.github.bambi4k.oshootcleaner.SHORTCUT_ANALYSE" -> {
                pagerState.scrollToPage(AppTab.ANALYSIS.ordinal)
                activity.intent.action = null
            }
            "com.example.io.github.bambi4k.oshootcleaner.SHORTCUT_DASHBOARD" -> {
                pagerState.scrollToPage(AppTab.DASHBOARD.ordinal)
                activity.intent.action = null
            }
        }

        prefs.edit().putBoolean("seen_permissions", true).apply()
    }

    // After an activity recreation (e.g. language change), widgets may
    // have a stale locale. Sync once the new activity is stable.
    LaunchedEffect(Unit) {
        try {
            WidgetTheme.syncLanguageIfChanged(context)
        } catch (_: Throwable) {
            // Non-fatal. Widgets will sync on the next theme change.
        }
    }

    // Single entry point for widget refresh. The WidgetTheme queue
    // coalesces rapid calls and rate-limits, so we can call this from
    // every trigger without worrying about flooding the launcher.
    fun requestWidgetSync() {
        WidgetTheme.requestSync(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    android.util.Log.d("WidgetSync", "trigger: ON_START")
                    requestWidgetSync()
                }
                Lifecycle.Event.ON_STOP -> {
                    android.util.Log.d("WidgetSync", "trigger: ON_STOP")
                    requestWidgetSync()
                }
                else -> { /* no-op */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun onModeChange(mode: ThemeMode) {
        android.util.Log.d("WidgetSync", "trigger: onModeChange(${mode.name})")
        ThemeStore.setMode(context, mode)
        theme = ThemeStore.getSelectedTheme(context)
        requestWidgetSync()
    }

    fun onFlavorChange(flavor: ThemeSpec) {
        android.util.Log.d("WidgetSync", "trigger: onFlavorChange(${flavor.id})")
        if (theme.mode == ThemeMode.DAY) {
            ThemeStore.setLightFlavor(context, flavor)
        } else {
            ThemeStore.setDarkFlavor(context, flavor)
        }
        theme = flavor
        requestWidgetSync()
    }

    var showByline by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1100)
        showByline = false
    }
    val bylineAlpha by animateFloatAsState(
        targetValue = if (showByline) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "byline"
    )
    val bylineVisible = bylineAlpha > 0.01f

    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = theme.bgBase,
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.bgMantle)
                        .padding(horizontal = 2.dp, vertical = 6.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        val label = stringResource(tab.labelRes)

                        val iconTint by animateColorAsState(
                            targetValue = if (selected) theme.buttonPrimaryBg else theme.fontsSecondary,
                            animationSpec = tween(durationMillis = 200),
                            label = "tabIconTint"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (selected) theme.buttonPrimaryBg else theme.fontsSecondary,
                            animationSpec = tween(durationMillis = 200),
                            label = "tabTextColor"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    scope.launch {
                                        pagerState.animateScrollToPage(tab.ordinal)
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 1.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                tab.icon,
                                contentDescription = label,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = label,
                                color = textColor,
                                fontSize = 8.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(theme.bgBase)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startX = down.position.x
                            val startY = down.position.y
                            var decision = 0

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break
                                if (!change.pressed) {
                                    if (decision == 2) pagerEnabled = true
                                    break
                                }

                                if (decision == 0) {
                                    val dx = kotlin.math.abs(change.position.x - startX)
                                    val dy = kotlin.math.abs(change.position.y - startY)
                                    if (dx + dy > 14f) {
                                        decision = if (dx > dy * 1.5f) 1 else 2
                                        if (decision == 2) pagerEnabled = false
                                    }
                                }
                            }
                        }
                    }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = pagerEnabled
                ) { page ->
                    PageWithGlassEffect(
                        pagerState = pagerState,
                        page = page
                    ) {
                        when (AppTab.entries[page]) {
                            AppTab.DASHBOARD -> DashboardTab(theme)
                            AppTab.ANALYSIS -> AnalysisTab(theme)
                            AppTab.PERFORMANCE -> PerformanceTab(theme)
                            AppTab.STORAGE -> StorageTab(theme)
                            AppTab.BATTERY -> BatteryTab(theme)
                            AppTab.PERMISSIONS -> PermissionsTab(theme)
                            AppTab.SETTINGS -> SettingsTab(theme, ::onModeChange, ::onFlavorChange)
                        }
                    }
                }
            }
        }

        if (bylineVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF000000))
                    .graphicsLayer(alpha = bylineAlpha)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val logoAppearAlpha by animateFloatAsState(
                        targetValue = if (showByline) 1f else 0f,
                        animationSpec = tween(durationMillis = 350),
                        label = "logoAppear"
                    )

                    Image(
                        painter = painterResource(id = R.drawable.ic_logo_white),
                        contentDescription = null,
                        modifier = Modifier
                            .size(140.dp)
                            .graphicsLayer(alpha = logoAppearAlpha)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 40.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val bylineAppearAlpha by animateFloatAsState(
                        targetValue = if (showByline) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = 350,
                            delayMillis = 150
                        ),
                        label = "bylineAppear"
                    )

                    Text(
                        text = stringResource(R.string.settings_about_author),
                        color = Color(0xFF6A6A6A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.graphicsLayer(alpha = bylineAppearAlpha)
                    )
                }
            }
        }
    }
}