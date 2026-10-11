package com.melta.app.core.prefs

/** Pure helpers for choosing how the bar is shown. Unit tested on the JVM. */
object BarModes {

    /**
     * The mode actually used. The status bar overlay needs a permission Android may refuse
     * (it is a "restricted setting" for sideloaded apps), so without it we quietly fall back to
     * the notification panel and the bar always works.
     */
    fun effective(wanted: BarMode, canDrawOverlays: Boolean): BarMode =
        if (wanted == BarMode.OVERLAY && canDrawOverlays) BarMode.OVERLAY else BarMode.NOTIFICATION

    /** "1 h 20 m", "45 m", or null when the time is unknown. */
    fun formatChargeTime(ms: Long): String? {
        if (ms <= 0) return null
        val totalMinutes = ((ms + 59_999) / 60_000).toInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0 -> "$m m"
            m == 0 -> "$h h"
            else -> "$h h $m m"
        }
    }
}
