package com.andermuchael.launcherandroid.data

import android.content.Context
import android.content.Intent
import com.andermuchael.launcherandroid.model.AppInfo

class AppRepository(private val context: Context) {

    fun getLaunchableApps(): List<AppInfo> {
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
    }
}
