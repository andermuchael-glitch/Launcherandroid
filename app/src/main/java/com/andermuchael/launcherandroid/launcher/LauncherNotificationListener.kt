package com.andermuchael.launcherandroid.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

data class LauncherNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long
)

class LauncherNotificationListener : NotificationListenerService() {
    companion object {
        const val ACTION_NOTIFICATIONS_UPDATED =
            "com.andermuchael.launcherandroid.NOTIFICATIONS_UPDATED"
        private const val PREFS = "launcher_notifications"
        private const val ITEMS = "items"

        fun load(context: android.content.Context): List<LauncherNotification> {
            val raw = context.getSharedPreferences(PREFS, 0).getString(ITEMS, "[]") ?: "[]"
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    LauncherNotification(
                        key = item.optString("key"),
                        packageName = item.optString("packageName"),
                        appName = item.optString("appName"),
                        title = item.optString("title"),
                        text = item.optString("text"),
                        postedAt = item.optLong("postedAt")
                    )
                }
            }.getOrDefault(emptyList())
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshFromActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshFromActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshFromActiveNotifications()
    }

    private fun refreshFromActiveNotifications() {
        val current = runCatching { activeNotifications?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .asSequence()
            .filter { it.packageName != packageName }
            .filterNot { it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 }
            .sortedByDescending { it.postTime }
            .take(8)
            .mapNotNull { sbn ->
                runCatching {
                    val extras = sbn.notification.extras
                    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                    val text = (
                        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                            ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
                        )?.toString().orEmpty()
                    val label = runCatching {
                        packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(sbn.packageName, 0)
                        ).toString()
                    }.getOrDefault(sbn.packageName.substringAfterLast('.'))
                    LauncherNotification(
                        key = sbn.key,
                        packageName = sbn.packageName,
                        appName = label,
                        title = title.ifBlank { label },
                        text = text,
                        postedAt = sbn.postTime
                    )
                }.getOrNull()
            }.toList()

        val array = JSONArray()
        current.forEach { item ->
            array.put(JSONObject().apply {
                put("key", item.key)
                put("packageName", item.packageName)
                put("appName", item.appName)
                put("title", item.title)
                put("text", item.text)
                put("postedAt", item.postedAt)
            })
        }
        getSharedPreferences(PREFS, 0).edit().putString(ITEMS, array.toString()).apply()
        sendBroadcast(Intent(ACTION_NOTIFICATIONS_UPDATED).setPackage(packageName))
    }
}
