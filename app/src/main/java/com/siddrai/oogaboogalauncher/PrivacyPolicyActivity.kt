package com.siddrai.oogaboogalauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class PrivacyPolicyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.prepare(this)
        super.onCreate(savedInstanceState)
        AppTheme.apply(this)

        val palette = AppTheme.current
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(36))
            setBackgroundColor(palette.background)
        }

        content.addView(title("Privacy Policy"))
        content.addView(body("Last updated: July 19, 2026", italic = true))

        content.addView(heading("TL;DR"))
        content.addView(body("Ooga Booga Launcher does not collect or transmit your data."))
        content.addView(body("Seriously."))
        content.addView(body("The app does not request the Android INTERNET permission, so it cannot send your launcher data anywhere."))

        content.addView(heading("Data collection"))
        content.addView(body("Ooga Booga Launcher does not include:"))
        content.addView(bullets(listOf(
            "Analytics",
            "Trackers",
            "Advertising",
            "Accounts",
            "Cloud synchronization",
            "Remote logging",
            "Anonymous usage statistics"
        )))

        content.addView(heading("Data stored on your device"))
        content.addView(body("The app stores only the local configuration required to operate, including:"))
        content.addView(bullets(listOf(
            "Home-screen, hidden, distracting, and ShutUp app selections",
            "Theme colours and clock preferences",
            "Widget identifiers, positions, and sizes",
            "Shortcut selections",
            "Active timer state and extension count"
        )))
        content.addView(body("This information remains in the app's private storage on your device. It is not uploaded or shared."))

        content.addView(heading("Android access used"))
        content.addView(body("Depending on the features you enable, the app may use:"))
        content.addView(bullets(listOf(
            "Installed-app visibility to display and launch applications",
            "Accessibility access to detect foreground apps, enforce timers, and display timer-expiry controls",
            "Notification access to dismiss notifications from apps on the ShutUp list",
            "Wallpaper access to apply the selected solid theme colour",
            "Home role to operate as your launcher"
        )))
        content.addView(body("Accessibility and Notification access are optional and controlled through Android Settings."))

        content.addView(heading("Internet access"))
        content.addView(body("The app does not declare the Android INTERNET permission. It has no analytics endpoint, advertising service, account system, or cloud backend."))

        content.addView(heading("Changes to this policy"))
        content.addView(body("If the app changes in a way that affects privacy, this document will be updated."))
        content.addView(body("Though if that ever involves adding trackers, you're legally allowed to be disappointed."))

        content.addView(body("Made with hate (mostly at other launchers)", bold = true).apply {
            setPadding(0, dp(24), 0, dp(10))
        })
        content.addView(TextView(this).apply {
            text = REPOSITORY_URL
            textSize = 14f
            setTextColor(palette.accent)
            setPadding(0, dp(4), 0, dp(8))
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL))) }
        })
        content.addView(body("~Sid", bold = true))

        setContentView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            setBackgroundColor(palette.background)
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun title(value: String) = TextView(this).apply {
        text = value
        textSize = 30f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        setTextColor(AppTheme.current.foreground)
        setPadding(0, 0, 0, dp(16))
    }

    private fun heading(value: String) = TextView(this).apply {
        text = value
        textSize = 20f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        setTextColor(AppTheme.current.foreground)
        setPadding(0, dp(22), 0, dp(8))
    }

    private fun body(value: String, bold: Boolean = false, italic: Boolean = false) = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(AppTheme.current.foreground)
        typeface = Typeface.create(Typeface.SANS_SERIF, when {
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        })
        setLineSpacing(0f, 1.15f)
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun bullets(items: List<String>) = body(items.joinToString("\n") { "• $it" }).apply {
        setPadding(dp(10), dp(4), 0, dp(6))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REPOSITORY_URL = "https://github.com/Sidd-Rai/Ooga-Booga-Launcher"
    }
}
