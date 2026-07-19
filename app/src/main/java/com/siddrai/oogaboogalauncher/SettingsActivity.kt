package com.siddrai.oogaboogalauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.prepare(this); super.onCreate(savedInstanceState); AppTheme.apply(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(34), dp(24), dp(20)); setBackgroundColor(MainActivity.BG)
        }
        root.addView(TextView(this).apply {
            text = "SETTINGS"; textSize = 30f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setTextColor(MainActivity.INK); setPadding(0, 0, 0, dp(22))
        })
        root.addView(option("HOME & SCREENS") { startActivity(Intent(this, HomeSettingsActivity::class.java)) })
        root.addView(option("HIDDEN APPS") { select("hidden") })
        root.addView(option("DISTRACTING APPS") { select("distracting") })
        root.addView(option("SHUTUP LIST") { select("shutup") })
        root.addView(option("NOTIFICATION ACCESS") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        root.addView(option("TIME-LIMIT SERVICE") { startActivity(Intent(this, AccessibilityHelpActivity::class.java)) })
        root.addView(option("APPEARANCE") { startActivityForResult(Intent(this, ThemeEditorActivity::class.java), 71) })
        root.addView(option("PRIVACY POLICY") { startActivity(Intent(this, PrivacyPolicyActivity::class.java)) })
        setContentView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; isFillViewport = true; setBackgroundColor(MainActivity.BG); addView(root)
        })
    }

    private fun option(title: String, action: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setOnClickListener { action() }
        addView(TextView(this@SettingsActivity).apply { text = title; textSize = 16f; gravity = Gravity.CENTER_VERTICAL; setTextColor(MainActivity.INK) },
            LinearLayout.LayoutParams(0, dp(52), 1f))
        addView(TextView(this@SettingsActivity).apply { text = "›"; textSize = 24f; setTextColor(MainActivity.MUTED) })
    }
    private fun select(mode: String) = startActivity(Intent(this, AppSelectionActivity::class.java).putExtra("mode", mode))
    @Deprecated("Legacy result API keeps this dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 71 && resultCode == RESULT_OK) recreate()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
