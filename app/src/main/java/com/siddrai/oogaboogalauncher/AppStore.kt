package com.siddrai.oogaboogalauncher

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.media.AudioAttributes

data class LaunchableApp(val label: String, val packageName: String)
enum class HapticKind { WIDGET_HOVER, WIDGET_DELETE, WIDGET_RESIZE, NAVIGATION, ACTION, CHECKBOX }

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
    fun hapticsEnabled() = prefs.getBoolean(HAPTICS_ENABLED, true)
    fun setHapticsEnabled(value: Boolean) = prefs.edit().putBoolean(HAPTICS_ENABLED, value).apply()
    fun widgetHoverHaptics() = prefs.getBoolean(WIDGET_HOVER_HAPTICS, true)
    fun setWidgetHoverHaptics(value: Boolean) = prefs.edit().putBoolean(WIDGET_HOVER_HAPTICS, value).apply()
    fun widgetDeleteHaptics() = prefs.getBoolean(WIDGET_DELETE_HAPTICS, true)
    fun setWidgetDeleteHaptics(value: Boolean) = prefs.edit().putBoolean(WIDGET_DELETE_HAPTICS, value).apply()
    fun widgetResizeHaptics() = prefs.getBoolean(WIDGET_RESIZE_HAPTICS, true)
    fun setWidgetResizeHaptics(value: Boolean) = prefs.edit().putBoolean(WIDGET_RESIZE_HAPTICS, value).apply()
    fun navigationHaptics() = prefs.getBoolean(NAVIGATION_HAPTICS, true)
    fun setNavigationHaptics(value: Boolean) = prefs.edit().putBoolean(NAVIGATION_HAPTICS, value).apply()
    fun actionHaptics() = prefs.getBoolean(ACTION_HAPTICS, true)
    fun setActionHaptics(value: Boolean) = prefs.edit().putBoolean(ACTION_HAPTICS, value).apply()
    fun checkboxHaptics() = prefs.getBoolean(CHECKBOX_HAPTICS, true)
    fun setCheckboxHaptics(value: Boolean) = prefs.edit().putBoolean(CHECKBOX_HAPTICS, value).apply()

    fun performHaptic(kind: HapticKind) {
        if (!hapticsEnabled()) return
        when (kind) {
            HapticKind.WIDGET_HOVER -> if (!widgetHoverHaptics()) return
            HapticKind.WIDGET_DELETE -> if (!widgetDeleteHaptics()) return
            HapticKind.WIDGET_RESIZE -> if (!widgetResizeHaptics()) return
            HapticKind.NAVIGATION -> if (!navigationHaptics()) return
            HapticKind.ACTION -> if (!actionHaptics()) return
            HapticKind.CHECKBOX -> if (!checkboxHaptics()) return
        }
        val vibrator = if (Build.VERSION.SDK_INT >= 31)
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        else context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (!vibrator.hasVibrator()) return
        val effect = when (kind) {
            HapticKind.WIDGET_HOVER -> if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK) else VibrationEffect.createOneShot(12L, 80)
            HapticKind.WIDGET_DELETE -> if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK) else VibrationEffect.createWaveform(longArrayOf(0, 30, 25, 55), intArrayOf(0, 180, 0, 255), -1)
            HapticKind.WIDGET_RESIZE -> VibrationEffect.createOneShot(16L, 110)
            HapticKind.NAVIGATION -> VibrationEffect.createOneShot(10L, 60)
            HapticKind.ACTION -> VibrationEffect.createOneShot(18L, 110)
            HapticKind.CHECKBOX -> VibrationEffect.createOneShot(8L, 70)
        }
        vibrator.vibrate(effect, AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
    }

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
        prefs.edit().putString(ACTIVE_PACKAGE, packageName)
            .putLong(ACTIVE_UNTIL, System.currentTimeMillis() + duration)
            .putLong(ACTIVE_DURATION, duration).putLong(ACTIVE_REMAINING, duration)
            .putInt(ACTIVE_EXTENSIONS, 0).remove(SCREEN_LOCKED_AT).apply()
    }
    fun clearSession() = prefs.edit().remove(ACTIVE_PACKAGE).remove(ACTIVE_UNTIL)
        .remove(ACTIVE_DURATION).remove(ACTIVE_EXTENSIONS).remove(ACTIVE_REMAINING).remove(SCREEN_LOCKED_AT).apply()
    fun activePackage(): String? = prefs.getString(ACTIVE_PACKAGE, null)
    fun lastForegroundPackage(): String? = prefs.getString(LAST_FOREGROUND_PACKAGE, null)
    fun setLastForegroundPackage(value: String) = prefs.edit().putString(LAST_FOREGROUND_PACKAGE, value).apply()
    fun activeUntil(): Long = prefs.getLong(ACTIVE_UNTIL, 0L)
    fun remainingSessionMs(): Long {
        val until = activeUntil()
        return if (until > 0L) (until - System.currentTimeMillis()).coerceAtLeast(0L)
        else prefs.getLong(ACTIVE_REMAINING, 0L).coerceAtLeast(0L)
    }
    fun hasUsableSession(packageName: String) = activePackage() == packageName && remainingSessionMs() > 0L
    fun pauseSession() {
        if (activePackage() == null) return
        prefs.edit().putLong(ACTIVE_REMAINING, remainingSessionMs()).putLong(ACTIVE_UNTIL, 0L).apply()
    }
    fun resumeSession(): Boolean {
        if (activePackage() == null) return false
        val remaining = remainingSessionMs()
        if (remaining <= 0L) return false
        prefs.edit().putLong(ACTIVE_UNTIL, System.currentTimeMillis() + remaining).apply()
        return true
    }
    fun markScreenLocked() {
        pauseSession()
        if (!prefs.contains(SCREEN_LOCKED_AT)) {
            prefs.edit().putLong(SCREEN_LOCKED_AT, System.currentTimeMillis()).apply()
        }
    }
    fun wasScreenLocked() = prefs.contains(SCREEN_LOCKED_AT)
    fun clearScreenLockMarker() = prefs.edit().remove(SCREEN_LOCKED_AT).apply()
    fun unlockInvalidated(maxGraceMs: Long = 2 * 60_000L): Boolean {
        val lockedAt = prefs.getLong(SCREEN_LOCKED_AT, 0L)
        if (lockedAt <= 0L) return false
        val remainingAtLock = prefs.getLong(ACTIVE_REMAINING, maxGraceMs).coerceAtLeast(1L)
        val allowedLockTime = minOf(maxGraceMs, remainingAtLock)
        val lockedDuration = System.currentTimeMillis() - lockedAt
        val invalid = lockedDuration >= allowedLockTime
        prefs.edit().remove(SCREEN_LOCKED_AT).apply()
        if (invalid) clearSession()
        return invalid
    }
    fun extensions() = prefs.getInt(ACTIVE_EXTENSIONS, 0)
    fun extendSession(): Boolean {
        val count = extensions()
        val duration = prefs.getLong(ACTIVE_DURATION, 0L)
        if (count >= 3 || duration <= 0L) return false
        prefs.edit().putInt(ACTIVE_EXTENSIONS, count + 1)
            .putLong(ACTIVE_UNTIL, System.currentTimeMillis() + duration).putLong(ACTIVE_REMAINING, duration).apply()
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
        private const val LAST_FOREGROUND_PACKAGE = "last_foreground_package"
        private const val SHOW_CLOCK = "show_clock"
        private const val HAPTICS_ENABLED = "haptics_enabled"
        private const val WIDGET_HOVER_HAPTICS = "widget_hover_haptics"
        private const val WIDGET_DELETE_HAPTICS = "widget_delete_haptics"
        private const val WIDGET_RESIZE_HAPTICS = "widget_resize_haptics"
        private const val NAVIGATION_HAPTICS = "navigation_haptics"
        private const val ACTION_HAPTICS = "action_haptics"
        private const val CHECKBOX_HAPTICS = "checkbox_haptics"
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

