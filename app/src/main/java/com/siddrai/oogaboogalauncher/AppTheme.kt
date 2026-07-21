package com.siddrai.oogaboogalauncher

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.appwidget.AppWidgetHostView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import java.util.WeakHashMap

data class Palette(val background: Int, val foreground: Int, val muted: Int,
                   val accent: Int, val line: Int, val light: Boolean)

object AppTheme {
    private const val LIGHT = "theme_light"
    private const val BACKGROUND = "theme_background"
    private const val FOREGROUND = "theme_foreground"
    private const val FONT = "font_family"
    private const val FONT_SCALE = "font_scale"
    private val originalTextSizes = WeakHashMap<TextView, Float>()
    private val typographyListeners = WeakHashMap<Activity, Boolean>()
    private val previewText = WeakHashMap<TextView, Boolean>()

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

    fun font(context: Context) = context.getSharedPreferences(AppStore.PREFS, Context.MODE_PRIVATE)
        .getString(FONT, "monospace") ?: "monospace"

    fun fontScale(context: Context) = context.getSharedPreferences(AppStore.PREFS, Context.MODE_PRIVATE)
        .getFloat(FONT_SCALE, 1f).coerceIn(.8f, 1.3f)

    fun saveTypography(context: Context, font: String, scale: Float) {
        context.getSharedPreferences(AppStore.PREFS, Context.MODE_PRIVATE).edit()
            .putString(FONT, font).putFloat(FONT_SCALE, scale.coerceIn(.8f, 1.3f)).apply()
    }

    fun prepare(activity: Activity) {
        activity.setTheme(if (load(activity).light) R.style.AppThemeLight else R.style.AppTheme)
    }

    fun apply(activity: Activity) {
        val value = load(activity)
        activity.window.statusBarColor = value.background
        activity.window.navigationBarColor = value.background
        activity.window.decorView.systemUiVisibility = if (value.light) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
        installTypography(activity)
    }

    private fun installTypography(activity: Activity) {
        if (typographyListeners.containsKey(activity)) return
        val listener = ViewTreeObserver.OnGlobalLayoutListener { styleTree(activity.window.decorView, activity) }
        typographyListeners[activity] = true
        activity.window.decorView.viewTreeObserver.addOnGlobalLayoutListener(listener)
        activity.window.decorView.post { styleTree(activity.window.decorView, activity) }
    }

    fun previewTypography(view: TextView, font: String, scale: Float) {
        previewText[view] = true
        view.typeface = Typeface.create(font, Typeface.NORMAL)
        view.textSize = 22f * scale
    }

    private fun styleTree(view: View, context: Context) {
        if (view is AppWidgetHostView) return
        if (view is TextView && previewText[view] != true) {
            val original = originalTextSizes.getOrPut(view) { view.textSize }
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, original * fontScale(context))
            view.typeface = Typeface.create(font(context), view.typeface?.style ?: Typeface.NORMAL)
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) styleTree(view.getChildAt(index), context)
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
