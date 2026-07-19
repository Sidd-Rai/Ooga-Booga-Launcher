package com.nolauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class AppSelectionActivity : Activity() {
    private lateinit var store: AppStore
    private lateinit var mode: String
    private lateinit var selected: MutableSet<String>
    private var editing = true
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.prepare(this); super.onCreate(savedInstanceState); AppTheme.apply(this)
        store = AppStore(this)
        mode = intent.getStringExtra("mode") ?: "pinned"
        editing = mode != "hidden"
        selected = when (mode) {
            "hidden" -> store.hidden().toMutableSet()
            "distracting" -> store.distracting().toMutableSet()
            "shutup" -> store.shutUp().toMutableSet()
            else -> store.pinned().toMutableSet()
        }
        render()
    }

    override fun onPause() { saveSelection(); super.onPause() }

    private fun saveSelection() {
        when (mode) {
            "hidden" -> {
                store.setHidden(selected)
                store.setPinned(store.pinned() - selected)
            }
            "distracting" -> store.setDistracting(selected)
            "shutup" -> store.setShutUp(selected)
            else -> {
                store.setPinned(selected)
                store.setHidden(store.hidden() - selected)
            }
        }
    }

    private fun render() {
        val apps = store.apps()
        val title = when (mode) {
            "hidden" -> "HIDDEN APPS"
            "distracting" -> "DISTRACTING APPS"
            "shutup" -> "SHUTUP LIST"
            else -> "HOME SCREEN"
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), 0); setBackgroundColor(MainActivity.BG)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = title; textSize = 25f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); setTextColor(MainActivity.INK)
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        if (mode == "hidden") header.addView(action(if (editing) "DONE" else "EDIT") {
            if (editing) saveSelection()
            editing = !editing; render()
        })
        root.addView(header)
        root.addView(TextView(this).apply {
            text = if (mode == "hidden" && !editing) "TAP TO OPEN  •  HOLD FOR OPTIONS" else "TAP TO TOGGLE  •  SAVED AUTOMATICALLY"
            textSize = 9f; letterSpacing = .1f; setTextColor(MainActivity.MUTED); setPadding(0, dp(3), 0, dp(8))
        })

        lateinit var search: EditText
        lateinit var rows: LinearLayout
        fun populate(filter: String) {
            rows.removeAllViews()
            apps.asSequence()
                .filter { it.label.contains(filter, ignoreCase = true) }
                .filter { mode != "hidden" || editing || it.packageName in selected }
                .forEach { app ->
                    if (editing) rows.addView(checkRow(app), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
                    else rows.addView(appRow(app), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
                }
        }
        if (editing) root.addView(LinearLayout(this).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            addView(action("SELECT ALL") { selected.clear(); selected.addAll(apps.map { it.packageName }); populate(query) })
            addView(action("UNSELECT ALL") { selected.clear(); populate(query) })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        search = EditText(this).apply {
            hint = "SEARCH APPS"; setHintTextColor(MainActivity.MUTED); setTextColor(MainActivity.INK)
            textSize = 14f; isSingleLine = true; setText(query); setPadding(dp(10), 0, dp(10), 0)
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(8) })
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty(); populate(query)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        populate(query)
        root.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(rows) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun checkRow(app: LaunchableApp) = CheckBox(this).apply {
        text = app.label; textSize = 15f; gravity = Gravity.CENTER_VERTICAL; setTextColor(MainActivity.INK)
        buttonTintList = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(MainActivity.ACCENT, MainActivity.MUTED))
        isChecked = app.packageName in selected; setPadding(dp(2), 0, 0, 0)
        setOnCheckedChangeListener { _, checked -> if (checked) selected.add(app.packageName) else selected.remove(app.packageName) }
    }

    private fun appRow(app: LaunchableApp) = TextView(this).apply {
        text = app.label; textSize = 16f; gravity = Gravity.CENTER_VERTICAL; setTextColor(MainActivity.INK)
        setOnClickListener { packageManager.getLaunchIntentForPackage(app.packageName)?.let(::startActivity) }
        setOnLongClickListener { hiddenAppMenu(app); true }
    }

    private fun hiddenAppMenu(app: LaunchableApp) {
        val pinned = app.packageName in store.pinned()
        val distracting = app.packageName in store.distracting()
        val actions = arrayOf(
            if (pinned) "Unpin from home" else "Pin to home",
            if (distracting) "Unmark Distracting" else "Mark Distracting",
            "Unhide", "App info"
        )
        AlertDialog.Builder(this).setTitle(app.label).setItems(actions) { _, index ->
            when (actions[index]) {
                "Pin to home" -> { store.setPinned(store.pinned() + app.packageName); selected.remove(app.packageName) }
                "Unpin from home" -> store.setPinned(store.pinned() - app.packageName)
                "Mark Distracting" -> store.setDistracting(store.distracting() + app.packageName)
                "Unmark Distracting" -> store.setDistracting(store.distracting() - app.packageName)
                "Unhide" -> selected.remove(app.packageName)
                "App info" -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
            }
            if (actions[index] != "App info") { store.setHidden(selected); render() }
        }.show()
    }

    private fun action(label: String, click: () -> Unit) = TextView(this).apply {
        text = label; textSize = 10f; letterSpacing = .08f; gravity = Gravity.CENTER; setTextColor(MainActivity.ACCENT)
        setPadding(dp(15), 0, 0, 0); setOnClickListener { click() }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
