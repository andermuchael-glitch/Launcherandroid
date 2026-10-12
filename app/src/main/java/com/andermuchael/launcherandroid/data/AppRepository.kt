package com.andermuchael.launcherandroid.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.andermuchael.launcherandroid.model.AppInfo

class AppRepository(private val context: Context) {

    companion object {
        @Volatile private var cachedApps: List<AppInfo>? = null

        fun cachedApps(): List<AppInfo> = cachedApps.orEmpty()

        fun invalidateCache() {
            cachedApps = null
        }
    }

    fun getLaunchableApps(): List<AppInfo> {
        cachedApps?.takeIf { it.isNotEmpty() }?.let { return it }
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // Primeiro caminho: consulta direta das atividades lançáveis.
        val directResults = runCatching {
            pm.queryIntentActivities(launcherIntent, 0)
        }.getOrDefault(emptyList())

        val directApps = directResults.asSequence()
            .mapNotNull { resolveInfo ->
                runCatching {
                    AppInfo(
                        label = resolveInfo.loadLabel(pm).toString(),
                        packageName = resolveInfo.activityInfo.packageName,
                        icon = resolveInfo.loadIcon(pm)
                    )
                }.getOrNull()
            }
            .distinctBy { it.packageName }
            .toList()

        // Fallback para dispositivos que retornem uma consulta vazia por política
        // de visibilidade de pacotes ou por um estado transitório do PackageManager.
        val foundApps = if (directApps.isNotEmpty()) {
            directApps
        } else {
            runCatching {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .asSequence()
                    .mapNotNull { appInfo ->
                        val launchIntent = runCatching {
                            pm.getLaunchIntentForPackage(appInfo.packageName)
                        }.getOrNull()
                        if (launchIntent == null) null else runCatching {
                            AppInfo(
                                label = pm.getApplicationLabel(appInfo).toString(),
                                packageName = appInfo.packageName,
                                icon = pm.getApplicationIcon(appInfo)
                            )
                        }.getOrNull()
                    }
                    .distinctBy { it.packageName }
                    .toList()
            }.getOrDefault(emptyList())
        }

        val sorted = foundApps.sortedBy { it.label.lowercase() }
        // Nunca guardar uma resposta vazia: permite tentar novamente na próxima abertura.
        if (sorted.isNotEmpty()) cachedApps = sorted
        return sorted
    }
}
