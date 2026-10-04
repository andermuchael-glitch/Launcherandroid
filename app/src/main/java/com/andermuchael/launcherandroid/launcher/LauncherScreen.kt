package com.andermuchael.launcherandroid.launcher

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.andermuchael.launcherandroid.data.AppRepository
import com.andermuchael.launcherandroid.model.AppInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    var apps by remember { mutableStateOf(emptyList<AppInfo>()) }
    var query by remember { mutableStateOf("") }
    var drawerOpen by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(listOf<String>()) }
    var settingsOpen by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(context.getSharedPreferences("launcher", 0).getBoolean("show_date", true)) }
    val prefs = remember { context.getSharedPreferences("launcher", 0) }
    var compactMode by remember { mutableStateOf(prefs.getBoolean("compact", false)) }
    var theme by remember { mutableStateOf(prefs.getString("theme", "azul") ?: "azul") }
    var iconSize by remember { mutableFloatStateOf(prefs.getFloat("icon_size", 48f)) }
    var wallpaperUri by remember { mutableStateOf(prefs.getString("wallpaper_uri", null)) }

    LaunchedEffect(Unit) {
        apps = repository.getLaunchableApps()
        favorites = context.getSharedPreferences("launcher", 0)
            .getStringSet("favorites", emptySet())?.toList() ?: emptyList()
    }

    val filteredApps = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    val favoriteApps = favorites.mapNotNull { pkg -> apps.find { it.packageName == pkg } }

    val wallpaperBitmap = remember(wallpaperUri) { wallpaperUri?.let { runCatching { context.contentResolver.openInputStream(Uri.parse(it))?.use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull() } }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { wallpaperUri = uri.toString(); prefs.edit().putString("wallpaper_uri", uri.toString()).apply() }
    }

    if (settingsOpen) {
        LauncherSettings(
            showDate = showDate,
            compactMode = compactMode,
            theme = theme,
            iconSize = iconSize,
            hasWallpaper = wallpaperBitmap != null,
            onPickWallpaper = { wallpaperPicker.launch("image/*") },
            onRemoveWallpaper = { wallpaperUri = null; prefs.edit().remove("wallpaper_uri").apply() },
            onTheme = { theme = it; prefs.edit().putString("theme", it).apply() },
            onIconSize = { iconSize = it; prefs.edit().putFloat("icon_size", it).apply() },
            onShowDate = { showDate = it; context.getSharedPreferences("launcher", 0).edit().putBoolean("show_date", it).apply() },
            onCompact = { compactMode = it; context.getSharedPreferences("launcher", 0).edit().putBoolean("compact", it).apply() },
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
            onClose = { drawerOpen = false; query = "" }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(launcherBackground(theme))
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount -> if (dragAmount < -24f) drawerOpen = true }
            }
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        if (wallpaperBitmap != null) {
            Image(bitmap = wallpaperBitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
        }
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Launcher", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { settingsOpen = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Configurações do Launcher", tint = Color.White.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.height(24.dp))
            ClockAndDate(showDate = showDate)
            Spacer(Modifier.height(if (compactMode) 16.dp else 26.dp))
            SearchField(value = query, onQueryChange = { query = it; drawerOpen = true })
            Spacer(Modifier.height(if (compactMode) 20.dp else 30.dp))

            Text(
                "Favoritos",
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = Color.White.copy(alpha = 0.085f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(5) { index ->
                        val app = favoriteApps.getOrNull(index)
                        if (app != null) {
                            FavoriteDockItem(app, iconSize) { openApp(context, app.packageName) }
                        } else {
                            AddFavoriteItem { drawerOpen = true }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { drawerOpen = true },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Apps, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Aplicativos", fontWeight = FontWeight.SemiBold)
                }
                FilledTonalButton(
                    onClick = { settingsOpen = true },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Configurações", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Deslize para cima para abrir a gaveta", color = Color.White.copy(alpha = 0.48f),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ClockAndDate(showDate: Boolean) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); kotlinx.coroutines.delay(1000) } }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(now)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, color = Color.White, fontSize = 68.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
        if (showDate) Text(date.replaceFirstChar { it.uppercase() }, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SearchField(value: String, onQueryChange: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.12f)
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
    theme: String,
    iconSize: Float,
    hasWallpaper: Boolean,
    onPickWallpaper: () -> Unit,
    onRemoveWallpaper: () -> Unit,
    onTheme: (String) -> Unit,
    onIconSize: (Float) -> Unit,
    onShowDate: (Boolean) -> Unit,
    onCompact: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D1420)).padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Voltar", tint = Color.White)
            }
            Text("Configurações do Launcher", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(26.dp))
        Text("Tela inicial", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.08f)) {
            Column {
                SettingSwitch("Mostrar data", "Exibir a data abaixo do relógio", showDate, onShowDate)
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                SettingSwitch("Modo compacto", "Reduzir os espaços da tela inicial", compactMode, onCompact)
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
                Text("Tema", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("azul" to "Azul", "preto" to "Preto", "claro" to "Claro").forEach { (key, label) ->
                        FilterChip(selected = theme == key, onClick = { onTheme(key) }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Tamanho dos ícones: " + iconSize.toInt() + " dp", color = Color.White.copy(alpha = 0.8f))
                Slider(value = iconSize, onValueChange = onIconSize, valueRange = 40f..64f, steps = 5)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Sistema", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) { Text("Launcher padrão do Android") }
        Spacer(Modifier.height(8.dp))
        Text("Escolha o Launcherandroid como aplicativo de tela inicial padrão nas configurações do Android.", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
    }
}

private fun launcherBackground(theme: String): Brush {
    return when (theme) {
        "preto" -> Brush.verticalGradient(listOf(Color(0xFF050505), Color(0xFF111111), Color(0xFF000000)))
        "claro" -> Brush.verticalGradient(listOf(Color(0xFFE8EEF5), Color(0xFFD3DDE8), Color(0xFFBFCBDA)))
        else -> Brush.verticalGradient(listOf(Color(0xFF101827), Color(0xFF18283B), Color(0xFF0B111C)))
    }
}

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
    onToggleFavorite: (AppInfo) -> Unit, onOpen: (AppInfo) -> Unit, onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1420)).padding(horizontal = 16.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White) }
            Text("Aplicativos", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Surface(modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.10f)) {
            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.width(10.dp))
                androidx.compose.foundation.text.BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true,
                    modifier = Modifier.weight(1f), textStyle = LocalTextStyle.current.copy(color = Color.White))
                if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Limpar", tint = Color.White)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("${apps.size} aplicativos", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 82.dp), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppItem(app, app.packageName in favorites, { onOpen(app) }, { onToggleFavorite(app) })
            }
        }
    }
}

@Composable
private fun QuickAppItem(app: AppInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(58.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
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
    Column(
        modifier = Modifier.weight(1f).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
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
private fun AppItem(app: AppInfo, isFavorite: Boolean, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            AppIcon(app, onClick)
            Surface(modifier = Modifier.size(26.dp).align(Alignment.TopEnd), shape = CircleShape, color = Color(0xFF0D1420).copy(alpha = 0.92f)) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = "Favorito",
                        tint = if (isFavorite) Color(0xFFFFC857) else Color.White.copy(alpha = 0.65f), modifier = Modifier.size(15.dp))
                }
            }
        }
        Text(app.label, color = Color.White.copy(alpha = 0.9f), maxLines = 2, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun RowScope.FavoriteItem(app: AppInfo, onClick: () -> Unit) {
    Column(modifier = Modifier.weight(1f).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(17.dp), color = Color.White.copy(alpha = 0.10f)) {
            Image(bitmap = app.icon.toBitmap(96, 96).asImageBitmap(), contentDescription = app.label, modifier = Modifier.padding(7.dp).size(48.dp))
        }
        Text(app.label, color = Color.White.copy(alpha = 0.82f), maxLines = 1, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun AppIcon(app: AppInfo, onClick: () -> Unit) {
    Image(bitmap = app.icon.toBitmap(112, 112).asImageBitmap(), contentDescription = app.label,
        modifier = Modifier.size(62.dp).clip(RoundedCornerShape(17.dp)).clickable(onClick = onClick))
}

private fun openApp(context: Context, packageName: String) {
    context.packageManager.getLaunchIntentForPackage(packageName)?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(it) }
}

private fun toggleFavorite(context: Context, current: List<String>, packageName: String): List<String> {
    val next = if (packageName in current) current.filterNot { it == packageName } else (current + packageName).take(5)
    context.getSharedPreferences("launcher", 0).edit().putStringSet("favorites", next.toSet()).apply()
    return next
}
