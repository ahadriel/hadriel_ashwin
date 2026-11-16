package com.hadriel.macoslauncher

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val OrbitBg = Color(0xFF080D19)
private val OrbitPanel = Color(0xFF17263D)
private val OrbitAccent = Color(0xFF83C9E8)
private val OrbitText = Color(0xFFF1F5FC)
private val OrbitMuted = Color(0xFF9AAAC2)

private enum class OrbitPage(val label: String) {
    HOME("HOME"), SOUND("SOUND"), CONTROL("CONTROL"), EONIS("EONIS")
}

@Composable
fun OrbitHomeOs() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(OrbitPage.HOME) }
    var clock by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var wledHost by remember {
        mutableStateOf(context.getSharedPreferences("orbit_home_os", Context.MODE_PRIVATE).getString("wled_host", "") ?: "")
    }
    var wledOnline by remember { mutableStateOf<Boolean?>(null) }
    var wledBusy by remember { mutableStateOf(false) }
    var wledMessage by remember { mutableStateOf("Add your WLED device address to connect it.") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            clock = SimpleDateFormat("h:mm", Locale.getDefault()).format(now)
            date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
            delay(30_000)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = OrbitAccent,
        secondary = Color(0xFF9BE4D7),
        background = OrbitBg,
        surface = OrbitPanel,
        onPrimary = Color(0xFF07111D),
        onBackground = OrbitText,
        onSurface = OrbitText
    )) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0xFF080D19), Color(0xFF11213A), Color(0xFF0B1425)))
            )
        ) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("O R B I T", color = OrbitText, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
                        Text("HOME OPERATING ENVIRONMENT", color = OrbitAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(clock, color = OrbitText, fontSize = 31.sp, fontWeight = FontWeight.Light)
                        Text(date, color = OrbitMuted, fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(Color(0xAA182840)).border(1.dp, Color(0x334D789B), RoundedCornerShape(18.dp))
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OrbitPage.values().forEach { item ->
                        val selected = page == item
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .background(if (selected) Color(0xFF29445D) else Color.Transparent)
                                .clickable { page = item }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(item.label, color = if (selected) OrbitAccent else OrbitMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    when (page) {
                        OrbitPage.HOME -> HomeDashboard(
                            wledOnline = wledOnline,
                            onSound = { page = OrbitPage.SOUND },
                            onControl = { page = OrbitPage.CONTROL },
                            onEonis = { page = OrbitPage.EONIS },
                            onSpotify = { launchKnownApp(context, "com.spotify.music", "Spotify") },
                            onPlex = { launchKnownApp(context, "com.plexapp.android", "Plex") }
                        )
                        OrbitPage.SOUND -> SoundPage(
                            onSpotify = { launchKnownApp(context, "com.spotify.music", "Spotify") },
                            onPlex = { launchKnownApp(context, "com.plexapp.android", "Plex") }
                        )
                        OrbitPage.CONTROL -> ControlPage(
                            host = wledHost,
                            onHostChange = { wledHost = it },
                            online = wledOnline == true,
                            busy = wledBusy,
                            message = wledMessage,
                            onSave = {
                                context.getSharedPreferences("orbit_home_os", Context.MODE_PRIVATE)
                                    .edit().putString("wled_host", wledHost.trim()).apply()
                                wledMessage = "WLED address saved on this tablet."
                            },
                            onTest = {
                                wledBusy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { WledClient.setPower(wledHost, null) }
                                    wledOnline = result.first
                                    wledMessage = result.second
                                    wledBusy = false
                                }
                            },
                            onPower = { enabled ->
                                wledBusy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { WledClient.setPower(wledHost, enabled) }
                                    wledOnline = result.first
                                    wledMessage = result.second
                                    wledBusy = false
                                }
                            }
                        )
                        OrbitPage.EONIS -> EonisPage { launchAppByLabel(context, "eonis") }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ORBIT CORE  •  BUILD 001", color = Color(0xFF7186A3), fontSize = 9.sp, letterSpacing = 1.sp)
                    Text("LENOVO TAB M8 FHD", color = Color(0xFF7186A3), fontSize = 9.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun HomeDashboard(
    wledOnline: Boolean?,
    onSound: () -> Unit,
    onControl: () -> Unit,
    onEonis: () -> Unit,
    onSpotify: () -> Unit,
    onPlex: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel {
            Text("YOUR HOME, AT A GLANCE", color = OrbitAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
            Spacer(Modifier.height(7.dp))
            Text("Welcome back.", color = OrbitMuted, fontSize = 14.sp)
            Text("Everything in orbit.", color = OrbitText, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            Text("A calm command centre for the things that make your home yours.", color = OrbitMuted, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModuleCard(Modifier.weight(1f), "SOUND", "Spotify + Plex", { Icon(Icons.Default.MusicNote, null, tint = OrbitAccent) }, onSound)
            ModuleCard(Modifier.weight(1f), "CONTROL", if (wledOnline == true) "WLED connected" else "Smart home", { Icon(Icons.Default.Lightbulb, null, tint = OrbitAccent) }, onControl)
            ModuleCard(Modifier.weight(1f), "EONIS", "Productivity", { Icon(Icons.Default.Apps, null, tint = OrbitAccent) }, onEonis)
        }
        Panel {
            Text("LISTEN NOW", color = OrbitAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Text("Your media", color = OrbitText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(onClick = onSpotify, modifier = Modifier.weight(1f)) { Text("OPEN SPOTIFY") }
                OutlinedButton(onClick = onPlex, modifier = Modifier.weight(1f)) { Text("OPEN PLEX") }
            }
        }
        Panel {
            Text("HOME NETWORK", color = OrbitAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Spacer(Modifier.height(5.dp))
            Text("WLED lighting", color = OrbitText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when (wledOnline) {
                    true -> "Your WLED controller responded."
                    false -> "Not connected yet."
                    null -> "Connection has not been checked."
                },
                color = OrbitMuted, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SoundPage(onSpotify: () -> Unit, onPlex: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeading("ORBIT SOUND", "One place to reach your music and media.")
        ModuleCard(Modifier.fillMaxWidth(), "SPOTIFY", "Open Spotify on this tablet", { Icon(Icons.Default.MusicNote, null, tint = OrbitAccent) }, onSpotify)
        ModuleCard(Modifier.fillMaxWidth(), "PLEX", "Browse your personal media library", { Icon(Icons.Default.OpenInNew, null, tint = OrbitAccent) }, onPlex)
        Panel {
            Text("INTEGRATION STATUS", color = OrbitAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Text("Build 001 opens the installed Spotify and Plex apps. Account linking, playback state and remote queue controls will be added as separate integrations.", color = OrbitMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ControlPage(
    host: String,
    onHostChange: (String) -> Unit,
    online: Boolean,
    busy: Boolean,
    message: String,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onPower: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeading("ORBIT CONTROL", "Direct connections to the devices in your home.")
        Panel {
            Text("WLED LIGHTING", color = OrbitAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Text("LED controller", color = OrbitText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("WLED IP address or host") },
                placeholder = { Text("192.168.1.50") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OrbitText, unfocusedTextColor = OrbitText,
                    focusedBorderColor = OrbitAccent, unfocusedBorderColor = Color(0xFF40536D),
                    focusedLabelColor = OrbitAccent, unfocusedLabelColor = OrbitMuted, cursorColor = OrbitAccent
                )
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text("SAVE ADDRESS") }
                Button(onClick = onTest, enabled = !busy, modifier = Modifier.weight(1f)) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("TEST")
                }
            }
            Text(if (online) "WLED ONLINE" else message, color = if (online) Color(0xFF83D9B8) else OrbitMuted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onPower(true) }, enabled = !busy && host.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("LIGHTS ON")
                }
                OutlinedButton(onClick = { onPower(false) }, enabled = !busy && host.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Text("LIGHTS OFF")
                }
            }
        }
        Panel {
            Text("DIRECT INTEGRATIONS", color = OrbitAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Text("WLED is the first live device adapter. More devices can be added independently without coupling ORBIT Core to one smart-home platform.", color = OrbitMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EonisPage(onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeading("EONIS", "Your time. Your universe.")
        Panel {
            Text("PRODUCTIVITY, IN ORBIT", color = OrbitAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Text("EONIS becomes a first-class destination in your home environment.", color = OrbitText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Text("Build 001 looks for the installed EONIS app and opens it. A secure tasks, calendar and reminders bridge will follow once the EONIS app interface is confirmed.", color = OrbitMuted, fontSize = 12.sp)
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.OpenInNew, null, Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("OPEN EONIS")
            }
        }
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = OrbitAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text(subtitle, color = OrbitText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ModuleCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Panel(modifier.clickable(onClick = onClick)) {
        icon()
        Spacer(Modifier.height(10.dp))
        Text(title, color = OrbitText, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = OrbitMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xDD1A2A42), Color(0xCC121E31))))
            .border(1.dp, Color(0x334F789D), RoundedCornerShape(22.dp)).padding(17.dp),
        content = content,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    )
}

private fun launchKnownApp(context: Context, packageName: String, label: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "$label is not installed on this tablet.", Toast.LENGTH_SHORT).show()
    }
}

private fun launchAppByLabel(context: Context, label: String) {
    val pm = context.packageManager
    val match = pm.getInstalledApplications(0).firstOrNull {
        pm.getApplicationLabel(it).toString().contains(label, ignoreCase = true) &&
            pm.getLaunchIntentForPackage(it.packageName) != null
    }
    if (match != null) {
        pm.getLaunchIntentForPackage(match.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
    } else {
        Toast.makeText(context, "EONIS is not installed or is not available to launch yet.", Toast.LENGTH_LONG).show()
    }
}

private object WledClient {
    fun setPower(hostInput: String, enabled: Boolean?): Pair<Boolean, String> {
        val host = hostInput.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (host.isBlank() || host.contains("/") || host.contains(" ")) {
            return false to "Enter a local WLED host or IP address, without a path."
        }
        return try {
            val connection = (URL("http://$host/json/state").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3500
                readTimeout = 3500
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val payload = if (enabled == null) "{}" else "{\"on\":$enabled}"
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            connection.disconnect()
            if (code in 200..299) {
                if (enabled == null) true to "WLED responded successfully. Connection is ready."
                else true to if (enabled) "WLED lights switched on." else "WLED lights switched off."
            } else {
                false to "WLED returned HTTP $code. Check the address and Wi-Fi connection."
            }
        } catch (_: Exception) {
            false to "Could not reach WLED. Check its IP and make sure the tablet is on the same Wi-Fi."
        }
    }
}
