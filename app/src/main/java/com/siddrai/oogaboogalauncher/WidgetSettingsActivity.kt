package com.siddrai.oogaboogalauncher

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class WidgetSettingsActivity : Activity() {
    private lateinit var store: AppStore
    private lateinit var host: AppWidgetHost
    private val manager by lazy { AppWidgetManager.getInstance(this) }
    private var pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingNew = false
    private var choosing = false
    private var directBrowse = false
    private lateinit var screen: String
    private val previewExecutor = Executors.newFixedThreadPool(2)

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.prepare(this); super.onCreate(savedInstanceState); AppTheme.apply(this)
        store = AppStore(this); host = AppWidgetHost(this, HOST_ID)
        screen = intent.getStringExtra("screen") ?: AppStore.MAIN_SCREEN
        directBrowse = intent.getBooleanExtra(EXTRA_BROWSE, false)
        pendingId = savedInstanceState?.getInt("pending_id", AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        pendingNew = savedInstanceState?.getBoolean("pending_new", false) ?: false
        choosing = savedInstanceState?.getBoolean("choosing", directBrowse) ?: directBrowse
        if (choosing) renderGallery() else renderManage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("pending_id", pendingId); outState.putBoolean("pending_new", pendingNew)
        outState.putBoolean("choosing", choosing); super.onSaveInstanceState(outState)
    }

    @Deprecated("Handles the in-app gallery")
    override fun onBackPressed() {
        if (choosing && !directBrowse) renderManage() else super.onBackPressed()
    }

    private fun renderManage() {
        choosing = false
        val root = base()
        root.addView(title(when (screen) {
            AppStore.LEFT_WIDGET_SCREEN -> "LEFT SCREEN WIDGETS"
            AppStore.RIGHT_WIDGET_SCREEN -> "RIGHT SCREEN WIDGETS"
            else -> "HOME WIDGETS"
        }))
        root.addView(Button(this).apply { text = "Browse widgets"; isAllCaps = false; setOnClickListener { renderGallery() } })
        store.widgetIds(screen).forEach { id ->
            val info = manager.getAppWidgetInfo(id)
            root.addView(TextView(this).apply {
                text = info?.let(::widgetLabel) ?: "Unavailable widget"; textSize = 16f; setTextColor(MainActivity.INK)
                gravity = Gravity.CENTER_VERTICAL; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58))
                setOnClickListener { widgetMenu(id, info) }
                setOnLongClickListener {
                    widgetMenu(id, info); true
                }
            })
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; setBackgroundColor(MainActivity.BG); addView(root) })
    }

    private fun renderGallery() {
        choosing = true
        val providers = manager.installedProviders.sortedBy { appLabel(it).lowercase() }
        val root = base()
        root.addView(title("CHOOSE A WIDGET"))
        val search = EditText(this).apply {
            hint = "SEARCH BY APP OR WIDGET"; setHintTextColor(MainActivity.MUTED); setTextColor(MainActivity.INK)
            textSize = 14f; isSingleLine = true; setPadding(dp(12), 0, dp(12), 0)
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(12) })
        val adapter = WidgetGalleryAdapter(providers.toMutableList())
        val grid = GridView(this).apply {
            numColumns = 2; horizontalSpacing = dp(12); verticalSpacing = dp(16); stretchMode = GridView.STRETCH_COLUMN_WIDTH
            this.adapter = adapter
            setOnItemClickListener { _, _, position, _ -> bind(adapter.getItem(position)) }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                adapter.replace(providers.filter { appLabel(it).contains(query, true) || widgetLabel(it).contains(query, true) })
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun bind(info: AppWidgetProviderInfo) {
        pendingId = host.allocateAppWidgetId()
        pendingNew = true
        if (manager.bindAppWidgetIdIfAllowed(pendingId, info.provider)) configureOrSave(pendingId)
        else startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        }, BIND)
    }

    private fun configureOrSave(id: Int) {
        val configure = manager.getAppWidgetInfo(id)?.configure
        if (configure != null) startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = configure; putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }, CONFIGURE) else saveWidget(id)
    }

    @Deprecated("Android widget binding uses activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId) ?: pendingId
        if (resultCode != RESULT_OK || id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            if (pendingNew && pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) host.deleteAppWidgetId(pendingId)
            pendingId = AppWidgetManager.INVALID_APPWIDGET_ID; return
        }
        if (requestCode == BIND) configureOrSave(id)
        else if (requestCode == CONFIGURE) saveWidget(id)
        else if (requestCode == EDIT) { pendingId = AppWidgetManager.INVALID_APPWIDGET_ID; renderManage() }
    }

    private fun saveWidget(id: Int) {
        store.addWidget(id, screen); pendingId = AppWidgetManager.INVALID_APPWIDGET_ID; pendingNew = false
        if (directBrowse) { setResult(RESULT_OK); finish() } else renderManage()
    }

    private fun widgetMenu(id: Int, info: AppWidgetProviderInfo?) {
        val actions = if (info?.configure != null) arrayOf("Configure", "Remove") else arrayOf("Remove")
        AlertDialog.Builder(this).setTitle(info?.let(::widgetLabel) ?: "Widget").setItems(actions) { _, which ->
            if (actions[which] == "Configure") {
                pendingId = id; pendingNew = false
                startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = info?.configure; putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                }, EDIT)
            } else {
                store.removeWidget(id); host.deleteAppWidgetId(id); renderManage()
            }
        }.show()
    }

    private data class ViewHolder(val image: ImageView, val label: TextView)

    private inner class WidgetGalleryAdapter(private val providers: MutableList<AppWidgetProviderInfo>) : BaseAdapter() {
        fun replace(next: List<AppWidgetProviderInfo>) { providers.clear(); providers.addAll(next); notifyDataSetChanged() }
        override fun getCount() = providers.size
        override fun getItem(position: Int) = providers[position]
        override fun getItemId(position: Int) = providers[position].provider.hashCode().toLong()
        override fun getView(position: Int, recycled: View?, parent: ViewGroup?): View {
            val row = recycled as? LinearLayout ?: LinearLayout(this@WidgetSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(6), dp(8), dp(6), dp(8))
                val image = ImageView(this@WidgetSettingsActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
                val label = TextView(this@WidgetSettingsActivity).apply {
                    gravity = Gravity.CENTER
                    textSize = 12f
                    maxLines = 2
                    setTextColor(MainActivity.INK)
                }
                addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120)))
                addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
                tag = ViewHolder(image, label)
            }
            val holder = row.tag as ViewHolder
            val info = getItem(position)
            val key = info.provider.flattenToString()
            holder.image.tag = key
            holder.image.setImageDrawable(null)
            try {
                previewExecutor.execute {
                    val drawable = try {
                        info.loadPreviewImage(this@WidgetSettingsActivity, resources.displayMetrics.densityDpi)
                            ?: info.loadIcon(this@WidgetSettingsActivity, resources.displayMetrics.densityDpi)
                    } catch (_: Exception) {
                        null
                    }
                    holder.image.post {
                        if (!isFinishing && !isDestroyed && holder.image.tag == key) {
                            holder.image.setImageDrawable(drawable)
                        }
                    }
                }
            } catch (_: RejectedExecutionException) {
                // The activity is closing; the recycled row no longer needs a preview.
            }
            holder.label.text = "${appLabel(info)}\n${widgetLabel(info)}"
            return row
        }
    }

    private fun appLabel(info: AppWidgetProviderInfo): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(info.provider.packageName, 0)).toString()
    }.getOrElse { info.provider.packageName }

    private fun widgetLabel(info: AppWidgetProviderInfo): String = runCatching {
        info.loadLabel(packageManager).toString()
    }.getOrElse { appLabel(info) }

    private fun base() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(40), dp(20), dp(18)); setBackgroundColor(MainActivity.BG)
    }
    private fun title(value: String) = TextView(this).apply {
        text = value; textSize = 27f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        setTextColor(MainActivity.INK); setPadding(dp(4), 0, 0, dp(20))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroy() { previewExecutor.shutdownNow(); super.onDestroy() }

    companion object {
        const val HOST_ID = 7401
        const val EXTRA_BROWSE = "browse"
        private const val BIND = 81; private const val CONFIGURE = 82; private const val EDIT = 83
    }
}
