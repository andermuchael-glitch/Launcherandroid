package com.andermuchael.launcherandroid.launcher

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
        val prefs = context.getSharedPreferences("launcher", 0)
        favorites = prefs.getStringSet("favorites", emptySet())?.toList() ?: emptyList()
    }

    val filteredApps = apps.filter {
        query.isBlank() || it.label.contains(query, ignoreCase = true)
    }
    val favoriteApps = favorites.mapNotNull { pkg -> apps.find { it.packageName == pkg } }

    if (drawerOpen) {
        AppDrawer(
            apps = filteredApps,
            query = query,
            onQueryChange = { query = it },
            favorites = favorites.toSet(),
            onToggleFavorite = { app ->
                favorites = toggleFavorite(context, favorites, app.packageName)
            },
            onOpen = { app -> openApp(context, app.packageName) },
            onClose = {
                drawerOpen = false
                query = ""
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ClockAndDate()

            Spacer(Modifier.height(28.dp))

            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = { drawerOpen = true }
            )

            Spacer(Modifier.weight(1f))

            if (favoriteApps.isNotEmpty()) {
                Text(
                    "Favoritos",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    favoriteApps.take(5).forEach { app ->
                        FavoriteItem(app) { openApp(context, app.packageName) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            FilledTonalButton(
                onClick = { drawerOpen = true },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Apps, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Todos os aplicativos")
            }
        }
    }
}

@Composable
private fun ClockAndDate() {
    var now by remember { mutableStateOf(Date()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            kotlinx.coroutines.delay(1000)
        }
    }

    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(now)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, fontSize = 64.sp, style = MaterialTheme.typography.displayMedium)
        Text(
            date.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
        placeholder = { Text("Buscar aplicativos") },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Limpar")
                }
            }
        },
        shape = RoundedCornerShape(18.dp)
    )
    if (value.isNotBlank()) {
        LaunchedEffect(Unit) { onSearch() }
    }
}

@Composable
private fun AppDrawer(
    apps: List<AppInfo>,
    query: String,
    onQueryChange: (String) -> Unit,
    favorites: Set<String>,
    onToggleFavorite: (AppInfo) -> Unit,
    onOpen: (AppInfo) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Fechar")
            }
            Text("Aplicativos", style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Buscar aplicativo") },
            shape = RoundedCornerShape(18.dp)
        )

        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 82.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppItem(
                    app = app,
                    isFavorite = app.packageName in favorites,
                    onClick = { onOpen(app) },
                    onToggleFavorite = { onToggleFavorite(app) }
                )
            }
        }
    }
}

@Composable
private fun AppItem(
    app: AppInfo,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            AppIcon(app, onClick)
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(30.dp).align(Alignment.TopEnd)
            ) {
                Icon(
                    if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorito",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            app.label,
            maxLines = 2,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun FavoriteItem(app: AppInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
        )
        Text(
            app.label,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun AppIcon(app: AppInfo, onClick: () -> Unit) {
    Image(
        bitmap = app.icon.toBitmap(112, 112).asImageBitmap(),
        contentDescription = app.label,
        modifier = Modifier
            .size(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    )
}

private fun openApp(context: android.content.Context, packageName: String) {
    context.packageManager.getLaunchIntentForPackage(packageName)?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(it)
    }
}

private fun toggleFavorite(
    context: android.content.Context,
    current: List<String>,
    packageName: String
): List<String> {
    val next = if (packageName in current) {
        current.filterNot { it == packageName }
    } else {
        (current + packageName).take(5)
    }
    context.getSharedPreferences("launcher", 0)
        .edit()
        .putStringSet("favorites", next.toSet())
        .apply()
    return next
}
