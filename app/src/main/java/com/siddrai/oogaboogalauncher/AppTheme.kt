package com.siddrai.oogaboogalauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.fonts.SystemFonts
import android.os.Build
import android.appwidget.AppWidgetHostView
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.util.WeakHashMap

data class Palette(val background: Int, val foreground: Int, val muted: Int,
                   val accent: Int, val line: Int, val light: Boolean)
data class FontOption(val label: String, val spec: String)

object AppTheme {
    private const val LIGHT = "theme_light"
    private const val BACKGROUND = "theme_background"
    private const val FOREGROUND = "theme_foreground"
    private const val FONT = "font_family"
    private const val FONT_SCALE = "font_scale"
    private val originalTextSizes = WeakHashMap<TextView, Float>()
    private val watchedGroups = WeakHashMap<ViewGroup, Boolean>()
    private val previewText = WeakHashMap<TextView, Boolean>()
    private var cachedFont: String? = null
    private var cachedFontScale: Float? = null
    private var cachedFonts: List<FontOption>? = null
    private val typefaceCache = HashMap<Pair<String, Int>, Typeface>()

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

    fun font(context: Context): String = cachedFont ?: (context.getSharedPreferences(AppStore.PREFS, Context.MODE_PRIVATE)
        .getString(FONT, "monospace") ?: "monospace").also { cachedFont = it }

    fun fontScale(context: Context): Float = cachedFontScale ?: context.getSharedPreferences(AppStore.PREFS, Context.MODE_PRIVATE)
        .getFloat(FONT_SCALE, 1f).coerceIn(.8f, 1.3f).also { cachedFontScale = it }

    fun saveTypography(context: Context, font: String, scale: Float) {
        cachedFont = font; cachedFontScale = scale.coerceIn(.8f, 1.3f); typefaceCache.clear()
        context.getSharedPreferences(AppStore.PREFS, Context.MODE_PRIVATE).edit()
            .putString(FONT, font).putFloat(FONT_SCALE, scale.coerceIn(.8f, 1.3f)).apply()
    }

    fun availableFonts(): List<FontOption> {
        cachedFonts?.let { return it }
        val generic = listOf(
            FontOption("Monospace", "monospace"),
            FontOption("Sans Serif", "sans-serif"),
            FontOption("Serif", "serif")
        )
        if (Build.VERSION.SDK_INT < 29) return generic
        val installed = SystemFonts.getAvailableFonts().mapNotNull { font ->
            val file = font.file ?: return@mapNotNull null
            val label = file.nameWithoutExtension
                .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                .replace(Regex("[-_]+"), " ")
            FontOption(label, file.absolutePath)
        }.distinctBy { it.spec }.sortedBy { it.label.lowercase() }
        return (generic + installed).also { cachedFonts = it }
    }

    fun fontLabel(spec: String) = availableFonts().firstOrNull { it.spec == spec }?.label ?: "System font"

    fun typeface(context: Context, style: Int = Typeface.NORMAL) = typeface(font(context), style)

    fun typeface(spec: String, style: Int = Typeface.NORMAL): Typeface = typefaceCache.getOrPut(spec to style) {
        val base = if (spec.startsWith("/")) runCatching { Typeface.createFromFile(spec) }.getOrNull()
            else Typeface.create(spec, Typeface.NORMAL)
        Typeface.create(base ?: Typeface.MONOSPACE, style)
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
        watchTree(activity.window.decorView, activity)
    }

    fun previewTypeface(view: TextView, font: String) {
        previewText[view] = true
        view.typeface = typeface(font, view.typeface?.style ?: Typeface.NORMAL)
    }

    fun previewTypography(view: TextView, font: String, scale: Float) {
        previewTypeface(view, font)
        view.textSize = 22f * scale
    }

    private fun styleTree(view: View, context: Context) {
        if (view is AppWidgetHostView) return
        if (view is TextView && previewText[view] != true) {
            val original = originalTextSizes.getOrPut(view) { view.textSize }
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, original * fontScale(context))
            view.typeface = typeface(context, view.typeface?.style ?: Typeface.NORMAL)
        }
    }

    private fun watchTree(view: View, context: Context) {
        styleTree(view, context)
        if (view !is ViewGroup || view is AppWidgetHostView || watchedGroups.put(view, true) != null) return
        view.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) { child?.let { watchTree(it, context) } }
            override fun onChildViewRemoved(parent: View?, child: View?) = Unit
        })
        for (index in 0 until view.childCount) watchTree(view.getChildAt(index), context)
    }

    fun showMenu(activity: Activity, title: String?, actions: Array<String>, onSelect: (Int) -> Unit) {
        val palette = load(activity)
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(14))
            background = GradientDrawable().apply {
                setColor(palette.background); cornerRadius = dp(24).toFloat(); setStroke(dp(1), palette.line)
            }
        }
        title?.let { label -> card.addView(TextView(activity).apply {
            text = label; textSize = 21f; setTextColor(palette.foreground)
            typeface = typeface(activity, Typeface.BOLD); setPadding(dp(2), 0, dp(2), dp(12))
        }) }
        lateinit var dialog: AlertDialog
        actions.forEach { action -> card.addView(TextView(activity).apply {
            text = action; textSize = 15f; gravity = Gravity.CENTER_VERTICAL; setTextColor(palette.foreground)
            setPadding(dp(2), 0, dp(2), 0); background = GradientDrawable().apply {
                setColor(blend(palette.foreground, palette.background, .07f)); cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { val index = actions.indexOf(action); dialog.dismiss(); onSelect(index) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { bottomMargin = dp(7) }) }
        dialog = AlertDialog.Builder(activity).setView(card).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setDimAmount(.42f)
        }
        dialog.show()
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
