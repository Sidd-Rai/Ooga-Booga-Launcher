package com.siddrai.oogaboogalauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray

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
        root.addView(option("APP PERMISSIONS") { startActivity(Intent(this, PermissionsActivity::class.java)) })
        root.addView(option("APPEARANCE") { startActivityForResult(Intent(this, ThemeEditorActivity::class.java), 71) })
        root.addView(option("PRIVACY POLICY") { startActivity(Intent(this, PrivacyPolicyActivity::class.java)) })
        root.addView(option("CHECK FOR UPDATES") { checkForUpdates() })
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
    private fun checkForUpdates() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        val url = Uri.parse("https://sidd-rai.github.io/Ooga-Booga-Launcher/").buildUpon()
            .appendQueryParameter("version", version).build()
        AlertDialog.Builder(this)
            .setTitle("Check for Updates")
            .setMessage(randomUpdateJoke())
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("OPEN UPDATE PAGE") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, url)) }
            .show()
    }

    private fun randomUpdateJoke(): String = runCatching {
        val text = resources.openRawResource(R.raw.update_jokes).bufferedReader().use { it.readText() }
        val jokes = JSONArray(text)
        jokes.getString(kotlin.random.Random.nextInt(jokes.length()))
    }.getOrDefault("The launcher can't check for updates. Your browser can.")

    private fun select(mode: String) = startActivity(Intent(this, AppSelectionActivity::class.java).putExtra("mode", mode))
    @Deprecated("Legacy result API keeps this dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 71 && resultCode == RESULT_OK) recreate()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
