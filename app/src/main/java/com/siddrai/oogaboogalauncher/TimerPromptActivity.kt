package com.siddrai.oogaboogalauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class TimerPromptActivity : Activity() {
    private lateinit var store: AppStore
    private lateinit var targetPackage: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.apply(this); val palette = AppTheme.current
        store = AppStore(this); targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: return finish()
        if (store.activePackage() == targetPackage) return finish()
        val label = store.apps().firstOrNull { it.packageName == targetPackage }?.label ?: targetPackage
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(22), dp(24), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.argb(236, Color.red(palette.background), Color.green(palette.background), Color.blue(palette.background)))
                cornerRadius = dp(26).toFloat(); setStroke(dp(1), palette.line)
            }
        }
        card.addView(TextView(this).apply {
            text = label; textSize = 23f; typeface = Typeface.create(AppTheme.font(this@TimerPromptActivity), Typeface.BOLD)
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
            }; setOnClickListener { start(minutes) }
        }, LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginStart = dp(3); marginEnd = dp(3) }) }
        card.addView(choices)
        card.addView(option("CUSTOM TIME", palette.accent) { custom() })
        card.addView(option("CANCEL", palette.muted) { closeTarget() })
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                marginStart = dp(22); marginEnd = dp(22)
            })
        })
        window.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = .52f; if (android.os.Build.VERSION.SDK_INT >= 31) blurBehindRadius = 48 }
            if (android.os.Build.VERSION.SDK_INT >= 31) addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
    }

    private fun option(label: String, colour: Int, click: () -> Unit) = TextView(this).apply {
        text = label; textSize = 11f; letterSpacing = .08f; gravity = Gravity.CENTER; setTextColor(colour)
        setPadding(0, dp(14), 0, dp(10)); setOnClickListener { click() }
    }

    private fun custom() {
        val input = EditText(this).apply { hint = "Minutes"; inputType = InputType.TYPE_CLASS_NUMBER }
        val dialog = AlertDialog.Builder(this).setTitle("CUSTOM TIMER").setView(input)
            .setPositiveButton("START", null).setNegativeButton("CANCEL", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = input.text.toString().toIntOrNull()
            if (value != null && value > 0) { dialog.dismiss(); start(value) } else input.error = "Enter at least 1 minute"
        } }
        dialog.show()
    }

    private fun start(minutes: Int) {
        store.beginSession(targetPackage, minutes)
        val notificationIntent = NotificationLaunchBridge.consume(targetPackage)
        val openedNotification = if (notificationIntent != null) runCatching {
            notificationIntent.send(); true
        }.getOrDefault(false) else false
        if (!openedNotification) packageManager.getLaunchIntentForPackage(targetPackage)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }?.let(::startActivity)
        finish()
    }
    private fun closeTarget() {
        NotificationLaunchBridge.discard(targetPackage)
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish()
    }
    @Deprecated("Launcher prompt owns Back behavior")
    override fun onBackPressed() = closeTarget()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { const val EXTRA_PACKAGE = "target_package" }
}
