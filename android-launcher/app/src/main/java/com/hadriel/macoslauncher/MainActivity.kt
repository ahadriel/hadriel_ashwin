package com.hadriel.macoslauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val android.content.Context.launcherStore by preferencesDataStore("launcher_settings")
private val FavoritesKey = stringSetPreferencesKey("dock_favorites")
private val HomePageKey = intPreferencesKey("home_page")

data class LaunchableApp(val packageName: String, val label: String, val icon: Drawable)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            (window.decorView.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        setContent { LauncherApp() }
    }
}

@Composable
private fun LauncherApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps = remember {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(query, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                LaunchableApp(packageName, info.loadLabel(pm).toString(), info.loadIcon(pm))
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }
    val settings by context.launcherStore.data.collectAsStateWithLifecycle(initialValue = androidx.datastore.preferences.core.emptyPreferences())
    val storedFavorites = settings[FavoritesKey] ?: emptySet()
    val storedPage = settings[HomePageKey] ?: 0
    val favorites = remember(apps, storedFavorites) {
        val valid = apps.filter { it.packageName in storedFavorites }
        if (valid.isNotEmpty()) valid.take(6) else apps.take(5)
    }
    var launchpad by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<LaunchableApp?>(null) }
    var clock by remember { mutableStateOf("") }
    var battery by remember { mutableIntStateOf(100) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = SimpleDateFormat("EEE d MMM  •  h:mm", Locale.getDefault()).format(Date())
            val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (level >= 0 && scale > 0) battery = (level * 100 / scale)
            delay(30_000)
        }
    }

    val pageSize = 20
    val pages = max(1, (apps.size + pageSize - 1) / pageSize)
    val pagerState = rememberPagerState(initialPage = storedPage.coerceIn(0, pages - 1), pageCount = { pages })
    LaunchedEffect(pagerState.currentPage) {
        context.launcherStore.edit { it[HomePageKey] = pagerState.currentPage }
    }
    BackHandler(enabled = launchpad || preview != null) {
        if (preview != null) preview = null else launchpad = false
    }

    MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF10172F), Color(0xFF243B68), Color(0xFF11152D))))
        ) {
            Box(Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color(0x6677B8FF), Color.Transparent), radius = 850f)
            ))
            Column(Modifier.fillMaxSize()) {
                MenuBar(clock, battery)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 22.dp),
                        pageSpacing = 24.dp
                    ) { page ->
                        val pageApps = apps.drop(page * pageSize).take(pageSize)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(pageApps, key = { it.packageName }) { app ->
                                AppTile(app, onClick = { launchApp(context, app) }, onLongClick = { preview = app })
                            }
                        }
                    }
                }
                PageDots(pages, pagerState.currentPage)
                Dock(
                    apps = favorites,
                    onOpenLaunchpad = { launchpad = true },
                    onLaunch = { launchApp(context, it) }
                )
                Spacer(Modifier.height(12.dp))
            }

            AnimatedVisibility(visible = launchpad, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().clickable { launchpad = false; search = "" }) {
                    // Blur the home screen behind the launchpad; Compose falls back gracefully on older devices.
                    Box(Modifier.fillMaxSize().blur(18.dp).background(Color(0x66070B16)))
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 42.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(Color(0xCC182238))
                            .clickable { /* Consume taps inside the panel so only outside taps dismiss it. */ }
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(10.dp))
                            OutlinedTextField(
                                value = search, onValueChange = { search = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Search apps", color = Color.LightGray) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF9EC5FF), unfocusedBorderColor = Color(0x668FA8D5)
                                )
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        val filtered = apps.filter { it.label.contains(search, ignoreCase = true) }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(filtered, key = { it.packageName }) { app ->
                                AppTile(app, compact = true, onClick = {
                                    launchApp(context, app); launchpad = false; search = ""
                                }, onLongClick = { preview = app })
                            }
                        }
                    }
                }
            }

            if (preview != null) AppPreview(
                app = preview!!,
                isFavorite = favorites.any { it.packageName == preview!!.packageName },
                onDismiss = { preview = null },
                onOpen = { launchApp(context, preview!!); preview = null },
                onToggleFavorite = {
                    val selected = preview!!
                    val updated = if (favorites.any { it.packageName == selected.packageName }) {
                        favorites.filterNot { it.packageName == selected.packageName }
                    } else (favorites + selected).take(6)
                    scope.launch { context.launcherStore.edit { it[FavoritesKey] = updated.map { item -> item.packageName }.toSet() } }
                    preview = null
                }
            )
        }
    }
}

private fun launchApp(context: android.content.Context, app: LaunchableApp) {
    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent != null) context.startActivity(intent)
}

@Composable
private fun MenuBar(clock: String, battery: Int) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp)).background(Color(0x553D4A64)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("●", color = Color.White, fontSize = 12.sp)
        Spacer(Modifier.width(9.dp))
        Text("Finder", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.width(16.dp))
        Text("File   Edit   View   Go   Window", color = Color(0xFFE1E8F5), fontSize = 11.sp, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text("◖ $battery%   $clock", color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: LaunchableApp,
    compact: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val icon = remember(app.packageName) { app.icon.toBitmap(112, 112).asImageBitmap() }
    Column(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = if (compact) 2.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = icon,
            contentDescription = app.label,
            modifier = Modifier.size(if (compact) 43.dp else 58.dp).clip(RoundedCornerShape(13.dp)),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(6.dp))
        Text(
            app.label, color = Color.White, fontSize = if (compact) 9.sp else 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PageDots(count: Int, selected: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { index ->
            val width by animateFloatAsState(
                targetValue = if (index == selected) 17f else 6f,
                animationSpec = tween(550, easing = FastOutSlowInEasing), label = "page-dot"
            )
            Box(
                Modifier.padding(horizontal = 3.dp).size(width.dp, 6.dp).clip(CircleShape)
                    .background(if (index == selected) Color.White else Color(0x668DA2C5))
            )
        }
    }
}

@Composable
private fun Dock(
    apps: List<LaunchableApp>,
    onOpenLaunchpad: () -> Unit,
    onLaunch: (LaunchableApp) -> Unit
) {
    var dockWidth by remember { mutableFloatStateOf(1f) }
    var activeIndex by remember { mutableIntStateOf(-1) }
    val slotWidth = dockWidth / (apps.size + 2).coerceAtLeast(2)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp).navigationBarsPadding()
            .onGloballyPositioned { dockWidth = it.size.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(apps.size, slotWidth) {
                detectDragGestures(
                    onDragStart = { activeIndex = (it.x / slotWidth).toInt() },
                    onDrag = { change, _ -> activeIndex = (change.position.x / slotWidth).toInt() },
                    onDragEnd = { activeIndex = -1 },
                    onDragCancel = { activeIndex = -1 }
                )
            }
            .clip(RoundedCornerShape(25.dp)).background(Color(0x665F7191))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.forEachIndexed { index, app ->
            val scale by animateFloatAsState(
                targetValue = if (abs(activeIndex - index) == 0) 1.32f else if (abs(activeIndex - index) == 1) 1.12f else 1f,
                animationSpec = tween(180, easing = FastOutSlowInEasing), label = "dock-scale"
            )
            val icon = remember(app.packageName) { app.icon.toBitmap(112, 112).asImageBitmap() }
            Image(
                bitmap = icon, contentDescription = app.label,
                modifier = Modifier.size(42.dp).scale(scale).clip(RoundedCornerShape(11.dp))
                    .clickable { onLaunch(app) },
                contentScale = ContentScale.Fit
            )
        }
        Box(Modifier.width(1.dp).height(32.dp).background(Color(0x99D7E3F5)))
        IconButton(onClick = onOpenLaunchpad) {
            Icon(Icons.Default.Apps, contentDescription = "Launchpad", tint = Color.White, modifier = Modifier.size(31.dp))
        }
    }
}

@Composable
private fun AppPreview(app: LaunchableApp, isFavorite: Boolean, onDismiss: () -> Unit, onOpen: () -> Unit, onToggleFavorite: () -> Unit) {
    val icon = remember(app.packageName) { app.icon.toBitmap(144, 144).asImageBitmap() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0x88070B15)).clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxWidth(0.84f).clip(RoundedCornerShape(26.dp))
                    .background(Color(0xE82B354B)).clickable { /* Keep taps inside the preview from dismissing it. */ }
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    listOf(Color(0xFFFF6259), Color(0xFFFFC04D), Color(0xFF35C96B)).forEach {
                        Box(Modifier.padding(end = 7.dp).size(11.dp).clip(CircleShape).background(it))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("APP PREVIEW", color = Color(0xFFB8C8E2), fontSize = 10.sp, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(22.dp))
                Image(icon, contentDescription = app.label, modifier = Modifier.size(70.dp))
                Spacer(Modifier.height(12.dp))
                Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                Text("Ready to open", color = Color(0xFFB9C6D9), fontSize = 12.sp)
                Spacer(Modifier.height(22.dp))
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Open app") }
                OutlinedButton(onClick = onToggleFavorite, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isFavorite) "Remove from dock" else "Add to dock")
                }
                TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFFD8E4F7)) }
            }
        }
    }
}
