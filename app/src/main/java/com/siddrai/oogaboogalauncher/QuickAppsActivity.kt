package com.siddrai.oogaboogalauncher

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class QuickAppsActivity : Activity() {
    private lateinit var store: AppStore
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.prepare(this); super.onCreate(savedInstanceState); AppTheme.apply(this)
        store = AppStore(this); render()
    }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(40), dp(24), dp(20)); setBackgroundColor(MainActivity.BG)
        }
        root.addView(TextView(this).apply { text = "BOTTOM SHORTCUTS"; textSize = 27f; setTextColor(MainActivity.INK); setPadding(0, 0, 0, dp(26)) })
        root.addView(slot("LEFT", store.quickLeft()) { store.setQuickLeft(it) })
        root.addView(slot("RIGHT", store.quickRight()) { store.setQuickRight(it) })
        setContentView(root)
    }

    private fun slot(side: String, current: String?, save: (String?) -> Unit) = TextView(this).apply {
        val app = store.apps().firstOrNull { it.packageName == current }
        text = "$side\n${app?.label ?: "Disabled"}"; textSize = 16f; setTextColor(MainActivity.INK)
        gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72))
        setOnClickListener { chooseApp(side, save) }
    }

    private fun chooseApp(side: String, save: (String?) -> Unit) {
        val all = store.apps()
        var filtered: List<LaunchableApp?> = listOf(null) + all
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, dp(18), 0) }
        val search = EditText(this).apply { hint = "Search apps"; isSingleLine = true }
        val list = ListView(this)
        fun update() {
            val labels = filtered.map { it?.label ?: "Disabled" }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                filtered = (if (query.isBlank()) listOf(null) else emptyList()) + all.filter { it.label.contains(query, true) }
                update()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        box.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        box.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360)))
        update()
        val dialog = AlertDialog.Builder(this).setTitle("$side SHORTCUT").setView(box).setNegativeButton("CANCEL", null).create()
        list.setOnItemClickListener { _, _, position, _ -> save(filtered[position]?.packageName); dialog.dismiss(); render() }
        dialog.show()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
