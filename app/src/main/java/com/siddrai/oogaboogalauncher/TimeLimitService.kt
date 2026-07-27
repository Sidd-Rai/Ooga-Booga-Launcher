package com.siddrai.oogaboogalauncher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class TimeLimitService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: AppStore
    private var foregroundPackage: String? = null
    private var promptingPackage: String? = null
    private var promptOverlayPackage: String? = null
    private var sessionInForeground = false
    private var overlay: View? = null
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (sessionInForeground || (foregroundPackage != null && foregroundPackage in store.distracting())) {
                        store.markScreenLocked()
                    } else {
                        store.pauseSession()
                        store.clearScreenLockMarker()
                    }
                    sessionInForeground = false
                    handler.removeCallbacks(expiry)
                }
                Intent.ACTION_USER_PRESENT -> {
                    val invalid = store.unlockInvalidated(LOCK_RESET_MS)
                    val target = foregroundPackage ?: store.lastForegroundPackage()
                    if (target != null && target in store.distracting()) {
                        foregroundPackage = target
                        val active = store.activePackage()
                        if (invalid || active == null || active != target || store.remainingSessionMs() <= 0L) {
                            store.clearSession()
                            promptFor(target)
                        } else {
                            sessionInForeground = true
                            if (store.resumeSession()) scheduleExpiry() else showExpired()
                        }
                    }
                }
            }
        }
    }

    private val expiry = Runnable {
        val active = store.activePackage()
        if (active != null && foregroundPackage == active && store.activeUntil() > 0L) {
            if (System.currentTimeMillis() >= store.activeUntil()) {
                if (store.extensions() >= 3) closeApp("OOPS — out of time") else showTimeUp()
            } else scheduleExpiry()
        }
    }

    private fun scheduleExpiry() {
        handler.removeCallbacks(expiry)
        val delay = store.activeUntil() - System.currentTimeMillis()
        if (delay > 0L && store.activePackage() == foregroundPackage) handler.postDelayed(expiry, delay)
    }

    override fun onServiceConnected() {
        store = AppStore(this)
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
            else registerReceiver(screenReceiver, filter)
            receiverRegistered = true
        }
        val invalid = store.unlockInvalidated(LOCK_RESET_MS)
        foregroundPackage = store.lastForegroundPackage()
        if (invalid && foregroundPackage != null && foregroundPackage in store.distracting()) {
            store.clearSession()
            promptFor(foregroundPackage!!)
        } else {
            foregroundPackage?.let { handleForeground(it) }
        }
    }

    private fun isInputMethod(pkg: String): Boolean {
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return false
        val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
        if (pkg == defaultIme) return true
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
        return imm.enabledInputMethodList.any { it.packageName == pkg }
    }

    private fun promptFor(targetPackage: String) {
        if (promptingPackage == targetPackage || promptOverlayPackage == targetPackage) return
        promptingPackage = targetPackage
        showTimerPrompt(targetPackage)
    }

    private fun showExpired() {
        if (overlay != null) return
        if (store.extensions() >= 3) closeApp("OOPS — out of time") else showTimeUp()
    }

    private fun showTimerPrompt(targetPackage: String) {
        val palette = AppTheme.load(this)
        val label = store.apps().firstOrNull { it.packageName == targetPackage }?.label ?: targetPackage
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(22), dp(24), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.argb(236, Color.red(palette.background), Color.green(palette.background), Color.blue(palette.background)))
                cornerRadius = dp(26).toFloat(); setStroke(dp(1), palette.line)
            }
        }
        card.addView(TextView(this).apply {
            text = label; textSize = 23f; typeface = AppTheme.typeface(this@TimeLimitService, Typeface.BOLD)
            setTextColor(palette.foreground)
        })
        card.addView(TextView(this).apply {
            text = "Choose a time limit"; textSize = 13f; setTextColor(palette.muted); setPadding(0, dp(4), 0, dp(18))
        })
        val choices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(1, 5, 10, 15).forEach { minutes -> choices.addView(TextView(this).apply {
            text = "$minutes\nMIN"; textSize = 13f; gravity = Gravity.CENTER; setTextColor(palette.foreground)
            setPadding(dp(4), dp(12), dp(4), dp(12)); background = GradientDrawable().apply {
                setColor(Color.argb(70, Color.red(palette.foreground), Color.green(palette.foreground), Color.blue(palette.foreground)))
                cornerRadius = dp(16).toFloat()
            }
            setOnClickListener { beginTimer(targetPackage, minutes) }
        }, LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginStart = dp(3); marginEnd = dp(3) }) }
        card.addView(choices)
        card.addView(promptAction("CUSTOM TIME", palette.accent) { showCustomTime(card, targetPackage, palette) })
        card.addView(promptAction("CANCEL", palette.muted) { cancelPrompt(targetPackage) })
        promptOverlayPackage = targetPackage
        showOverlay(card, .52f)
    }

    private fun showCustomTime(card: LinearLayout, targetPackage: String, palette: Palette) {
        card.removeViews(2, card.childCount - 2)
        val input = EditText(this).apply {
            hint = "Minutes"; inputType = InputType.TYPE_CLASS_NUMBER; setTextColor(palette.foreground)
            setHintTextColor(palette.muted); isSingleLine = true
        }
        card.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)))
        card.addView(promptAction("START", palette.accent) {
            val minutes = input.text.toString().toIntOrNull()
            if (minutes != null && minutes > 0) beginTimer(targetPackage, minutes) else input.error = "Enter at least 1 minute"
        })
        card.addView(promptAction("CANCEL", palette.muted) { cancelPrompt(targetPackage) })
        input.requestFocus()
        (getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(card, card.layoutParams)
        input.post { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun promptAction(label: String, colour: Int, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 11f; letterSpacing = .08f; gravity = Gravity.CENTER; setTextColor(colour)
        setPadding(0, dp(14), 0, dp(10)); setOnClickListener { action() }
    }

    private fun beginTimer(targetPackage: String, minutes: Int) {
        store.performHaptic(HapticKind.ACTION)
        store.beginSession(targetPackage, minutes)
        sessionInForeground = (foregroundPackage == targetPackage)
        scheduleExpiry()
        promptingPackage = null; promptOverlayPackage = null; removeOverlay()
    }

    private fun cancelPrompt(targetPackage: String) {
        promptingPackage = null; promptOverlayPackage = null; removeOverlay()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun showTimeUp() {
        val palette = AppTheme.load(this)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(28), dp(28), dp(24))
            background = GradientDrawable().apply { setColor(palette.background); cornerRadius = dp(22).toFloat() }
        }
        card.addView(TextView(this).apply {
            text = "OOPS — OUT OF TIME"; textSize = 23f; typeface = AppTheme.typeface(this@TimeLimitService, Typeface.BOLD)
            gravity = Gravity.CENTER; setTextColor(palette.foreground); setPadding(0, 0, 0, dp(10))
        })
        card.addView(TextView(this).apply {
            text = "${3 - store.extensions()} extensions remaining"; textSize = 13f; gravity = Gravity.CENTER
            setTextColor(palette.muted); setPadding(0, 0, 0, dp(18))
        })
        card.addView(Button(this).apply {
            text = "Extend timer"; isAllCaps = false
            setOnClickListener { if (store.extendSession()) { scheduleExpiry(); removeOverlay() } else closeApp("No extensions left") }
        })
        card.addView(Button(this).apply { text = "Close app"; isAllCaps = false; setOnClickListener { closeApp(null) } })
        showOverlay(card, .72f)
    }

    private fun showOverlay(view: View, dim: Float) {
        removeOverlay()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER; dimAmount = dim; horizontalMargin = .06f }
        overlay = view
        (getSystemService(WINDOW_SERVICE) as WindowManager).addView(view, params)
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
        handleForeground(next)
    }

    private fun handleForeground(next: String) {
        if (isInputMethod(next)) return
        if (next == packageName) {
            store.setLastForegroundPackage(next); sessionInForeground = false
            foregroundPackage = next; store.pauseSession(); handler.removeCallbacks(expiry)
            if (promptOverlayPackage != null) { promptOverlayPackage = null; promptingPackage = null; removeOverlay() }
            return
        }
        if (next == "com.android.systemui") {
            sessionInForeground = false
            store.pauseSession(); handler.removeCallbacks(expiry); return
        }
        promptOverlayPackage?.let { target ->
            if (next == target) { foregroundPackage = next; return }
            promptOverlayPackage = null; promptingPackage = null; removeOverlay()
        }
        if (next != promptingPackage) promptingPackage = null
        store.setLastForegroundPackage(next)
        foregroundPackage = next
        val active = store.activePackage()
        when {
            active == next -> {
                if (store.remainingSessionMs() <= 0L) {
                    sessionInForeground = false
                    store.clearSession()
                    promptFor(next)
                } else {
                    sessionInForeground = true
                    if (store.resumeSession()) scheduleExpiry() else showExpired()
                }
            }
            active != null && next in store.distracting() -> {
                sessionInForeground = false; store.pauseSession(); store.clearSession(); promptFor(next)
            }
            active != null -> { sessionInForeground = false; store.pauseSession(); handler.removeCallbacks(expiry) }
            next in store.distracting() -> { sessionInForeground = false; promptFor(next) }
            else -> sessionInForeground = false
        }
    }
    override fun onInterrupt() = Unit
    override fun onDestroy() {
        removeOverlay(); handler.removeCallbacks(expiry)
        if (receiverRegistered) runCatching { unregisterReceiver(screenReceiver) }
        receiverRegistered = false; super.onDestroy()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { private const val LOCK_RESET_MS = 2 * 60_000L }
}

