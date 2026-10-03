package com.andermuchael.launcherandroid.data

import android.content.Context
import com.andermuchael.launcherandroid.model.AppInfo

class AppRepository(private val context: Context) {

    fun getLaunchableApps(): List<AppInfo> {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(context.packageName)

        return pm.queryIntentActivities(
            android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            },
            0
        ).map {
            AppInfo(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                icon = it.loadIcon(pm)
            )
        }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
