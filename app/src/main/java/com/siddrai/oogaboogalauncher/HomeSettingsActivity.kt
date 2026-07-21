package com.siddrai.oogaboogalauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class HomeSettingsActivity : Activity() {
    private lateinit var store: AppStore

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.prepare(this); super.onCreate(savedInstanceState); AppTheme.apply(this)
        store = AppStore(this); render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(40), dp(24), dp(20)); setBackgroundColor(MainActivity.BG)
        }
        root.addView(TextView(this).apply {
            text = "HOME & SCREENS"; textSize = 28f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setTextColor(MainActivity.INK); setPadding(0, 0, 0, dp(24))
        })
        root.addView(option("HOME SCREEN APPS") { selectApps() })
        root.addView(toggle("DEFAULT CLOCK ON HOME SCREEN", store.showClock()) { store.setShowClock(it) })
        root.addView(option("BOTTOM SHORTCUTS") { startActivity(Intent(this, QuickAppsActivity::class.java)) })
        root.addView(toggle("LEFT WIDGET SCREEN", store.leftScreen()) { store.setLeftScreen(it); render() })
        root.addView(toggle("RIGHT WIDGET SCREEN", store.rightScreen()) { store.setRightScreen(it); render() })
        root.addView(toggle("WIDGET DELETE HAPTICS", store.widgetDeleteHaptics()) { store.setWidgetDeleteHaptics(it); if (it) store.performWidgetHaptic(false) })
        setContentView(ScrollView(this).apply { isFillViewport = true; setBackgroundColor(MainActivity.BG); addView(root) })
    }
    private fun selectApps() = startActivity(Intent(this, AppSelectionActivity::class.java).putExtra("mode", "pinned"))
    private fun option(title: String, action: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setOnClickListener { action() }
        addView(TextView(this@HomeSettingsActivity).apply { text = title; textSize = 16f; gravity = Gravity.CENTER_VERTICAL; setTextColor(MainActivity.INK) },
            LinearLayout.LayoutParams(0, dp(56), 1f))
        addView(TextView(this@HomeSettingsActivity).apply { text = "›"; textSize = 24f; setTextColor(MainActivity.MUTED) })
    }
    private fun toggle(title: String, checked: Boolean, change: (Boolean) -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@HomeSettingsActivity).apply { text = title; textSize = 16f; gravity = Gravity.CENTER_VERTICAL; setTextColor(MainActivity.INK) },
            LinearLayout.LayoutParams(0, dp(56), 1f))
        addView(Switch(this@HomeSettingsActivity).apply { isChecked = checked; setOnCheckedChangeListener { _, value -> change(value) } })
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
