package com.nolauncher

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class TimeLimitService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: AppStore
    private var foregroundPackage: String? = null
    private var promptingPackage: String? = null
    private var overlay: View? = null

    private val check = object : Runnable {
        override fun run() {
            val active = store.activePackage()
            val expired = store.activeUntil() > 0 && System.currentTimeMillis() >= store.activeUntil()
            if (active != null && expired && overlay == null) {
                if (foregroundPackage == active) {
                    if (store.extensions() >= 3) closeApp("OOPS — out of time") else showTimeUp()
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onServiceConnected() {
        store = AppStore(this); handler.removeCallbacks(check); handler.post(check)
    }

    private fun showTimeUp() {
        val palette = AppTheme.load(this)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(28), dp(28), dp(24))
            background = GradientDrawable().apply { setColor(palette.background); cornerRadius = dp(22).toFloat() }
        }
        card.addView(TextView(this).apply {
            text = "OOPS — OUT OF TIME"; textSize = 23f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER; setTextColor(palette.foreground); setPadding(0, 0, 0, dp(10))
        })
        card.addView(TextView(this).apply {
            text = "${3 - store.extensions()} extensions remaining"; textSize = 13f; gravity = Gravity.CENTER
            setTextColor(palette.muted); setPadding(0, 0, 0, dp(18))
        })
        card.addView(Button(this).apply {
            text = "Extend timer"; isAllCaps = false
            setOnClickListener { if (store.extendSession()) removeOverlay() else closeApp("No extensions left") }
        })
        card.addView(Button(this).apply {
            text = "Close app"; isAllCaps = false; setOnClickListener { closeApp(null) }
        })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER; dimAmount = .72f; horizontalMargin = .06f }
        overlay = card
        (getSystemService(WINDOW_SERVICE) as WindowManager).addView(card, params)
    }

    private fun closeApp(message: String?) {
        removeOverlay(); store.clearSession(); performGlobalAction(GLOBAL_ACTION_HOME)
        message?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
    }
    private fun removeOverlay() {
        overlay?.let { runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } }
        overlay = null
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val next = event?.packageName?.toString() ?: return
        if (next == packageName) return
        if (next != promptingPackage) promptingPackage = null
        foregroundPackage = next
        val active = store.activePackage()
        if (active != null) {
            if (next == active) store.resumeSession() else store.pauseSession()
        } else if (next in store.distracting() && promptingPackage != next) {
            promptingPackage = next
            startActivity(android.content.Intent(this, TimerPromptActivity::class.java).apply {
                putExtra(TimerPromptActivity.EXTRA_PACKAGE, next)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            })
        }
    }
    override fun onInterrupt() = Unit
    override fun onDestroy() { removeOverlay(); handler.removeCallbacks(check); super.onDestroy() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
