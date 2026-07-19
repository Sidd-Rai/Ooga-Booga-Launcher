package com.nolauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class TimerPromptActivity : Activity() {
    private lateinit var store: AppStore
    private lateinit var targetPackage: String

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.prepare(this); super.onCreate(savedInstanceState); AppTheme.apply(this)
        store = AppStore(this); targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: return finish()
        if (store.activePackage() == targetPackage) return finish()
        val label = store.apps().firstOrNull { it.packageName == targetPackage }?.label ?: targetPackage
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(MainActivity.BG)
        }
        root.addView(TextView(this).apply {
            text = label; textSize = 25f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); setTextColor(MainActivity.INK)
        })
        root.addView(TextView(this).apply {
            text = "Choose a time limit"; textSize = 13f; setTextColor(MainActivity.MUTED); setPadding(0, dp(6), 0, dp(22))
        })
        listOf(1, 5, 10, 15).forEach { minutes -> root.addView(option("$minutes MINUTES") { start(minutes) }) }
        root.addView(option("CUSTOM") { custom() })
        root.addView(option("CANCEL") { closeTarget() })
        setContentView(root)
    }

    private fun option(label: String, click: () -> Unit) = TextView(this).apply {
        text = label; textSize = 14f; gravity = Gravity.CENTER; setTextColor(MainActivity.INK)
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
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
    private fun start(minutes: Int) { store.beginSession(targetPackage, minutes); finish() }
    private fun closeTarget() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish()
    }
    @Deprecated("Launcher prompt owns Back behavior")
    override fun onBackPressed() = closeTarget()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    companion object { const val EXTRA_PACKAGE = "target_package" }
}
