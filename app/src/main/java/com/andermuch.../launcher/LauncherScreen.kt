package com.andermuchael.launcherandroid.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Looper
import android.content.Intent
import android.graphics.BitmapFactory
import android.app.WallpaperManager
import android.app.ActivityManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.net.Uri
import android.os.Build
import android.app.role.RoleManager
import android.util.Base64
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.andermuchael.launcherandroid.data.AppRepository
import com.andermuchael.launcherandroid.model.AppInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import coil.Coil

@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    var apps by remember { mutableStateOf(AppRepository.cachedApps()) }
    var query by remember { mutableStateOf("") }
    var drawerOpen by remember { mutableStateOf(false) }
    var favorites by remember {
        mutableStateOf(
            context.getSharedPreferences("launcher", 0)
                .getStringSet("favorites", emptySet())?.toList() ?: emptyList()
        )
    }
    var settingsOpen by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(context.getSharedPreferences("launcher", 0).getBoolean("show_date", true)) }
    val prefs = remember { context.getSharedPreferences("launcher", 0) }
    var compactMode by remember { mutableStateOf(prefs.getBoolean("compact", false)) }
    var iconSize by remember { mutableFloatStateOf(prefs.getFloat("icon_size", 48f)) }
    var clockSize by remember { mutableFloatStateOf(prefs.getFloat("clock_size", 78f)) }
    var wallpaperDim by remember { mutableFloatStateOf(prefs.getFloat("wallpaper_dim", 0.38f)) }
    var memoryMessage by remember { mutableStateOf<String?>(null) }
    var wallpaperUri by remember { mutableStateOf(prefs.getString("wallpaper_uri", null)) }
    var widgetId by remember { mutableStateOf(prefs.getInt("widget_id", AppWidgetManager.INVALID_APPWIDGET_ID)) }
    val appWidgetHost = remember { AppWidgetHost(context, 1024) }
    val widgetPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                widgetId = id
                prefs.edit().putInt("widget_id", id).apply()
            }
        }
    }
    DisposableEffect(appWidgetHost) {
        appWidgetHost.startListening()
        onDispose { appWidgetHost.stopListening() }
    }
    var wallpaperDownloading by remember { mutableStateOf(false) }
    var wallpaperMessage by remember { mutableStateOf<String?>(null) }
    var weatherCity by remember { mutableStateOf(prefs.getString("weather_city", "") ?: "") }
    var weatherUsingLocation by remember { mutableStateOf(prefs.getBoolean("weather_using_location", false)) }
    var weatherExpanded by rememberSaveable { mutableStateOf(false) }
    var folders by remember { mutableStateOf(loadFolders(prefs)) }
    var folderDialog by remember { mutableStateOf(false) }
    var folderOpen by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<LauncherFolder?>(null) }
    var longPressApp by remember { mutableStateOf<AppInfo?>(null) }
    var addToFolderApp by remember { mutableStateOf<AppInfo?>(null) }
    var quickNotes by remember { mutableStateOf(loadQuickNotes(prefs)) }
    var noteDraft by remember { mutableStateOf("") }
    var locationLoading by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            locationLoading = true
            locationMessage = null
            requestWeatherLocation(context) { city, message ->
                locationLoading = false
                if (!city.isNullOrBlank()) {
                    weatherCity = city
                    prefs.edit().putString("weather_city", city).apply()
                }
                locationMessage = message
            }
        } else {
            locationMessage = "Permissão de localização não concedida."
        }
    }

    LaunchedEffect(Unit) {
        if (apps.isEmpty()) {
            apps = withContext(Dispatchers.IO) { repository.getLaunchableApps() }
        }
    }

    val filteredApps = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    val favoriteApps = favorites.mapNotNull { pkg -> apps.find { it.packageName == pkg } }

    val weather by produceState<WeatherData?>(initialValue = cachedWeather(weatherCity), key1 = weatherCity) {
        if (weatherCity.isBlank()) {
            value = null
        } else {
            if (value == null) {
                value = withContext(Dispatchers.IO) { fetchWeather(weatherCity) }
            }
            while (true) {
                delay(60 * 60 * 1000L)
                val refreshed = withContext(Dispatchers.IO) { fetchWeather(weatherCity) }
                if (refreshed != null) value = refreshed
            }
        }
    }

    val wallpaperBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = wallpaperUri) {
        value = withContext(Dispatchers.IO) { wallpaperUri?.let { decodeWallpaper(context, Uri.parse(it)) } }
    }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { wallpaperUri = uri.toString(); prefs.edit().putString("wallpaper_uri", uri.toString()).apply() }
    }

    if (longPressApp != null) {
        AppLongPressMenu(app = longPressApp!!, isFavorite = longPressApp!!.packageName in favorites, onDismiss = { longPressApp = null }, onToggleFavorite = { favorites = toggleFavorite(context, favorites, longPressApp!!.packageName); longPressApp = null }, onAddToFolder = { addToFolderApp = longPressApp; longPressApp = null }, onAppInfo = { openAppInfo(context, longPressApp!!.packageName); longPressApp = null }, onUninstall = { uninstallApp(context, longPressApp!!.packageName); longPressApp = null })
    }
    if (addToFolderApp != null) {
        AddToFolderDialog(app = addToFolderApp!!, folders = folders, onDismiss = { addToFolderApp = null }, onAdd = { folder -> folders = addAppToFolder(prefs, folders, folder, addToFolderApp!!.packageName); addToFolderApp = null }, onNewFolder = { addToFolderApp = null; selectedFolder = null; folderDialog = true })
    }

    if (folderOpen && selectedFolder != null) {
        FolderAppsDialog(
            folder = selectedFolder!!,
            apps = apps,
            onOpenApp = { packageName ->
                folderOpen = false
                openApp(context, packageName)
            },
            onEdit = {
                folderOpen = false
                folderDialog = true
            },
            onDismiss = {
                folderOpen = false
                selectedFolder = null
            }
        )
    }

    if (folderDialog) {
        FolderEditorDialog(apps = apps, initial = selectedFolder, onDismiss = { folderDialog = false; selectedFolder = null }, onSave = { folder -> folders = upsertFolder(prefs, folders, folder); folderDialog = false; selectedFolder = null }, onDelete = { folder -> folders = folders.filterNot { it.id == folder.id }; saveFolders(prefs, folders); folderDialog = false; selectedFolder = null })
    }

    if (settingsOpen) {
        LauncherSettings(
            showDate = showDate,
            compactMode = compactMode,
            weatherCity = weatherCity,
            weatherUsingLocation = weatherUsingLocation,
            widgetId = widgetId,
            iconSize = iconSize,
            clockSize = clockSize,
            wallpaperDim = wallpaperDim,
            memoryMessage = memoryMessage,
            hasWallpaper = wallpaperBitmap != null,
            wallpaperDownloading = wallpaperDownloading,
            wallpaperMessage = wallpaperMessage,
            onPickWallpaper = { wallpaperPicker.launch("image/*") },
            onRemoveWallpaper = { wallpaperUri = null; prefs.edit().remove("wallpaper_uri").apply() },
            onSelectOnlineWallpaper = { option ->
                wallpaperDownloading = true
                wallpaperMessage = null
                downloadAndApplyWallpaper(context, option) { uri, message ->
                    wallpaperDownloading = false
                    if (uri != null) {
                        wallpaperUri = uri.toString()
                        prefs.edit().putString("wallpaper_uri", uri.toString()).apply()
                    }
                    wallpaperMessage = message
                }
            },
            onIconSize = { iconSize = it; prefs.edit().putFloat("icon_size", it).apply() },
            onClockSize = { clockSize = it; prefs.edit().putFloat("clock_size", it).apply() },
            onWallpaperDim = { wallpaperDim = it; prefs.edit().putFloat("wallpaper_dim", it).apply() },
            onOptimizeMemory = { memoryMessage = optimizeLauncherMemory(context) },
            onShowDate = { showDate = it; context.getSharedPreferences("launcher", 0).edit().putBoolean("show_date", it).apply() },
            onCompact = { compactMode = it; context.getSharedPreferences("launcher", 0).edit().putBoolean("compact", it).apply() },
            onWeatherCitySave = { city ->
                weatherCity = city.trim()
                weatherUsingLocation = false
                locationMessage = "Cidade definida manualmente."
                prefs.edit().putString("weather_city", weatherCity).putBoolean("weather_using_location", false).apply()
            },
            locationLoading = locationLoading,
            locationMessage = locationMessage,
            onUseManualWeather = {
                weatherUsingLocation = false
                locationMessage = "Modo manual ativado. Informe uma cidade."
                prefs.edit().putBoolean("weather_using_location", false).apply()
            },
            onUseLocation = {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fine || coarse) {
                    locationLoading = true
                    locationMessage = null
                    requestWeatherLocation(context) { city, message ->
                        locationLoading = false
                        if (!city.isNullOrBlank()) {
                            weatherCity = city
                            weatherUsingLocation = true
                            prefs.edit().putString("weather_city", city).putBoolean("weather_using_location", true).apply()
                        }
                        locationMessage = message
                    }
                } else {
                    locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            },
            onAddWidget = {
                val id = appWidgetHost.allocateAppWidgetId()
                val intent = android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                }
                widgetPicker.launch(intent)
            },
            onRemoveWidget = {
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) appWidgetHost.deleteAppWidgetId(widgetId)
                widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                prefs.edit().remove("widget_id").apply()
            },
            onSetDefaultLauncher = { requestDefaultLauncher(context) },
            onClose = { settingsOpen = false }
        )
        return
    }

    if (drawerOpen) {
        AppDrawer(
            apps = filteredApps,
            query = query,
            onQueryChange = { query = it },
            favorites = favorites.toSet(),
            onToggleFavorite = { app -> favorites = toggleFavorite(context, favorites, app.packageName) },
            onOpen = { app -> openApp(context, app.packageName) },
            onLongPress = { app -> longPressApp = app },
            onClose = { drawerOpen = false; query = "" }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(launcherBackground())
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -24f) drawerOpen = true
                }
            }
    ) {
        val currentWallpaper = wallpaperBitmap
        if (currentWallpaper != null) {
            Image(bitmap = currentWallpaper, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = wallpaperDim)))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 8.dp)
        ) {
            Spacer(Modifier.height(30.dp))
            ClockAndDate(showDate = showDate, clockSize = clockSize, onAlarm = { openAlarm(context) })
            if (weatherCity.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                WeatherCard(
                    weather = weather,
                    configuredCity = weatherCity,
                    expanded = weatherExpanded,
                    onToggle = { weatherExpanded = !weatherExpanded }
                )
            }
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                AndroidView(
                    factory = {
                        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)
                            ?: throw IllegalStateException("Widget não encontrado")
                        appWidgetHost.createView(context, widgetId, info)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 220.dp).clip(RoundedCornerShape(18.dp))
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(if (compactMode) 12.dp else 18.dp))
            SearchField(value = query, onQueryChange = { query = it; drawerOpen = true })

            Spacer(Modifier.height(10.dp))
            QuickNotesWidget(
                notes = quickNotes,
                draft = noteDraft,
                onDraftChange = { noteDraft = it },
                onAdd = {
                    val text = noteDraft.trim()
                    if (text.isNotEmpty()) {
                        quickNotes = (quickNotes + text).takeLast(5)
                        saveQuickNotes(prefs, quickNotes)
                        noteDraft = ""
                    }
                },
                onDelete = { note ->
                    quickNotes = quickNotes.filterNot { it == note }
                    saveQuickNotes(prefs, quickNotes)
                }
            )

            if (folders.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Pastas", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    folders.take(3).forEach { folder ->
                        Surface(
                            modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.07f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(start = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).fillMaxHeight().clickable {
                                        selectedFolder = folder
                                        folderOpen = true
                                    },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(folder.name, color = Color.White, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                                        Text("${folder.packages.size}", color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        selectedFolder = folder
                                        folderDialog = true
                                    },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Editar pasta",
                                        tint = Color.White.copy(alpha = 0.55f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (folders.size < 3) {
                        Surface(
                            modifier = Modifier.weight(1f).height(54.dp).clickable { folderDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.05f)
                        ) {
                            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = "Nova pasta", tint = Color.White.copy(alpha = 0.62f), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Nova pasta", color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(if (compactMode) 12.dp else 18.dp))
            Text("Favoritos", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(favoriteApps.take(12), key = { it.packageName }) { app ->
                    MinimalFavoriteItem(
                        app = app,
                        iconSize = iconSize,
                        onClick = { openApp(context, app.packageName) },
                        onLongPress = { longPressApp = app }
                    )
                }
                item {
                    MinimalAddFavoriteItem { drawerOpen = true }
                }
            }

            Spacer(Modifier.weight(1f))

            TextButton(
                onClick = { settingsOpen = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.62f))
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("Configurar")
            }

            Spacer(Modifier.height(4.dp))

            FilledTonalButton(
                onClick = { drawerOpen = true },
                modifier = Modifier.align(Alignment.CenterHorizontally).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.09f),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Todos os aplicativos")
            }

            Spacer(Modifier.height(8.dp))
            Text("Deslize para cima", modifier = Modifier.align(Alignment.CenterHorizontally), color = Color.White.copy(alpha = 0.35f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinimalFavoriteItem(
    app: AppInfo,
    iconSize: Float,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            app.label,
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
                fontSize = 17.sp
            ),
            maxLines = 1
        )
    }
}
@Composable
private fun MinimalAddFavoriteItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Add, contentDescription = "Adicionar favorito", tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text("Adicionar favorito", color = Color.White.copy(alpha = 0.35f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ClockAndDate(showDate: Boolean, clockSize: Float, onAlarm: () -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            kotlinx.coroutines.delay(1000)
        }
    }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(now)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onAlarm)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                time,
                color = Color.White,
                fontSize = 60.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-2).sp,
                fontFamily = FontFamily.Serif
            )
            if (showDate) {
                Text(
                    date.replaceFirstChar { it.uppercase() },
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Icon(
            Icons.Default.AccessAlarm,
            contentDescription = "Alarme",
            tint = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.padding(start = 4.dp, top = 6.dp).size(22.dp)
        )
    }
}

@Composable
private fun WeatherCard(
    weather: WeatherData?,
    configuredCity: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.075f)
    ) {
        if (weather == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.65f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Carregando clima de $configuredCity…",
                    color = Color.White.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        } else if (!expanded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(weatherEmoji(weather.code), fontSize = 19.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        weather.city,
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                    Text(
                        weatherDescription(weather.code),
                        color = Color.White.copy(alpha = 0.48f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(
                    weather.temperature.roundToInt().toString() + "°",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Light
                )
            }
        } else {
            Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(weatherEmoji(weather.code), fontSize = 24.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(weather.city, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Text(weatherDescription(weather.code), color = Color.White.copy(alpha = 0.94f), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(weather.temperature.roundToInt().toString() + "°", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Light)
                }
                Spacer(Modifier.height(6.dp))
                Text("Próximas horas", color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(3.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp), contentPadding = PaddingValues(end = 4.dp)) {
                    items(weather.hours.take(10)) { hour ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val hourLabel = hour.time.substringAfter("T").take(2) + "h"
                            Text(hourLabel, color = Color.White.copy(alpha = 0.58f), style = MaterialTheme.typography.labelSmall)
                            Text(weatherEmoji(hour.code), fontSize = 15.sp)
                            Text(hour.temperature.roundToInt().toString() + "°", color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.labelSmall)
                            if (hour.rainProbability > 0) {
                                Text(hour.rainProbability.toString() + "%", color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickNotesWidget(
    notes: List<String>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = Color.White.copy(alpha = 0.075f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(7.dp))
                Text("Notas rápidas", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text("máx. 5", color = Color.White.copy(alpha = 0.35f), style = MaterialTheme.typography.labelSmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp),
                    decorationBox = { inner ->
                        if (draft.isBlank()) {
                            Text("Digite uma anotação rápida…", color = Color.White.copy(alpha = 0.38f), style = MaterialTheme.typography.bodySmall)
                        }
                        inner()
                    }
                )
                TextButton(onClick = onAdd, enabled = draft.isNotBlank(), contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)) {
                    Text("Adicionar", fontSize = 12.sp)
                }
            }
            notes.takeLast(2).forEach { note ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("•", color = Color.White.copy(alpha = 0.42f), modifier = Modifier.padding(end = 6.dp))
                    Text(note, color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onDelete(note) }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Excluir anotação", tint = Color.White.copy(alpha = 0.32f), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onQueryChange: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(17.dp),
        color = Color.White.copy(alpha = 0.10f)
    ) {
        Row(modifier = Modifier.padding(horizontal = 17.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.width(12.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onQueryChange,
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 16.sp),
                decorationBox = { inner ->
                    if (value.isBlank()) Text("Buscar aplicativos", color = Color.White.copy(alpha = 0.55f))
                    inner()
                }
            )
        }
    }
}

@Composable
private fun LauncherSettings(
    showDate: Boolean,
    compactMode: Boolean,
    weatherCity: String,
    weatherUsingLocation: Boolean,
    widgetId: Int,
    iconSize: Float,
    clockSize: Float,
    wallpaperDim: Float,
    memoryMessage: String?,
    hasWallpaper: Boolean,
    wallpaperDownloading: Boolean,
    wallpaperMessage: String?,
    onPickWallpaper: () -> Unit,
    onSelectOnlineWallpaper: (WallpaperOption) -> Unit,
    onRemoveWallpaper: () -> Unit,
    onIconSize: (Float) -> Unit,
    onClockSize: (Float) -> Unit,
    onWallpaperDim: (Float) -> Unit,
    onOptimizeMemory: () -> Unit,
    onShowDate: (Boolean) -> Unit,
    onCompact: (Boolean) -> Unit,
    onWeatherCitySave: (String) -> Unit,
    locationLoading: Boolean,
    locationMessage: String?,
    onUseManualWeather: () -> Unit,
    onUseLocation: () -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D1420))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Voltar", tint = Color.White)
            }
            Text("Configurações do Launcher", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(26.dp))
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState())
        ) {
        Text("Tela inicial", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.08f)) {
            Column {
                SettingSwitch("Mostrar data", "Exibir a data abaixo do relógio", showDate, onShowDate)
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                SettingSwitch("Modo compacto", "Reduzir os espaços da tela inicial", compactMode, onCompact)
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.08f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Previsão do tempo", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Use a localização do celular ou informe uma cidade manualmente. A localização só é usada para descobrir a cidade do clima.", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onUseLocation,
                    enabled = !locationLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (locationLoading) CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                    else Text(if (weatherUsingLocation) "✓ Usando localização atual" else "Usar localização atual")
                }
                if (weatherUsingLocation) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onUseManualWeather) { Text("Usar uma cidade manualmente") }
                }
                if (!locationMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(locationMessage, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                var cityInput by remember(weatherCity) { mutableStateOf(weatherCity) }
                OutlinedTextField(
                    value = cityInput,
                    onValueChange = { cityInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !weatherUsingLocation,
                    singleLine = true,
                    label = { Text("Cidade") },
                    placeholder = { Text("Ex.: São Paulo") },
                    trailingIcon = { TextButton(onClick = { onWeatherCitySave(cityInput) }, enabled = cityInput.isNotBlank()) { Text("Salvar") } }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Personalização", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.08f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Papel de parede", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(if (hasWallpaper) "Imagem personalizada ativa" else "Use uma imagem do seu celular", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPickWallpaper, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("Escolher imagem") }
                    if (hasWallpaper) OutlinedButton(onClick = onRemoveWallpaper, shape = RoundedCornerShape(14.dp)) { Text("Remover") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.08f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Papéis de parede", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Escolha uma imagem em alta resolução. Toque para aplicar.", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(wallpaperOptions) { option ->
                        Surface(
                            modifier = Modifier.width(210.dp).height(132.dp).clickable(enabled = !wallpaperDownloading) { onSelectOnlineWallpaper(option) },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.06f)
                        ) {
                            Box {
                                AsyncImage(model = option.url, contentDescription = option.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)))
                                Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                                    Text(option.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("Toque para aplicar", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                if (wallpaperDownloading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                wallpaperMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.07f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tamanho dos elementos", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Ajuste o tamanho sem aplicar temas.", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Slider(value = iconSize, onValueChange = onIconSize, valueRange = 36f..52f, steps = 7)
                Spacer(Modifier.height(10.dp))
                Text("Tamanho do relógio", color = Color.White, fontWeight = FontWeight.Medium)
                Text(clockSize.roundToInt().toString() + " dp", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                Slider(value = clockSize, onValueChange = onClockSize, valueRange = 64f..96f, steps = 7)
                Spacer(Modifier.height(6.dp))
                Text("Escurecer papel de parede", color = Color.White, fontWeight = FontWeight.Medium)
                Text((wallpaperDim * 100).roundToInt().toString() + "%", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                Slider(value = wallpaperDim, onValueChange = onWallpaperDim, valueRange = 0.10f..0.60f, steps = 9)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Widgets", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.08f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Widgets sobre a tela inicial", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Adicione um widget do Android diretamente à tela inicial.", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAddWidget, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Adicionar widget") }
                    if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) OutlinedButton(onClick = onRemoveWidget, shape = RoundedCornerShape(14.dp)) { Text("Remover") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Desempenho", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.08f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Otimizar memória", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Libera o cache visual do Launcher e solicita a coleta de memória do próprio aplicativo. O Android continua gerenciando a RAM dos outros aplicativos.", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOptimizeMemory, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Otimizar memória") }
                memoryMessage?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Sistema", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onSetDefaultLauncher,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) { Text("Launcher padrão do Android") }
        Spacer(Modifier.height(8.dp))
        Text("Escolha o Launcherandroid como aplicativo de tela inicial padrão nas configurações do Android.", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(28.dp))
        }
    }
}

private fun decodeWallpaper(context: Context, uri: Uri): ImageBitmap? {
    return runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val metrics = context.resources.displayMetrics
        val targetW = metrics.widthPixels * 2
        val targetH = metrics.heightPixels * 2
        var sample = 1
        while (bounds.outWidth / sample > targetW * 1.5 || bounds.outHeight / sample > targetH * 1.5) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
    }.getOrNull()
}

private fun optimizeLauncherMemory(context: Context): String {
    return runCatching {
        Coil.imageLoader(context).memoryCache?.clear()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        Runtime.getRuntime().gc()
        val after = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        "Memória do Launcher otimizada. RAM disponível: " + (after.availMem / (1024 * 1024)) + " MB."
    }.getOrElse { "Não foi possível otimizar a memória agora." }
}

private fun launcherBackground(): Brush =
    Brush.verticalGradient(listOf(Color(0xFF080B10), Color(0xFF10151D), Color(0xFF06080C)))

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AppDrawer(
    apps: List<AppInfo>, query: String, onQueryChange: (String) -> Unit, favorites: Set<String>,
    onToggleFavorite: (AppInfo) -> Unit, onOpen: (AppInfo) -> Unit, onLongPress: (AppInfo) -> Unit, onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    val organizedApps = remember(apps) {
        apps.sortedBy { it.label.lowercase(Locale("pt", "BR")) }
    }

    val grouped = remember(organizedApps) {
        organizedApps.groupBy {
            it.label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "#"
        }.toSortedMap(compareBy { if (it == "#") "{" else it })
    }

    val rows = remember(grouped) {
        buildList {
            grouped.forEach { (letter, letterApps) ->
                add(DrawerRow.Header(letter))
                letterApps.forEach { app -> add(DrawerRow.App(app)) }
            }
        }
    }

    val letterPositions = remember(rows) {
        rows.mapIndexedNotNull { index, row ->
            if (row is DrawerRow.Header) row.letter to index else null
        }.toMap()
    }

    val alphabet = remember {
        ('A'..'Z').map { it.toString() } + "#"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1420))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Aplicativos",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${organizedApps.size} aplicativos",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.09f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.68f))
                Spacer(Modifier.width(10.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 16.sp),
                    decorationBox = { inner ->
                        if (query.isBlank()) {
                            Text("Buscar aplicativos", color = Color.White.copy(alpha = 0.42f))
                        }
                        inner()
                    }
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpar", tint = Color.White.copy(alpha = 0.65f))
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxSize()) {
            if (organizedApps.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nenhum aplicativo encontrado",
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        rows,
                        key = { row ->
                            when (row) {
                                is DrawerRow.Header -> "header_${row.letter}"
                                is DrawerRow.App -> row.app.packageName
                            }
                        }
                    ) { row ->
                        when (row) {
                            is DrawerRow.Header -> {
                                Text(
                                    row.letter,
                                    color = Color.White.copy(alpha = 0.42f),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 4.dp)
                                )
                            }
                            is DrawerRow.App -> {
                                NiagaraAppRow(
                                    app = row.app,
                                    isFavorite = row.app.packageName in favorites,
                                    onClick = { onOpen(row.app) },
                                    onLongPress = { onLongPress(row.app) }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(5.dp))

                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .pointerInput(letterPositions, rows) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                fun jump(positionY: Float) {
                                    val y = positionY.coerceIn(0f, size.height.toFloat())
                                    val fraction = if (size.height > 0) y / size.height else 0f
                                    val letterIndex = (fraction * alphabet.size).toInt().coerceIn(0, alphabet.lastIndex)
                                    val letter = alphabet[letterIndex]
                                    val target = letterPositions[letter]
                                        ?: letterPositions.entries.minByOrNull {
                                            kotlin.math.abs(it.key.first().code - letter.first().code)
                                        }?.value
                                    if (target != null) scope.launch { listState.scrollToItem(target) }
                                }
                                jump(down.position.y)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    jump(change.position.y)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        alphabet.forEach { letter ->
                            Text(
                                letter,
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface DrawerRow {
    data class Header(val letter: String) : DrawerRow
    data class App(val app: AppInfo) : DrawerRow
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NiagaraAppRow(app: AppInfo, isFavorite: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onClick, onLongClick = onLongPress).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(app.label, color = Color.White.copy(alpha = 0.90f), style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, fontSize = 16.sp), maxLines = 1, modifier = Modifier.weight(1f))
        if (isFavorite) Text("•", color = Color.White.copy(alpha = 0.35f), fontSize = 18.sp)
    }
}
@Composable
private fun QuickAppItem(app: AppInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(58.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val iconBitmap = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() }
        Image(
            bitmap = iconBitmap,
            contentDescription = app.label,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
        )
        Text(
            app.label,
            color = Color.White.copy(alpha = 0.82f),
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun RowScope.FavoriteDockItem(app: AppInfo, iconSize: Float, onClick: () -> Unit) {
    val iconBitmap = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() }
    Column(
        modifier = Modifier.weight(1f).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = iconBitmap,
            contentDescription = app.label,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
        )
        Text(
            app.label,
            color = Color.White.copy(alpha = 0.86f),
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun RowScope.AddFavoriteItem(onClick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.08f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("+", color = Color.White.copy(alpha = 0.75f), fontSize = 26.sp, fontWeight = FontWeight.Light)
            }
        }
        Text(
            "Adicionar",
            color = Color.White.copy(alpha = 0.58f),
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun AppItem(app: AppInfo, isFavorite: Boolean, onClick: () -> Unit, onToggleFavorite: () -> Unit, onLongPress: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppIcon(app, onClick, onLongPress)
        Text(
            app.label,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 2,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun RowScope.FavoriteItem(app: AppInfo, onClick: () -> Unit) {
    val iconBitmap = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() }
    Column(modifier = Modifier.weight(1f).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(17.dp), color = Color.White.copy(alpha = 0.10f)) {
            Image(bitmap = iconBitmap, contentDescription = app.label, modifier = Modifier.padding(7.dp).size(48.dp))
        }
        Text(app.label, color = Color.White.copy(alpha = 0.82f), maxLines = 1, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIcon(app: AppInfo, onClick: () -> Unit, onLongPress: () -> Unit) {
    val iconBitmap = remember(app.packageName) { app.icon.toBitmap(112, 112).asImageBitmap() }
    Image(bitmap = iconBitmap, contentDescription = app.label,
        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).combinedClickable(onClick = onClick, onLongClick = onLongPress))
}

data class LauncherFolder(val id: String, val name: String, val packages: List<String>)

private fun appCategory(app: AppInfo): String {
    val s = (app.label + " " + app.packageName).lowercase(Locale("pt", "BR"))
    return when {
        listOf("whatsapp", "telegram", "mensag", "sms", "mail", "gmail", "email").any { s.contains(it) } -> "Comunicação"
        listOf("nubank", "banco", "bradesco", "inter", "finance", "pix", "carteira", "mercado pago", "pag").any { s.contains(it) } -> "Financeiro"
        listOf("facebook", "instagram", "tiktok", "twitter", "x.com", "social").any { s.contains(it) } -> "Social"
        listOf("netflix", "youtube", "spotify", "music", "jogo", "game", "bowling").any { s.contains(it) } -> "Entretenimento"
        listOf("trello", "drive", "docs", "office", "work", "calendar", "agenda", "canva").any { s.contains(it) } -> "Trabalho"
        listOf("config", "calcul", "arquivo", "file", "cleaner", "chip", "alexa", "ferrament").any { s.contains(it) } -> "Ferramentas"
        else -> "Outros"
    }
}

private fun loadFolders(prefs: android.content.SharedPreferences): List<LauncherFolder> {
    return prefs.getStringSet("folders", emptySet()).orEmpty().mapNotNull { raw ->
        val p = raw.split("|", limit = 3)
        if (p.size == 3) runCatching { LauncherFolder(p[0], String(Base64.decode(p[1], Base64.NO_WRAP), Charsets.UTF_8), p[2].split(",").filter { it.isNotBlank() }) }.getOrNull() else null
    }
}

private fun saveFolders(prefs: android.content.SharedPreferences, folders: List<LauncherFolder>) {
    prefs.edit().putStringSet("folders", folders.map { "${it.id}|${Base64.encodeToString(it.name.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}|${it.packages.joinToString(",")}" }.toSet()).apply()
}

private fun upsertFolder(prefs: android.content.SharedPreferences, folders: List<LauncherFolder>, folder: LauncherFolder): List<LauncherFolder> {
    val next = (folders.filterNot { it.id == folder.id } + folder).take(8)
    saveFolders(prefs, next)
    return next
}

@Composable
private fun FolderAppsDialog(
    folder: LauncherFolder,
    apps: List<AppInfo>,
    onOpenApp: (String) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val folderApps = folder.packages.mapNotNull { pkg ->
        apps.find { it.packageName == pkg }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(folder.name, modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.EditNote, contentDescription = "Editar pasta")
                }
            }
        },
        text = {
            if (folderApps.isEmpty()) {
                Text("Esta pasta está vazia.")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(folderApps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenApp(app.packageName) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = app.icon.toBitmap(80, 80).asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                app.label,
                                maxLines = 2,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

@Composable
private fun FolderEditorDialog(
    apps: List<AppInfo>, initial: LauncherFolder?, onDismiss: () -> Unit, onSave: (LauncherFolder) -> Unit, onDelete: (LauncherFolder) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    val selected = remember { mutableStateListOf<String>().apply { addAll(initial?.packages ?: emptyList()) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nova pasta" else "Editar pasta") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome da pasta") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("Escolha os aplicativos", style = MaterialTheme.typography.labelLarge)
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(apps.sortedBy { it.label.lowercase() }, key = { it.packageName }) { app ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { if (selected.contains(app.packageName)) selected.remove(app.packageName) else selected.add(app.packageName) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected.contains(app.packageName), onCheckedChange = { if (it) selected.add(app.packageName) else selected.remove(app.packageName) })
                            Text(app.label, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) { val id = initial?.id ?: System.currentTimeMillis().toString(); onSave(LauncherFolder(id, name.trim(), selected.toList())) } }) { Text("Salvar") } },
        dismissButton = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (initial != null) TextButton(onClick = { onDelete(initial) }) { Text("Excluir") }; TextButton(onClick = onDismiss) { Text("Cancelar") } } }
    )
}
@Composable
private fun AppLongPressMenu(app: AppInfo, isFavorite: Boolean, onDismiss: () -> Unit, onToggleFavorite: () -> Unit, onAddToFolder: () -> Unit, onAppInfo: () -> Unit, onUninstall: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(bitmap = app.icon.toBitmap(80, 80).asImageBitmap(), contentDescription = null, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(12.dp))
                Text(app.label, maxLines = 2)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onToggleFavorite, modifier = Modifier.fillMaxWidth()) { Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = null); Spacer(Modifier.width(12.dp)); Text(if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos", modifier = Modifier.weight(1f), textAlign = TextAlign.Start) }
                TextButton(onClick = onAddToFolder, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Folder, contentDescription = null); Spacer(Modifier.width(12.dp)); Text("Adicionar a uma pasta", modifier = Modifier.weight(1f), textAlign = TextAlign.Start) }
                TextButton(onClick = onAppInfo, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Settings, contentDescription = null); Spacer(Modifier.width(12.dp)); Text("Informações do app", modifier = Modifier.weight(1f), textAlign = TextAlign.Start) }
                TextButton(onClick = onUninstall, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Close, contentDescription = null); Spacer(Modifier.width(12.dp)); Text("Desinstalar", modifier = Modifier.weight(1f), textAlign = TextAlign.Start) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun AddToFolderDialog(app: AppInfo, folders: List<LauncherFolder>, onDismiss: () -> Unit, onAdd: (LauncherFolder) -> Unit, onNewFolder: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar a uma pasta") },
        text = {
            if (folders.isEmpty()) Text("Você ainda não criou nenhuma pasta.")
            else LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(folders, key = { it.id }) { folder ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onAdd(folder) }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFFC857))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f).padding(vertical = 9.dp)) { Text(folder.name, fontWeight = FontWeight.Medium); Text("${folder.packages.size} aplicativos", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onNewFolder) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Nova pasta") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun addAppToFolder(prefs: android.content.SharedPreferences, folders: List<LauncherFolder>, folder: LauncherFolder, packageName: String): List<LauncherFolder> {
    if (packageName in folder.packages) return folders
    return upsertFolder(prefs, folders, folder.copy(packages = (folder.packages + packageName).distinct()))
}

private fun openAppInfo(context: Context, packageName: String) {
    context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") })
}

private fun uninstallApp(context: Context, packageName: String) {
    context.startActivity(Intent(Intent.ACTION_DELETE).apply { data = Uri.parse("package:$packageName") })
}
private fun downloadAndApplyWallpaper(context: Context, option: WallpaperOption, onFinished: (Uri?, String?) -> Unit) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        try {
            val connection = (URL(option.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 20000
                requestMethod = "GET"
                connect()
            }
            if (connection.responseCode !in 200..299) throw IllegalStateException()
            val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
            val file = File(dir, option.name.replace("[^A-Za-z0-9]".toRegex(), "_") + ".jpg")
            connection.inputStream.use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
            connection.disconnect()
            WallpaperManager.getInstance(context).setStream(file.inputStream())
            withContext(Dispatchers.Main) { onFinished(Uri.fromFile(file), option.name + " aplicado.") }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) { onFinished(null, "Não foi possível aplicar o papel de parede agora.") }
        }
    }
}

private fun loadQuickNotes(prefs: android.content.SharedPreferences): List<String> {
    return prefs.getStringSet("quick_notes", emptySet()).orEmpty().toList().takeLast(5)
}

private fun saveQuickNotes(prefs: android.content.SharedPreferences, notes: List<String>) {
    prefs.edit().putStringSet("quick_notes", notes.takeLast(5).toSet()).apply()
}

private fun requestWeatherLocation(context: Context, onFinished: (String?, String?) -> Unit) {
    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) {
        onFinished(null, "Permita a localização para usar o clima automático.")
        return
    }

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = when {
        hasFine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    if (provider == null) {
        onFinished(null, "Ative a localização do celular para continuar.")
        return
    }

    fun resolve(latitude: Double, longitude: Double) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val city = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var result: String? = null
                    val geocoder = Geocoder(context, Locale("pt", "BR"))
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        result = addresses.firstOrNull()?.locality
                            ?: addresses.firstOrNull()?.subAdminArea
                    }
                    var waited = 0
                    while (result == null && waited < 30) {
                        kotlinx.coroutines.delay(100)
                        waited++
                    }
                    result
                } else {
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale("pt", "BR")).getFromLocation(latitude, longitude, 1)
                        ?.firstOrNull()?.let { it.locality ?: it.subAdminArea }
                }
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (!city.isNullOrBlank()) onFinished(city, "Localização atual usada no clima.")
                else onFinished(null, "Não foi possível identificar a cidade pela localização.")
            }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val last = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        if (last != null) {
            resolve(last.latitude, last.longitude)
            return
        }
    }

    val listener = object : android.location.LocationListener {
        override fun onLocationChanged(location: android.location.Location) {
            runCatching { locationManager.removeUpdates(this) }
            resolve(location.latitude, location.longitude)
        }
        override fun onProviderEnabled(providerName: String) {}
        override fun onProviderDisabled(providerName: String) {}
    }
    runCatching {
        locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
    }.onFailure {
        onFinished(null, "Não foi possível obter a localização agora.")
    }
}

private fun requestDefaultLauncher(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            context.startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }
}
private fun openAlarm(context: Context) {
    val setAlarm = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
    }
    val showAlarms = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
    runCatching {
        context.startActivity(setAlarm)
    }.recoverCatching {
        context.startActivity(showAlarms)
    }
}

private fun openApp(context: Context, packageName: String) {
    context.packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
        context.startActivity(intent)
    }
}

private fun toggleFavorite(context: Context, current: List<String>, packageName: String): List<String> {
    val next = if (packageName in current) current.filterNot { it == packageName } else (current + packageName).take(12)
    context.getSharedPreferences("launcher", 0).edit().putStringSet("favorites", next.toSet()).apply()
    return next
}