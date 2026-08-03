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

class PermissionsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        AppTheme.prepare(this); super.onCreate(savedInstanceState); AppTheme.apply(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(40), dp(24), dp(20)); setBackgroundColor(MainActivity.BG)
        }
        root.addView(TextView(this).apply {
            text = "APP PERMISSIONS"; textSize = 28f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setTextColor(MainActivity.INK); setPadding(0, 0, 0, dp(24))
        })
        root.addView(option("NOTIFICATION ACCESS") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        root.addView(option("TIME-LIMIT SERVICE") { startActivity(Intent(this, AccessibilityHelpActivity::class.java)) })
        setContentView(ScrollView(this).apply { isFillViewport = true; setBackgroundColor(MainActivity.BG); addView(root) })
    }

    private fun option(title: String, action: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setOnClickListener { action() }
        addView(TextView(this@PermissionsActivity).apply {
            text = title; textSize = 16f; gravity = Gravity.CENTER_VERTICAL; setTextColor(MainActivity.INK)
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
        addView(TextView(this@PermissionsActivity).apply { text = "›"; textSize = 24f; setTextColor(MainActivity.MUTED) })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
