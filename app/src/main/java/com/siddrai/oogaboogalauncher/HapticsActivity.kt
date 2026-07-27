package com.siddrai.oogaboogalauncher

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class HapticsActivity : Activity() {
    private lateinit var store: AppStore

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        AppTheme.prepare(this); super.onCreate(savedInstanceState); AppTheme.apply(this)
        store = AppStore(this); render()
    }

    private fun render() {
        val palette = AppTheme.current
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(40), dp(24), dp(20)); setBackgroundColor(palette.background)
        }
        root.addView(TextView(this).apply {
            text = "HAPTICS"; textSize = 28f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setTextColor(palette.foreground); setPadding(0, 0, 0, dp(16))
        })

        root.addView(toggle("SYSTEM HAPTICS", store.hapticsEnabled()) {
            store.setHapticsEnabled(it)
            if (it) store.performHaptic(HapticKind.ACTION)
        })

        root.addView(sectionHeader("WIDGET HAPTICS"))
        root.addView(toggle("WIDGET HOVER", store.widgetHoverHaptics()) {
            store.setWidgetHoverHaptics(it)
            if (it) store.performHaptic(HapticKind.WIDGET_HOVER)
        })
        root.addView(toggle("WIDGET DELETE", store.widgetDeleteHaptics()) {
            store.setWidgetDeleteHaptics(it)
            if (it) store.performHaptic(HapticKind.WIDGET_DELETE)
        })
        root.addView(toggle("WIDGET RESIZE", store.widgetResizeHaptics()) {
            store.setWidgetResizeHaptics(it)
            if (it) store.performHaptic(HapticKind.WIDGET_RESIZE)
        })

        root.addView(sectionHeader("LAUNCHER HAPTICS"))
        root.addView(toggle("SCREEN NAVIGATION", store.navigationHaptics()) {
            store.setNavigationHaptics(it)
            if (it) store.performHaptic(HapticKind.NAVIGATION)
        })
        root.addView(toggle("ACTIONS & BUTTONS", store.actionHaptics()) {
            store.setActionHaptics(it)
            if (it) store.performHaptic(HapticKind.ACTION)
        })
        root.addView(toggle("CHECKBOXES & LISTS", store.checkboxHaptics()) {
            store.setCheckboxHaptics(it)
            if (it) store.performHaptic(HapticKind.CHECKBOX)
        })

        setContentView(ScrollView(this).apply { isFillViewport = true; setBackgroundColor(palette.background); addView(root) })
    }

    private fun sectionHeader(title: String) = TextView(this).apply {
        text = title; textSize = 11f; letterSpacing = .1f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(AppTheme.current.accent); setPadding(0, dp(20), 0, dp(4))
    }

    private fun toggle(title: String, checked: Boolean, change: (Boolean) -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@HapticsActivity).apply { text = title; textSize = 15f; gravity = Gravity.CENTER_VERTICAL; setTextColor(AppTheme.current.foreground) },
            LinearLayout.LayoutParams(0, dp(52), 1f))
        addView(Switch(this@HapticsActivity).apply { isChecked = checked; setOnCheckedChangeListener { _, value -> change(value) } })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

