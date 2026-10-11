package com.melta.app.shade.custom

import android.app.ActivityOptions
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One notification as Melta Shade shows it. */
data class ShadeNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val clearable: Boolean,
    val icon: Drawable?,
)

/**
 * Feeds Melta Shade with the notifications on the phone. Only active while the user has given
 * Melta notification access. Nothing is stored or sent anywhere.
 */
class MeltaNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        _notifications.value = emptyList()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private val labels = HashMap<String, String>()
    private val icons = HashMap<String, Drawable?>()

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        _notifications.value = active
            .filter { it.packageName != packageName && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .mapNotNull { it.toShade() }
    }

    private fun StatusBarNotification.toShade(): ShadeNotification? {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return ShadeNotification(
            key = key,
            packageName = packageName,
            appName = labelFor(packageName),
            title = title,
            text = text,
            postTime = postTime,
            clearable = isClearable,
            icon = iconFor(packageName),
        )
    }

    private fun labelFor(pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    private fun iconFor(pkg: String): Drawable? = icons.getOrPut(pkg) {
        try {
            packageManager.getApplicationIcon(pkg)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** Opens the notification's target the same way the system shade would. */
    fun open(key: String): Boolean {
        val sbn = runCatching { activeNotifications }.getOrNull()?.firstOrNull { it.key == key } ?: return false
        val intent = sbn.notification.contentIntent ?: return false
        val sent = runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
                intent.send(this, 0, null, null, null, null, options)
            } else {
                intent.send()
            }
        }.isSuccess
        if (sent && sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) cancelNotification(key)
        return sent
    }

    fun dismiss(key: String) = runCatching { cancelNotification(key) }

    fun clearAll() = runCatching { cancelAllNotifications() }

    companion object {
        @Volatile
        var instance: MeltaNotificationListener? = null
            private set

        private val _notifications = MutableStateFlow<List<ShadeNotification>>(emptyList())
        val notifications: StateFlow<List<ShadeNotification>> = _notifications.asStateFlow()

        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}
