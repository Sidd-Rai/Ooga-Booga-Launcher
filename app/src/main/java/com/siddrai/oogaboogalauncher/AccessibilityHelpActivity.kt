package com.siddrai.oogaboogalauncher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AccessibilityHelpActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.prepare(this); super.onCreate(savedInstanceState); AppTheme.apply(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(40), dp(24), dp(24)); setBackgroundColor(MainActivity.BG)
        }
        root.addView(TextView(this).apply { text = "TIME-LIMIT ACCESS"; textSize = 28f; setTextColor(MainActivity.INK) })
        root.addView(TextView(this).apply {
            text = "Android may label this as a restricted setting because the app was installed outside an app store. Open App Info, use the top-right menu, and choose ‘Allow restricted settings’. Then return here and open Accessibility."
            textSize = 15f; setTextColor(MainActivity.INK); setPadding(0, dp(24), 0, dp(26))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply {
            text = "1. Open App Info"; isAllCaps = false
            setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        })
        root.addView(Button(this).apply {
            text = "2. Open Accessibility"; isAllCaps = false
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        setContentView(root)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
