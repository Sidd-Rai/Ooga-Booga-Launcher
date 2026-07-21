package com.siddrai.oogaboogalauncher

import android.content.Context

data class LaunchableApp(val label: String, val packageName: String)

class AppStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var appCache: List<LaunchableApp>? = null

    fun apps(): List<LaunchableApp> {
        appCache?.let { return it }
        val pm = context.packageManager
        return pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != context.packageName }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { LaunchableApp(it.loadLabel(pm).toString(), it.packageName) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
            .also { appCache = it }
    }
    fun invalidateApps() { appCache = null }

    fun hidden() = prefs.getStringSet(HIDDEN, emptySet())?.toSet().orEmpty()
    fun pinned() = prefs.getStringSet(PINNED, emptySet())?.toSet().orEmpty()
    fun distracting() = prefs.getStringSet(DISTRACTING, emptySet())?.toSet().orEmpty()
    fun setHidden(values: Set<String>) = prefs.edit().putStringSet(HIDDEN, values).apply()
    fun setPinned(values: Set<String>) = prefs.edit().putStringSet(PINNED, values).apply()
    fun setDistracting(values: Set<String>) = prefs.edit().putStringSet(DISTRACTING, values).apply()
    fun shutUp() = prefs.getStringSet(SHUT_UP, emptySet())?.toSet().orEmpty()
    fun setShutUp(values: Set<String>) = prefs.edit().putStringSet(SHUT_UP, values).apply()
    fun showClock() = prefs.getBoolean(SHOW_CLOCK, true)
    fun setShowClock(value: Boolean) = prefs.edit().putBoolean(SHOW_CLOCK, value).apply()
    fun widgetDeleteHaptics() = prefs.getBoolean(WIDGET_DELETE_HAPTICS, true)
    fun setWidgetDeleteHaptics(value: Boolean) = prefs.edit().putBoolean(WIDGET_DELETE_HAPTICS, value).apply()
    fun quickLeft(): String? = prefs.getString(QUICK_LEFT, null)
    fun quickRight(): String? = prefs.getString(QUICK_RIGHT, null)
    fun setQuickLeft(value: String?) = prefs.edit().apply { if (value == null) remove(QUICK_LEFT) else putString(QUICK_LEFT, value) }.apply()
    fun setQuickRight(value: String?) = prefs.edit().apply { if (value == null) remove(QUICK_RIGHT) else putString(QUICK_RIGHT, value) }.apply()
    fun widgetIds(): List<Int> = prefs.getString(WIDGETS, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }
    fun widgetIds(screen: String) = widgetIds().filter { prefs.getString("widget_screen_$it", MAIN_SCREEN) == screen }
    fun addWidget(id: Int, screen: String = MAIN_SCREEN) {
        prefs.edit().putString(WIDGETS, (widgetIds() + id).distinct().joinToString(","))
            .putString("widget_screen_$id", screen).apply()
    }
    fun removeWidget(id: Int) = prefs.edit().putString(WIDGETS, (widgetIds() - id).joinToString(","))
        .remove("widget_screen_$id").remove("widget_width_$id").remove("widget_height_$id")
        .remove("widget_offset_x_$id").remove("widget_offset_y_$id")
        .remove("widget_x_$id").remove("widget_y_$id").apply()
    fun widgetHeight(id: Int, fallback: Int) = prefs.getInt("widget_height_$id", fallback)
    fun widgetWidth(id: Int) = prefs.getInt("widget_width_$id", 100)
    fun setWidgetSize(id: Int, width: Int, height: Int) = prefs.edit()
        .putInt("widget_width_$id", width).putInt("widget_height_$id", height).apply()
    fun widgetOffsetX(id: Int) = prefs.getInt("widget_offset_x_$id", 0)
    fun widgetOffsetY(id: Int) = prefs.getInt("widget_offset_y_$id", 0)
    fun setWidgetOffset(id: Int, x: Int, y: Int) = prefs.edit()
        .putInt("widget_offset_x_$id", x).putInt("widget_offset_y_$id", y).apply()
    fun widgetX(id: Int): Int? = if (prefs.contains("widget_x_$id")) prefs.getInt("widget_x_$id", 0) else null
    fun widgetY(id: Int): Int? = if (prefs.contains("widget_y_$id")) prefs.getInt("widget_y_$id", 0) else null
    fun setWidgetPosition(id: Int, x: Int, y: Int) = prefs.edit()
        .putInt("widget_x_$id", x).putInt("widget_y_$id", y).apply()
    fun moveWidget(id: Int, screen: String, direction: Int) {
        val all = widgetIds().toMutableList()
        val onScreen = widgetIds(screen)
        val from = onScreen.indexOf(id)
        val target = (from + direction).coerceIn(0, onScreen.lastIndex)
        if (from < 0 || from == target) return
        val other = onScreen[target]
        val firstIndex = all.indexOf(id); val secondIndex = all.indexOf(other)
        all[firstIndex] = other; all[secondIndex] = id
        prefs.edit().putString(WIDGETS, all.joinToString(",")).apply()
    }
    fun leftScreen() = prefs.getBoolean(LEFT_SCREEN, false)
    fun rightScreen() = prefs.getBoolean(RIGHT_SCREEN, false)
    fun setLeftScreen(value: Boolean) = prefs.edit().putBoolean(LEFT_SCREEN, value).apply()
    fun setRightScreen(value: Boolean) = prefs.edit().putBoolean(RIGHT_SCREEN, value).apply()

    fun beginSession(packageName: String, minutes: Int) {
        val duration = minutes * 60_000L
        prefs.edit()
            .putString(ACTIVE_PACKAGE, packageName)
            .putLong(ACTIVE_UNTIL, System.currentTimeMillis() + duration)
            .putLong(ACTIVE_DURATION, duration)
            .putInt(ACTIVE_EXTENSIONS, 0)
            .remove(ACTIVE_REMAINING)
            .apply()
    }

    fun clearSession() = prefs.edit().remove(ACTIVE_PACKAGE).remove(ACTIVE_UNTIL)
        .remove(ACTIVE_DURATION).remove(ACTIVE_EXTENSIONS).remove(ACTIVE_REMAINING).apply()
    fun activePackage(): String? = prefs.getString(ACTIVE_PACKAGE, null)
    fun activeUntil(): Long = prefs.getLong(ACTIVE_UNTIL, 0L)
    fun hasUsableSession(packageName: String): Boolean {
        if (activePackage() != packageName) return false
        val running = activeUntil() - System.currentTimeMillis()
        val paused = prefs.getLong(ACTIVE_REMAINING, 0L)
        return running > 0L || paused > 0L
    }
    fun pauseSession() {
        val until = activeUntil()
        if (until > 0) prefs.edit().putLong(ACTIVE_REMAINING, (until - System.currentTimeMillis()).coerceAtLeast(0))
            .putLong(ACTIVE_UNTIL, 0L).apply()
    }
    fun resumeSession() {
        if (activeUntil() != 0L) return
        if (prefs.contains(ACTIVE_REMAINING)) {
            val remaining = prefs.getLong(ACTIVE_REMAINING, 0L)
            prefs.edit().putLong(ACTIVE_UNTIL, System.currentTimeMillis() + remaining)
            .remove(ACTIVE_REMAINING).apply()
        }
    }
    fun markScreenLocked() { pauseSession(); prefs.edit().putLong(SCREEN_LOCKED_AT, System.currentTimeMillis()).apply() }
    fun resetSessionAfterLock(timeoutMs: Long) {
        val lockedAt = prefs.getLong(SCREEN_LOCKED_AT, 0L)
        if (lockedAt > 0L && System.currentTimeMillis() - lockedAt >= timeoutMs) clearSession()
        prefs.edit().remove(SCREEN_LOCKED_AT).apply()
    }
    fun extensions() = prefs.getInt(ACTIVE_EXTENSIONS, 0)
    fun extendSession(): Boolean {
        val count = extensions()
        val duration = prefs.getLong(ACTIVE_DURATION, 0L)
        if (count >= 3 || duration <= 0L) return false
        prefs.edit().putInt(ACTIVE_EXTENSIONS, count + 1)
            .putLong(ACTIVE_UNTIL, System.currentTimeMillis() + duration).apply()
        return true
    }

    companion object {
        const val PREFS = "local_settings"
        private const val HIDDEN = "hidden_apps"
        private const val PINNED = "pinned_apps"
        private const val DISTRACTING = "distracting_apps"
        private const val SHUT_UP = "shut_up_apps"
        private const val ACTIVE_PACKAGE = "active_package"
        private const val ACTIVE_UNTIL = "active_until"
        private const val ACTIVE_DURATION = "active_duration"
        private const val ACTIVE_EXTENSIONS = "active_extensions"
        private const val ACTIVE_REMAINING = "active_remaining"
        private const val SCREEN_LOCKED_AT = "screen_locked_at"
        private const val SHOW_CLOCK = "show_clock"
        private const val WIDGET_DELETE_HAPTICS = "widget_delete_haptics"
        private const val QUICK_LEFT = "quick_left"
        private const val QUICK_RIGHT = "quick_right"
        private const val WIDGETS = "widget_ids"
        private const val LEFT_SCREEN = "left_screen"
        private const val RIGHT_SCREEN = "right_screen"
        const val MAIN_SCREEN = "main"
        const val LEFT_WIDGET_SCREEN = "left"
        const val RIGHT_WIDGET_SCREEN = "right"
    }
}
