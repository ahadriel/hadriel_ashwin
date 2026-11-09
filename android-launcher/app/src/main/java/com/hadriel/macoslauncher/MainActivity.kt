package com.hadriel.macoslauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.launcherStore by preferencesDataStore("orbit_launcher_settings")
private val FavoritesKey = stringSetPreferencesKey("dock_favorites")
private val HiddenKey = stringSetPreferencesKey("hidden_apps")
private val HomePageKey = intPreferencesKey("home_page")
private val GridColumnsKey = intPreferencesKey("grid_columns")
private val AccentKey = intPreferencesKey("accent_index")
private val WallpaperKey = intPreferencesKey("wallpaper_index")
private val LabelsKey = booleanPreferencesKey("show_labels")
private val Clock24Key = booleanPreferencesKey("clock_24h")

private val AccentChoices = listOf(
    Color(0xFF83B8FF), Color(0xFF71E0D0), Color(0xFFB8A0FF),
    Color(0xFFFFB58A), Color(0xFFFF8FAE)
)
private val WallpaperChoices = listOf(
    listOf(Color(0xFF080D1C), Color(0xFF142A4D), Color(0xFF10172D)),
    listOf(Color(0xFF090D1B), Color(0xFF26365B), Color(0xFF182044)),
    listOf(Color(0xFF090F18), Color(0xFF16464B), Color(0xFF101D2C)),
    listOf(Color(0xFF110D20), Color(0xFF3B285F), Color(0xFF17152F)),
    listOf(Color(0xFF111018), Color(0xFF4A2C43), Color(0xFF191827))
)

data class LaunchableApp(val packageName: String, val label: String, val icon: Drawable)
private enum class DrawerTab { ALL, PINNED, HIDDEN }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false
        setContent { OrbitLauncher() }
    }
}

@Composable
private fun OrbitLauncher() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences by context.launcherStore.data.collectAsStateWithLifecycle(initialValue = emptyPreferences())

    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isRefreshing = true
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
        isRefreshing = false
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                scope.launch {
                    isRefreshing = true
                    apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
                    isRefreshing = false
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val favoritePackages = preferences[FavoritesKey]
    val hiddenPackages = preferences[HiddenKey] ?: emptySet()
    val favorites = remember(apps, favoritePackages) {
        if (favoritePackages == null) apps.take(5)
        else favoritePackages.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }.take(5)
    }
    val hiddenApps = remember(apps, hiddenPackages) { apps.filter { it.packageName in hiddenPackages } }
    val homeApps = remember(apps, hiddenPackages) { apps.filterNot { it.packageName in hiddenPackages } }
    val columns = (preferences[GridColumnsKey] ?: 4).coerceIn(4, 5)
    val accent = AccentChoices[(preferences[AccentKey] ?: 0).coerceIn(AccentChoices.indices)]
    val wallpaper = WallpaperChoices[(preferences[WallpaperKey] ?: 0).coerceIn(WallpaperChoices.indices)]
    val showLabels = preferences[LabelsKey] ?: true
    val clock24 = preferences[Clock24Key] ?: false

    var drawerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<LaunchableApp?>(null) }
    var drawerTab by remember { mutableStateOf(DrawerTab.ALL) }
    var clock by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var battery by remember { mutableIntStateOf(100) }

    LaunchedEffect(clock24) {
        while (true) {
            val timePattern = if (clock24) "HH:mm" else "h:mm"
            clock = SimpleDateFormat(timePattern, Locale.getDefault()).format(Date())
            date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
            val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (level >= 0 && scale > 0) battery = level * 100 / scale
            delay(15_000)
        }
    }

    val pageSize = columns * 5
    val pageCount = maxOf(1, (homeApps.size + pageSize - 1) / pageSize)
    val pagerState = rememberPagerState(
        initialPage = (preferences[HomePageKey] ?: 0).coerceIn(0, pageCount - 1),
        pageCount = { pageCount }
    )
    LaunchedEffect(pagerState.currentPage) {
        context.launcherStore.edit { it[HomePageKey] = pagerState.currentPage }
    }

    BackHandler(enabled = drawerOpen || settingsOpen || searchOpen || selectedApp != null) {
        when {
            selectedApp != null -> selectedApp = null
            settingsOpen -> settingsOpen = false
            searchOpen -> { searchOpen = false; searchText = "" }
            drawerOpen -> drawerOpen = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = accent,
        secondary = Color(0xFF9CCBFF),
        background = Color(0xFF080D1A),
        surface = Color(0xFF151D30),
        onPrimary = Color(0xFF07101F),
        onSurface = Color(0xFFF2F6FF)
    )) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(wallpaper))) {
            Box(Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(850f, 180f), radius = 1050f
                )
            ))
            Box(Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(Color(0x332B6AA8), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(0f, 1450f), radius = 950f
                )
            ))

            Column(Modifier.fillMaxSize()) {
                TopBar(clock, date, battery, accent, { searchOpen = true }, { settingsOpen = true })

                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("YOUR SPACE", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
                        Text("Good " + greeting() + ",", color = Color(0xFFB6C4DA), fontSize = 13.sp)
                        Text("what's the plan?", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp)
                    }
                    Surface(
                        onClick = { drawerOpen = true },
                        shape = RoundedCornerShape(17.dp),
                        color = Color(0x331A2B48),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x335D83B8))
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = accent, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Explore", color = Color(0xFFE8F0FF), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (homeApps.isEmpty()) {
                        EmptyHome(onExplore = { drawerOpen = true }, loading = isRefreshing)
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 18.dp),
                            pageSpacing = 8.dp,
                            key = { it }
                        ) { page ->
                            val pageApps = homeApps.drop(page * pageSize).take(pageSize)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 3.dp, end = 3.dp, top = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(19.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(pageApps, key = { it.packageName }) { app ->
                                    AppTile(app, accent, showLabels,
                                        onClick = { launchApp(context, app) },
                                        onLongClick = { selectedApp = app }
                                    )
                                }
                            }
                        }
                    }
                }

                PageDots(pageCount, pagerState.currentPage, accent) { page ->
                    scope.launch { pagerState.animateScrollToPage(page) }
                }
                Dock(
                    apps = favorites, accent = accent,
                    onOpenDrawer = { drawerOpen = true },
                    onLaunch = { launchApp(context, it) },
                    onLongPress = { selectedApp = it }
                )
                Spacer(Modifier.height(10.dp))
            }

            if (drawerOpen) {
                AppDrawer(
                    apps = apps, favorites = favorites, hiddenApps = hiddenApps,
                    tab = drawerTab, onTabChange = { drawerTab = it },
                    search = searchText, onSearchChange = { searchText = it },
                    accent = accent, showLabels = showLabels,
                    onDismiss = { drawerOpen = false; searchText = ""; drawerTab = DrawerTab.ALL },
                    onLaunch = { app -> launchApp(context, app); drawerOpen = false; searchText = "" },
                    onLongPress = { selectedApp = it }
                )
            }

            if (searchOpen) {
                SearchOverlay(
                    apps = homeApps, search = searchText, onSearchChange = { searchText = it },
                    accent = accent,
                    onDismiss = { searchOpen = false; searchText = "" },
                    onLaunch = { app -> launchApp(context, app); searchOpen = false; searchText = "" },
                    onLongPress = { selectedApp = it }
                )
            }

            if (settingsOpen) {
                SettingsDialog(
                    accentIndex = preferences[AccentKey] ?: 0,
                    wallpaperIndex = preferences[WallpaperKey] ?: 0,
                    columns = columns, showLabels = showLabels, clock24 = clock24,
                    onAccent = { index -> scope.launch { context.launcherStore.edit { it[AccentKey] = index } } },
                    onWallpaper = { index -> scope.launch { context.launcherStore.edit { it[WallpaperKey] = index } } },
                    onColumns = { value -> scope.launch { context.launcherStore.edit { it[GridColumnsKey] = value; it[HomePageKey] = 0 } } },
                    onLabels = { value -> scope.launch { context.launcherStore.edit { it[LabelsKey] = value } } },
                    onClock24 = { value -> scope.launch { context.launcherStore.edit { it[Clock24Key] = value } } },
                    onRefreshApps = {
                        scope.launch {
                            isRefreshing = true
                            apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
                            isRefreshing = false
                        }
                    },
                    onDismiss = { settingsOpen = false }
                )
            }

            selectedApp?.let { app ->
                AppActionsDialog(
                    app = app,
                    isFavorite = favorites.any { it.packageName == app.packageName },
                    isHidden = app.packageName in hiddenPackages,
                    accent = accent,
                    onDismiss = { selectedApp = null },
                    onOpen = { launchApp(context, app); selectedApp = null },
                    onFavorite = {
                        val next = if (favorites.any { it.packageName == app.packageName }) {
                            favorites.filterNot { it.packageName == app.packageName }
                        } else (favorites + app).take(5)
                        scope.launch { context.launcherStore.edit { it[FavoritesKey] = next.map { item -> item.packageName }.toSet() } }
                        selectedApp = null
                    },
                    onHide = {
                        val next = hiddenPackages.toMutableSet()
                        if (app.packageName in next) next.remove(app.packageName) else next.add(app.packageName)
                        scope.launch { context.launcherStore.edit { it[HiddenKey] = next } }
                        selectedApp = null
                    },
                    onInfo = { openAppInfo(context, app); selectedApp = null },
                    onUninstall = { uninstallApp(context, app); selectedApp = null }
                )
            }
        }
    }
}

private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "morning"
        hour < 17 -> "afternoon"
        else -> "evening"
    }
}

private fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    return pm.queryIntentActivities(query, PackageManager.MATCH_ALL)
        .mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            LaunchableApp(activityInfo.packageName, info.loadLabel(pm).toString(), info.loadIcon(pm))
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

private fun launchApp(context: Context, app: LaunchableApp) {
    runCatching {
        context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let(context::startActivity)
    }
}

private fun openAppInfo(context: Context, app: LaunchableApp) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun uninstallApp(context: Context, app: LaunchableApp) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun TopBar(time: String, date: String, battery: Int, accent: Color, onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(accent, Color(0xFF345A9A)))),
            contentAlignment = Alignment.Center
        ) {
            Text("O", color = Color(0xFF07101E), fontSize = 21.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("ORBIT", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
            Text("PERSONAL SPACE", color = Color(0xFF8294B1), fontSize = 8.sp, letterSpacing = 1.5.sp)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(time, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(date, color = Color(0xFFA9B8CE), fontSize = 9.sp)
        }
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x331C2B45)).padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.BatteryFull, null, tint = if (battery <= 20) Color(0xFFFFB17E) else accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("$battery%", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(5.dp))
        IconButton(onClick = onSearch, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Search, "Search apps", tint = Color(0xFFEAF2FF), modifier = Modifier.size(20.dp)) }
        IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Settings, "Launcher settings", tint = Color(0xFFEAF2FF), modifier = Modifier.size(19.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: LaunchableApp, accent: Color, showLabel: Boolean, compact: Boolean = false,
    onClick: () -> Unit, onLongClick: () -> Unit
) {
    val icon = remember(app.packageName) { app.icon.toBitmap(144, 144).asImageBitmap() }
    Column(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = if (compact) 3.dp else 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(if (compact) 54.dp else 66.dp).clip(RoundedCornerShape(if (compact) 16.dp else 19.dp))
                .background(Color(0x221E3352)),
            contentAlignment = Alignment.Center
        ) {
            Image(icon, app.label, modifier = Modifier.size(if (compact) 43.dp else 55.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
        }
        if (showLabel) {
            Spacer(Modifier.height(7.dp))
            Text(app.label, color = Color(0xFFE9F0FC), fontSize = if (compact) 10.sp else 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PageDots(count: Int, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val width by animateFloatAsState(if (index == selected) 19f else 6f, tween(360, easing = FastOutSlowInEasing), label = "orbit-page-dot")
            Box(Modifier.padding(horizontal = 3.dp).size(width.dp, 6.dp).clip(CircleShape)
                .background(if (index == selected) accent else Color(0x668DA2C5)).clickable { onSelect(index) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(
    apps: List<LaunchableApp>, accent: Color, onOpenDrawer: () -> Unit,
    onLaunch: (LaunchableApp) -> Unit, onLongPress: (LaunchableApp) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp).navigationBarsPadding()
            .clip(RoundedCornerShape(25.dp)).background(Color(0x77202D45))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
    ) {
        apps.take(5).forEach { app ->
            val icon = remember(app.packageName) { app.icon.toBitmap(112, 112).asImageBitmap() }
            Image(icon, app.label, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) }), contentScale = ContentScale.Fit)
        }
        if (apps.isNotEmpty()) Box(Modifier.width(1.dp).height(34.dp).background(Color(0x668CA5C8)))
        Box(
            Modifier.size(43.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.95f), Color(0xFF4B6EA9))))
                .clickable(onClick = onOpenDrawer),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Apps, "Open app library", tint = Color(0xFF07101E), modifier = Modifier.size(25.dp)) }
    }
}

@Composable
private fun EmptyHome(onExplore: () -> Unit, loading: Boolean) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(if (loading) "Preparing your space…" else "Your home is clear", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Open your app library to find something to add to your day.", color = Color(0xFF9DACC4), fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onExplore) { Text("Explore apps") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDrawer(
    apps: List<LaunchableApp>, favorites: List<LaunchableApp>, hiddenApps: List<LaunchableApp>,
    tab: DrawerTab, onTabChange: (DrawerTab) -> Unit, search: String, onSearchChange: (String) -> Unit,
    accent: Color, showLabels: Boolean, onDismiss: () -> Unit,
    onLaunch: (LaunchableApp) -> Unit, onLongPress: (LaunchableApp) -> Unit
) {
    val hiddenNames = remember(hiddenApps) { hiddenApps.map { it.packageName }.toSet() }
    val filtered = when (tab) {
        DrawerTab.ALL -> apps.filterNot { it.packageName in hiddenNames }
        DrawerTab.PINNED -> favorites
        DrawerTab.HIDDEN -> hiddenApps
    }.filter { it.label.contains(search, ignoreCase = true) }
    Box(Modifier.fillMaxSize().background(Color(0xCC050914)).clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 12.dp, start = 14.dp, end = 14.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Color(0xF0141E31)).clickable { }.padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("APP LIBRARY", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Everything in one place", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close app library", tint = Color.White) }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = search, onValueChange = onSearchChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Find an app", color = Color(0xFF8293AF)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = accent) },
                trailingIcon = if (search.isNotEmpty()) ({ IconButton(onClick = { onSearchChange("") }) { Icon(Icons.Default.Close, "Clear search") } }) else null,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = accent, unfocusedBorderColor = Color(0x554F6688), cursorColor = accent)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawerTab.values().forEach { item ->
                    FilterChip(
                        selected = tab == item, onClick = { onTabChange(item) },
                        label = { Text(when (item) { DrawerTab.ALL -> "All apps"; DrawerTab.PINNED -> "Dock"; DrawerTab.HIDDEN -> "Hidden" }) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.22f), selectedLabelColor = Color.White, labelColor = Color(0xFFAFBDD2))
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(if (search.isBlank()) "Nothing here yet" else "No matching apps", color = Color(0xFF9DACC4)) }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4), modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppTile(app, accent, showLabels, compact = true, onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) })
                    }
                }
            }
            Text("Tip: press and hold an app for more actions", color = Color(0xFF7285A2), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun SearchOverlay(
    apps: List<LaunchableApp>, search: String, onSearchChange: (String) -> Unit,
    accent: Color, onDismiss: () -> Unit, onLaunch: (LaunchableApp) -> Unit,
    onLongPress: (LaunchableApp) -> Unit
) {
    val matches = if (search.isBlank()) apps.take(12) else apps.filter { it.label.contains(search, ignoreCase = true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xF20B1222)).statusBarsPadding().padding(horizontal = 20.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("QUICK FIND", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close search", tint = Color.White) }
            }
            OutlinedTextField(
                value = search, onValueChange = onSearchChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search installed apps", color = Color(0xFF8293AF)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = accent) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = accent, unfocusedBorderColor = Color(0x554F6688), cursorColor = accent)
            )
            Spacer(Modifier.height(18.dp))
            Text(if (search.isBlank()) "SUGGESTED" else "${matches.size} RESULTS", color = Color(0xFF8DA0BD), fontSize = 10.sp, letterSpacing = 1.8.sp)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(matches, key = { it.packageName }) { app ->
                    AppTile(app, accent, true, compact = true, onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) })
                }
            }
        }
    }
}

@Composable
private fun AppActionsDialog(
    app: LaunchableApp, isFavorite: Boolean, isHidden: Boolean, accent: Color,
    onDismiss: () -> Unit, onOpen: () -> Unit, onFavorite: () -> Unit,
    onHide: () -> Unit, onInfo: () -> Unit, onUninstall: () -> Unit
) {
    val icon = remember(app.packageName) { app.icon.toBitmap(128, 128).asImageBitmap() }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF18243A),
        titleContentColor = Color.White, textContentColor = Color(0xFFB8C7DC),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(icon, app.label, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(app.label, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(app.packageName, color = Color(0xFF8EA1BD), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                TextButton(onClick = onFavorite, modifier = Modifier.fillMaxWidth()) { Text(if (isFavorite) "Remove from dock" else "Pin to dock", color = accent) }
                TextButton(onClick = onHide, modifier = Modifier.fillMaxWidth()) { Text(if (isHidden) "Show on home" else "Hide from home", color = Color.White) }
                TextButton(onClick = onInfo, modifier = Modifier.fillMaxWidth()) { Text("App info", color = Color.White) }
                TextButton(onClick = onUninstall, modifier = Modifier.fillMaxWidth()) { Text("Uninstall app", color = Color(0xFFFFA6A0)) }
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text("Open", color = accent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFFB8C7DC)) } }
    )
}

@Composable
private fun SettingsDialog(
    accentIndex: Int, wallpaperIndex: Int, columns: Int, showLabels: Boolean, clock24: Boolean,
    onAccent: (Int) -> Unit, onWallpaper: (Int) -> Unit, onColumns: (Int) -> Unit,
    onLabels: (Boolean) -> Unit, onClock24: (Boolean) -> Unit, onRefreshApps: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF151F33),
        title = {
            Column {
                Text("Personalise ORBIT", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Make this space yours", color = Color(0xFF9DADC4), fontSize = 12.sp)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsSection("ACCENT COLOUR") {
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        AccentChoices.forEachIndexed { index, color ->
                            Box(Modifier.size(32.dp).clip(CircleShape).background(color).clickable { onAccent(index) }, contentAlignment = Alignment.Center) {
                                if (index == accentIndex) Box(Modifier.size(11.dp).clip(CircleShape).background(Color(0xFF10182A)))
                            }
                        }
                    }
                }
                SettingsSection("ATMOSPHERE") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WallpaperChoices.forEachIndexed { index, colors ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(if (index == wallpaperIndex) Color(0xFF293A58) else Color(0xFF202C41))
                                    .clickable { onWallpaper(index) }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(52.dp, 28.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(colors)))
                                Spacer(Modifier.width(10.dp))
                                Text(listOf("Midnight blue", "Deep space", "Aurora", "Nebula", "Dusk")[index], color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                if (index == wallpaperIndex) Text("✓", color = AccentChoices[accentIndex], fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                SettingsSection("HOME GRID") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = columns == 4, onClick = { onColumns(4) }, label = { Text("4 columns") })
                        FilterChip(selected = columns == 5, onClick = { onColumns(5) }, label = { Text("5 columns") })
                    }
                }
                SettingsSection("DISPLAY") {
                    SettingSwitch("Show app names", showLabels, onLabels)
                    SettingSwitch("24-hour clock", clock24, onClock24)
                }
                SettingsSection("APP LIBRARY") {
                    TextButton(onClick = onRefreshApps) { Text("Refresh installed apps", color = AccentChoices[accentIndex]) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = AccentChoices[accentIndex]) } }
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, color = Color(0xFF8EA4C5), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        content()
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
