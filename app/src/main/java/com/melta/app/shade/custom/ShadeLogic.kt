package com.melta.app.shade.custom

/** Pure helpers for Melta Shade, unit tested on the JVM. */
object ShadeLogic {

    /**
     * Should a drag on the top edge open the shade? A clear pull of 24dp, or a quick flick
     * of at least 8dp, both downwards.
     */
    fun shouldOpen(dyPx: Float, velocityYPxPerSec: Float, density: Float): Boolean {
        val dy = dyPx / density
        val v = velocityYPxPerSec / density
        return dy >= 24f || (dy >= 8f && v >= 800f)
    }

    /**
     * Groups notifications by app, newest app first, newest notification first inside each app.
     */
    fun <T> groupByApp(items: List<T>, app: (T) -> String, time: (T) -> Long): List<Pair<String, List<T>>> =
        items.groupBy(app)
            .map { (pkg, list) -> pkg to list.sortedByDescending(time) }
            .sortedByDescending { (_, list) -> time(list.first()) }

    /** "now", "5m", "3h", "2d" */
    fun ago(nowMs: Long, thenMs: Long): String {
        val minutes = ((nowMs - thenMs).coerceAtLeast(0) / 60_000)
        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m"
            minutes < 60 * 24 -> "${minutes / 60}h"
            else -> "${minutes / (60 * 24)}d"
        }
    }
}
