package com.andermuchael.launcherandroid.data

import android.content.Context
import android.content.Intent
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
        cachedApps?.let { return it }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map {
                AppInfo(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
            .also { cachedApps = it }
    }
}
