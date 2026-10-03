package com.andermuchael.launcherandroid.launcher

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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

    LaunchedEffect(Unit) {
        apps = repository.getLaunchableApps()
        favorites = context.getSharedPreferences("launcher", 0)
            .getStringSet("favorites", emptySet())?.toList() ?: emptyList()
    }

    val filteredApps = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    val favoriteApps = favorites.mapNotNull { pkg -> apps.find { it.packageName == pkg } }

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
            .background(Brush.verticalGradient(listOf(Color(0xFF101827), Color(0xFF18283B), Color(0xFF0B111C))))
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount -> if (dragAmount < -24f) drawerOpen = true }
            }
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.10f)) {
                    Text("Launcher", modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(24.dp))
            ClockAndDate()
            Spacer(Modifier.height(26.dp))
            SearchField(value = query, onSearch = { drawerOpen = true })
            Spacer(Modifier.weight(1f))

            if (favoriteApps.isNotEmpty()) {
                Text("Favoritos", modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 12.dp),
                    color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.09f)) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        favoriteApps.take(5).forEach { app -> FavoriteItem(app) { openApp(context, app.packageName) } }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            FilledTonalButton(
                onClick = { drawerOpen = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.14f), contentColor = Color.White)
            ) {
                Icon(Icons.Default.Apps, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Todos os aplicativos", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Deslize para cima para abrir a gaveta", color = Color.White.copy(alpha = 0.48f),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ClockAndDate() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); kotlinx.coroutines.delay(1000) } }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(now)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, color = Color.White, fontSize = 68.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
        Text(date.replaceFirstChar { it.uppercase() }, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SearchField(value: String, onSearch: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onSearch),
        shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.12f)) {
        Row(modifier = Modifier.padding(horizontal = 17.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.width(12.dp))
            Text(if (value.isBlank()) "Buscar aplicativos" else value, color = Color.White.copy(alpha = 0.55f))
        }
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
private fun FavoriteItem(app: AppInfo, onClick: () -> Unit) {
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
