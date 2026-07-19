package com.nolauncher

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View

data class Palette(val background: Int, val foreground: Int, val muted: Int,
                   val accent: Int, val line: Int, val light: Boolean)

object AppTheme {
    private const val LIGHT = "theme_light"
    private const val BACKGROUND = "theme_background"
    private const val FOREGROUND = "theme_foreground"

    var current = create(false, Color.rgb(10, 11, 11), Color.rgb(236, 231, 218))
        private set

    fun load(context: Context): Palette {
        val prefs = context.getSharedPreferences(AppStore.PREFS, Context.MODE_PRIVATE)
        val light = prefs.getBoolean(LIGHT, false)
        val defaultBackground = if (light) Color.rgb(246, 244, 238) else Color.rgb(10, 11, 11)
        val defaultForeground = if (light) Color.rgb(28, 29, 28) else Color.rgb(236, 231, 218)
        current = create(light, prefs.getInt(BACKGROUND, defaultBackground), prefs.getInt(FOREGROUND, defaultForeground))
        return current
    }

    fun save(context: Context, light: Boolean, background: Int, foreground: Int) {
        context.getSharedPreferences(AppStore.PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(LIGHT, light).putInt(BACKGROUND, background).putInt(FOREGROUND, foreground).apply()
        current = create(light, background, foreground)
        Thread {
            runCatching {
                val wallpaper = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(background) }
                WallpaperManager.getInstance(context.applicationContext)
                    .setBitmap(wallpaper, null, true, WallpaperManager.FLAG_SYSTEM)
                wallpaper.recycle()
            }
        }.start()
    }

    fun prepare(activity: Activity) {
        activity.setTheme(if (load(activity).light) R.style.AppThemeLight else R.style.AppTheme)
    }

    fun apply(activity: Activity) {
        val value = load(activity)
        activity.window.statusBarColor = value.background
        activity.window.navigationBarColor = value.background
        activity.window.decorView.systemUiVisibility = if (value.light) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
    }

    private fun create(light: Boolean, background: Int, foreground: Int): Palette = Palette(
        background, foreground,
        blend(foreground, background, .56f), foreground,
        blend(foreground, background, .16f), light
    )

    private fun blend(front: Int, back: Int, amount: Float) = Color.rgb(
        (Color.red(back) + (Color.red(front) - Color.red(back)) * amount).toInt(),
        (Color.green(back) + (Color.green(front) - Color.green(back)) * amount).toInt(),
        (Color.blue(back) + (Color.blue(front) - Color.blue(back)) * amount).toInt()
    )
}
