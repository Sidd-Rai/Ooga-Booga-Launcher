package com.siddrai.oogaboogalauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.animation.DecelerateInterpolator
import android.view.ViewConfiguration
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var store: AppStore
    private lateinit var root: FrameLayout
    private lateinit var home: View
    private lateinit var drawer: View
    private lateinit var drawerList: ListView
    private lateinit var drawerSearch: EditText
    private lateinit var homeScroll: ScrollView
    private lateinit var widgetHost: AppWidgetHost
    private var activeResizeFrame: ResizableWidgetFrame? = null
    private val widgetFrames = mutableListOf<ResizableWidgetFrame>()
    private val screenCache = mutableMapOf<Int, View>()
    private val screenFrameCache = mutableMapOf<Int, List<ResizableWidgetFrame>>()
    private val screenScrollCache = mutableMapOf<Int, ScrollView>()
    private var deleteTarget: TextView? = null
    private var deleteTargetHovered = false
    private var deleteTargetArmed = false
    private var screenLongPressAllowed = false
    private var suppressLauncherGesture = false
    private var gestureStartedOnWidget = false
    private val widgetManager by lazy { AppWidgetManager.getInstance(this) }
    private var drawerOpen = false
    private var currentScreen = 0
    private var drawerBackRegistered = false
    private var downX = 0f
    private var downY = 0f
    private var settingsThemeWasLight = false
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            store.invalidateApps()
            if (::root.isInitialized) buildPages(drawerOpen)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.prepare(this)
        super.onCreate(savedInstanceState)
        AppTheme.apply(this)
        store = AppStore(this)
        widgetHost = AppWidgetHost(this, WidgetSettingsActivity.HOST_ID)
        buildPages()
        registerDrawerBack()
        requestHomeRole()
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(packageReceiver, packageFilter)
    }

    override fun onStart() { super.onStart(); widgetHost.startListening() }
    override fun onStop() { widgetHost.stopListening(); super.onStop() }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        AppTheme.dismissMenu(this)
        if (::root.isInitialized) {
            drawer.animate().cancel(); home.animate().cancel()
            drawer.visibility = View.GONE; drawer.translationY = 0f; drawerOpen = false
            if (::drawerSearch.isInitialized) drawerSearch.text.clear()
            (home.parent as? ViewGroup)?.removeView(home)
            currentScreen = 0; home = screenView(0)
            (home.parent as? ViewGroup)?.removeView(home)
            home.animate().cancel(); home.visibility = View.VISIBLE; home.translationX = 0f; home.translationY = 0f
            root.addView(home, root.indexOfChild(drawer).coerceAtLeast(0))
            hideKeyboard()
        }
        overridePendingTransition(0, 0)
    }

    @SuppressLint("GestureBackNavigation")
    @Deprecated("Handled for the drawer")
    override fun onBackPressed() {
        if (drawerOpen) showHome()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (activeResizeFrame?.isEditing() == true) suppressLauncherGesture = true
            activeResizeFrame?.takeIf { it.isEditing() && !it.containsRaw(event.rawX, event.rawY) }?.finishResize()
            if (activeResizeFrame?.isEditing() != true) activeResizeFrame = null
            gestureStartedOnWidget = widgetFrames.any { it.containsRaw(event.rawX, event.rawY) }
        }
        if (activeResizeFrame?.isEditing() == true) suppressLauncherGesture = true
        if (suppressLauncherGesture) {
            val handled = super.dispatchTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) suppressLauncherGesture = false
            return handled
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; screenLongPressAllowed = true }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) > ViewConfiguration.get(this).scaledTouchSlop ||
                abs(event.y - downY) > ViewConfiguration.get(this).scaledTouchSlop) screenLongPressAllowed = false
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!gestureStartedOnWidget && !drawerOpen && abs(dx) > dp(65) && abs(dx) > abs(dy) * 1.25f) {
                    val target = when {
                        dx > 0 && currentScreen == 0 && store.leftScreen() -> -1
                        dx < 0 && currentScreen == -1 -> 0
                        dx < 0 && currentScreen == 0 && store.rightScreen() -> 1
                        dx > 0 && currentScreen == 1 -> 0
                        else -> currentScreen
                    }
                    if (target != currentScreen) { showScreen(target); return true }
                }
                if (!gestureStartedOnWidget && abs(dy) > dp(65) && abs(dy) > abs(dx) * 1.25f) {
                    if (dy < 0 && !drawerOpen && (!::homeScroll.isInitialized || !homeScroll.canScrollVertically(1) || downY > root.height * .82f)) {
                        showDrawer()
                        return true
                    } else if (dy > 0 && drawerOpen && ::drawerList.isInitialized && !drawerList.canScrollVertically(-1)) {
                        showHome()
                        return true
                    }
                }
                gestureStartedOnWidget = false
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun buildPages(keepDrawer: Boolean = false) {
        deleteTarget = null; deleteTargetHovered = false; activeResizeFrame = null
        screenCache.clear(); screenFrameCache.clear(); screenScrollCache.clear()
        root = FrameLayout(this).apply { setBackgroundColor(BG); clipChildren = false; clipToPadding = false }
        home = screenView(currentScreen)
        drawer = drawerView()
        root.addView(home)
        root.addView(drawer)
        setContentView(root)
        if (keepDrawer) {
            home.visibility = View.GONE
            drawer.visibility = View.VISIBLE
            drawerOpen = true
            registerDrawerBack()
        } else {
            drawer.visibility = View.GONE
            drawerOpen = false
        }
    }

    private fun showDrawer(animated: Boolean = true) {
        if (drawerOpen) return
        screenLongPressAllowed = false
        drawerOpen = true
        registerDrawerBack()
        drawer.visibility = View.VISIBLE
        if (!animated) { home.visibility = View.GONE; return }
        drawer.translationY = root.height.toFloat()
        drawer.animate().translationY(0f).setDuration(90).setInterpolator(DecelerateInterpolator())
            .withLayer().withEndAction { home.visibility = View.GONE }.start()
    }

    private fun showHome(animated: Boolean = true) {
        if (!drawerOpen) return
        drawerOpen = false
        home.visibility = View.VISIBLE
        if (::drawerSearch.isInitialized) drawerSearch.text.clear()
        hideKeyboard()
        if (!animated) { drawer.visibility = View.GONE; drawer.translationY = 0f; return }
        drawer.animate().translationY(root.height.toFloat()).setDuration(80).setInterpolator(DecelerateInterpolator()).withLayer().withEndAction {
            drawer.visibility = View.GONE
            drawer.translationY = 0f
        }.start()
    }

    private fun requestHomeRole() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME))
                startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 41)
        } else if (packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
            )?.activityInfo?.packageName != packageName) startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    private fun homeView(): View {
        val column = vertical(dp(24), dp(24), dp(24), dp(12))
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        if (store.showClock()) {
            content.addView(TextClock(this).apply {
                format12Hour = "h:mm"; format24Hour = "HH:mm"; textSize = 58f
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL); setTextColor(INK); letterSpacing = .03f
            })
            content.addView(TextView(this).apply {
                text = SimpleDateFormat("EEEE  •  dd MMMM", Locale.getDefault()).format(Date()).uppercase()
                textSize = 12f; letterSpacing = .12f; setTextColor(MUTED); setPadding(dp(3), 0, 0, dp(22))
            })
        }
        if (store.widgetIds(AppStore.MAIN_SCREEN).isNotEmpty()) content.addView(widgetCanvas(AppStore.MAIN_SCREEN))
        val apps = store.apps().filter { it.packageName in store.pinned() && it.packageName !in store.hidden() }
        if (apps.isEmpty()) content.addView(TextView(this).apply {
            text = "No pinned apps\nOpen settings to choose a few."
            textSize = 14f; setTextColor(MUTED); setPadding(dp(2), dp(22), 0, dp(22))
        }) else apps.forEach { app -> content.addView(appRow(app)) }
        homeScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isFillViewport = true; clipChildren = false; clipToPadding = false; addView(content) }
        column.addView(homeScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val left = store.apps().firstOrNull { it.packageName == store.quickLeft() }
        val right = store.apps().firstOrNull { it.packageName == store.quickRight() }
        if (left != null || right != null) column.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(quickButton(left, Gravity.LEFT), LinearLayout.LayoutParams(0, dp(52), 1f))
            addView(quickButton(right, Gravity.RIGHT), LinearLayout.LayoutParams(0, dp(52), 1f))
        })
        installScreenLongPress(column, content, AppStore.MAIN_SCREEN)
        return column
    }

    private fun widgetCanvas(screen: String): FrameLayout {
        val availableWidth = resources.displayMetrics.widthPixels - dp(48)
        val canvas = FrameLayout(this).apply { clipChildren = false; clipToPadding = false; setBackgroundColor(Color.TRANSPARENT) }
        var nextY = 0
        store.widgetIds(screen).forEach { id ->
            val info = widgetManager.getAppWidgetInfo(id) ?: return@forEach
            val defaultHeight = maxOf(info.minHeight, 96).coerceAtMost(400)
            val height = dp(store.widgetHeight(id, defaultHeight))
            val width = (availableWidth * store.widgetWidth(id) / 100f).toInt().coerceIn(dp(120), availableWidth)
            val initialX = store.widgetX(id)?.let(::dp)
                ?: (((availableWidth - width) / 2) + dp(store.widgetOffsetX(id))).coerceIn(0, availableWidth - width)
            val initialY = store.widgetY(id)?.let(::dp)
                ?: (nextY + dp(store.widgetOffsetY(id))).coerceAtLeast(0)
            val hostView = widgetHost.createView(this, id, info).apply { clipChildren = false; clipToPadding = false; setAppWidget(id, info) }
            lateinit var frame: ResizableWidgetFrame
            frame = ResizableWidgetFrame(
                this, availableWidth, resources.displayMetrics.heightPixels * 2,
                onEditStart = { selected ->
                    if (activeResizeFrame !== selected) activeResizeFrame?.finishResize()
                    activeResizeFrame = selected
                    showDeleteTarget()
                },
                onResize = { newWidth, newHeight, finished ->
                    if (finished) {
                        transferFrameTranslation(frame)
                        resolveWidgetCollision(frame, canvas, availableWidth)
                        persistWidgetFrame(id, frame, availableWidth)
                        updateWidgetOptions(id, newWidth, newHeight)
                    }
                    updateCanvasHeight(canvas, screen)
                },
                onMove = { _, _ ->
                    if (deleteTargetArmed && isOverDeleteTarget(frame)) {
                        widgetFeedback(frame, true)
                        hideDeleteTarget(); store.removeWidget(id); widgetHost.deleteAppWidgetId(id)
                        root.post { refreshHome() }
                    } else {
                        transferFrameTranslation(frame)
                        resolveWidgetCollision(frame, canvas, availableWidth)
                        persistWidgetFrame(id, frame, availableWidth)
                        updateCanvasHeight(canvas, screen)
                    }
                },
                onMoveProgress = { updateDeleteTargetFeedback(frame) },
                onEditEnd = { hideDeleteTarget() }
            )
            frame.clipChildren = false; frame.clipToPadding = false
            frame.addView(hostView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            canvas.addView(frame, FrameLayout.LayoutParams(width, height).apply { leftMargin = initialX; topMargin = initialY })
            resolveWidgetCollision(frame, canvas, availableWidth)
            updateWidgetOptions(id, width, height)
            widgetFrames.add(frame)
            nextY = maxOf(nextY, (frame.layoutParams as FrameLayout.LayoutParams).topMargin + height + dp(12))
        }
        updateCanvasHeight(canvas, screen)
        return canvas
    }

    private fun showDeleteTarget() {
        if (deleteTarget != null) return
        val target = TextView(this).apply {
            text = "×  REMOVE"; textSize = 12f; letterSpacing = .08f; gravity = Gravity.CENTER; setTextColor(INK)
            background = GradientDrawable().apply { setColor(BG); cornerRadius = dp(18).toFloat(); setStroke(dp(1), INK) }
        }
        deleteTargetHovered = false; deleteTargetArmed = false; deleteTarget = target
        root.addView(target, FrameLayout.LayoutParams(dp(132), dp(44), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(10) })
    }

    private fun updateDeleteTargetFeedback(frame: View) {
        val hovered = isOverDeleteTarget(frame)
        if (!hovered) deleteTargetArmed = true
        if (hovered != deleteTargetHovered) {
            deleteTargetHovered = hovered
            deleteTarget?.animate()?.scaleX(if (hovered) 1.1f else 1f)?.scaleY(if (hovered) 1.1f else 1f)?.setDuration(70)?.start()
            if (hovered && deleteTargetArmed) widgetFeedback(frame, false)
        }
    }

    private fun hideDeleteTarget() {
        deleteTarget?.animate()?.cancel(); deleteTarget?.let { root.removeView(it) }; deleteTarget = null
        deleteTargetHovered = false; deleteTargetArmed = false
    }


    private fun widgetFeedback(view: View, deleting: Boolean) {
        store.performWidgetHaptic(deleting)
        view.performHapticFeedback(
            if (deleting) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }

    private fun isOverDeleteTarget(frame: View): Boolean {
        val target = deleteTarget ?: return false
        val frameRect = Rect(); val targetRect = Rect()
        return frame.getGlobalVisibleRect(frameRect) && target.getGlobalVisibleRect(targetRect) &&
            targetRect.contains(frameRect.centerX(), frameRect.top + minOf(dp(22), frameRect.height() / 2))
    }

    private fun transferFrameTranslation(frame: ResizableWidgetFrame) {
        val params = frame.layoutParams as FrameLayout.LayoutParams
        params.leftMargin += frame.translationX.toInt(); params.topMargin += frame.translationY.toInt()
        frame.translationX = 0f; frame.translationY = 0f; frame.layoutParams = params
    }

    private fun resolveWidgetCollision(frame: View, canvas: FrameLayout, availableWidth: Int) {
        val params = frame.layoutParams as FrameLayout.LayoutParams
        val grid = dp(8)
        params.leftMargin = ((params.leftMargin.coerceIn(0, availableWidth - params.width) + grid / 2) / grid) * grid
        params.topMargin = ((params.topMargin.coerceAtLeast(0) + grid / 2) / grid) * grid
        var attempts = 0
        while (attempts++ < canvas.childCount + 2) {
            val candidate = Rect(params.leftMargin, params.topMargin, params.leftMargin + params.width, params.topMargin + params.height)
            val collision = (0 until canvas.childCount).map { canvas.getChildAt(it) }.firstOrNull { other ->
                if (other === frame) false else {
                    val otherParams = other.layoutParams as FrameLayout.LayoutParams
                    Rect.intersects(candidate, Rect(otherParams.leftMargin, otherParams.topMargin,
                        otherParams.leftMargin + otherParams.width, otherParams.topMargin + otherParams.height))
                }
            } ?: break
            val otherParams = collision.layoutParams as FrameLayout.LayoutParams
            params.topMargin = otherParams.topMargin + otherParams.height + dp(8)
        }
        frame.layoutParams = params
    }

    private fun persistWidgetFrame(id: Int, frame: View, availableWidth: Int) {
        val params = frame.layoutParams as FrameLayout.LayoutParams
        store.setWidgetPosition(id, (params.leftMargin / resources.displayMetrics.density).toInt(),
            (params.topMargin / resources.displayMetrics.density).toInt())
        store.setWidgetSize(id, (params.width * 100f / availableWidth).toInt().coerceIn(30, 100),
            (params.height / resources.displayMetrics.density).toInt())
    }

    private fun updateCanvasHeight(canvas: FrameLayout, screen: String) {
        val contentHeight = (0 until canvas.childCount).maxOfOrNull {
            val child = canvas.getChildAt(it); val params = child.layoutParams as FrameLayout.LayoutParams
            params.topMargin + params.height
        } ?: 0
        val minimum = if (screen == AppStore.MAIN_SCREEN) 0 else resources.displayMetrics.heightPixels - dp(96)
        canvas.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(contentHeight + dp(12), minimum))
    }

    private fun updateWidgetOptions(id: Int, width: Int, height: Int) {
        val widthDp = (width / resources.displayMetrics.density).toInt()
        val heightDp = (height / resources.displayMetrics.density).toInt()
        widgetManager.updateAppWidgetOptions(id, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        })
    }

    private fun widgetScreen(screen: String): View {
        val column = vertical(dp(24), dp(24), dp(24), dp(12))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            minimumHeight = resources.displayMetrics.heightPixels - dp(120)
        }
        if (store.widgetIds(screen).isNotEmpty()) content.addView(widgetCanvas(screen))
        if (store.widgetIds(screen).isEmpty()) content.addView(TextView(this).apply {
            text = "No widgets on this screen"; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(MUTED); setPadding(0, dp(80), 0, dp(40))
        })
        homeScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isFillViewport = true; clipChildren = false; clipToPadding = false; addView(content) }
        column.addView(homeScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        installScreenLongPress(column, content, screen)
        return column
    }

    private fun installScreenLongPress(column: View, content: View, screen: String) {
        val listener = View.OnLongClickListener {
            if (!drawerOpen && screenLongPressAllowed) { showScreenMenu(screen); true } else false
        }
        listOf(column, content, homeScroll).forEach { view ->
            view.setOnLongClickListener(listener); view.isLongClickable = true
        }
    }

    private fun showScreenMenu(screen: String) {
        AppTheme.showMenu(this, null, arrayOf("Widgets", "Appearance")) { index ->
            if (index == 0) startActivityForResult(Intent(this, WidgetSettingsActivity::class.java)
                .putExtra("screen", screen).putExtra(WidgetSettingsActivity.EXTRA_BROWSE, true), 73)
            else startActivityForResult(Intent(this, ThemeEditorActivity::class.java), 72)
        }
    }

    private fun screenView(position: Int): View {
        screenCache[position]?.let { cached ->
            cached.animate().cancel(); cached.visibility = View.VISIBLE
            cached.alpha = 1f; cached.translationX = 0f; cached.translationY = 0f
            widgetFrames.clear(); widgetFrames.addAll(screenFrameCache[position].orEmpty())
            screenScrollCache[position]?.let { homeScroll = it }
            return cached
        }
        widgetFrames.clear()
        val created = when (position) {
            -1 -> widgetScreen(AppStore.LEFT_WIDGET_SCREEN)
            1 -> widgetScreen(AppStore.RIGHT_WIDGET_SCREEN)
            else -> homeView()
        }
        screenCache[position] = created
        screenFrameCache[position] = widgetFrames.toList()
        if (::homeScroll.isInitialized) screenScrollCache[position] = homeScroll
        return created
    }

    private fun invalidateScreen(position: Int) {
        screenCache.remove(position); screenFrameCache.remove(position); screenScrollCache.remove(position)
    }

    private fun showScreen(target: Int) {
        val previous = currentScreen
        val old = home
        currentScreen = target
        val next = screenView(target)
        next.animate().cancel(); old.animate().cancel()
        (next.parent as? ViewGroup)?.removeView(next)
        val direction = if (target > previous) 1f else -1f
        next.translationX = root.width * direction
        root.addView(next, root.indexOfChild(drawer))
        home = next
        next.animate().translationX(0f).setDuration(65).withLayer().start()
        old.animate().translationX(-root.width * direction).setDuration(65).withLayer().withEndAction { root.removeView(old) }.start()
    }

    private fun quickButton(app: LaunchableApp?, alignment: Int) = TextView(this).apply {
        text = app?.label?.uppercase().orEmpty(); textSize = 11f; letterSpacing = .08f
        gravity = alignment or Gravity.CENTER_VERTICAL; setTextColor(ACCENT)
        if (app != null) setOnClickListener { open(app) }
    }

    private fun drawerView(): View {
        val column = vertical(dp(20), dp(36), dp(20), 0).apply { setBackgroundColor(BG) }
        drawerSearch = EditText(this).apply {
            hint = "SEARCH APPLICATIONS"; setHintTextColor(MUTED); setTextColor(INK); textSize = 14f
            isSingleLine = true; background = softField(); setPadding(dp(16), 0, dp(16), 0)
        }
        column.addView(drawerSearch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            bottomMargin = dp(4)
        })
        var available = store.apps().filterNot { it.packageName in store.hidden() }
        val adapter = AppAdapter(available.toMutableList())
        drawerList = ListView(this).apply {
            this.adapter = adapter; divider = null; isVerticalScrollBarEnabled = false
            setFriction(ViewConfiguration.getScrollFriction() * .48f)
            setVelocityScale(1.75f)
            setOnItemClickListener { _, _, position, _ -> open(adapter.getItem(position)) }
            setOnItemLongClickListener { _, _, position, _ ->
                val app = adapter.getItem(position)
                showAppMenu(app) {
                    available = store.apps().filterNot { it.packageName in store.hidden() }
                    adapter.replace(available.filter { it.label.contains(drawerSearch.text.toString(), ignoreCase = true) })
                }
                true
            }
        }
        drawerSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.replace(available.filter { it.label.contains(s?.toString().orEmpty(), ignoreCase = true) })
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        val body = FrameLayout(this)
        body.addView(drawerList, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.RIGHT
            addView(textButton("APP SETTINGS") { openSettings() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))
            addView(textButton("SYSTEM SETTINGS") { startActivity(Intent(Settings.ACTION_SETTINGS)) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))
        }, FrameLayout.LayoutParams(dp(128), dp(68), Gravity.RIGHT or Gravity.TOP))
        column.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return column
    }

    private inner class AppAdapter(private val items: MutableList<LaunchableApp>) : BaseAdapter() {
        fun replace(next: List<LaunchableApp>) { items.clear(); items.addAll(next); notifyDataSetChanged() }
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = getItem(position).packageName.hashCode().toLong()
        override fun getView(position: Int, recycled: View?, parent: ViewGroup?): View {
            val row = (recycled as? TextView) ?: TextView(this@MainActivity).apply {
                textSize = 16f; gravity = Gravity.CENTER_VERTICAL; setTextColor(INK)
                setPadding(dp(6), 0, dp(4), 0)
            }
            row.text = getItem(position).label
            row.setPadding(dp(6), 0, if (position < 2) dp(132) else dp(4), 0)
            row.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
            return row
        }
    }

    private fun appRow(app: LaunchableApp) = TextView(this).apply {
        text = app.label; textSize = 16f; gravity = Gravity.CENTER_VERTICAL
        setTextColor(INK); setPadding(dp(6), 0, dp(4), 0); setOnClickListener { open(app) }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
    }

    private fun open(app: LaunchableApp) {
        if (app.packageName !in store.distracting()) return launch(app.packageName)
        if (store.hasUsableSession(app.packageName)) {
            store.resumeSession()
            return launch(app.packageName)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(22), dp(24), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.argb(226, Color.red(BG), Color.green(BG), Color.blue(BG)))
                cornerRadius = dp(26).toFloat(); setStroke(dp(1), LINE)
            }
        }
        box.addView(TextView(this).apply {
            text = app.label; textSize = 23f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); setTextColor(INK)
        })
        box.addView(TextView(this).apply {
            text = "Choose a time limit"; textSize = 13f; setTextColor(MUTED); setPadding(0, dp(4), 0, dp(18))
        })
        val choices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lateinit var dialog: AlertDialog
        listOf(1, 5, 10, 15).forEach { minutes -> choices.addView(TextView(this).apply {
            text = "$minutes\nMIN"; textSize = 13f; gravity = Gravity.CENTER; setTextColor(INK)
            setPadding(dp(4), dp(12), dp(4), dp(12)); background = GradientDrawable().apply {
                setColor(Color.argb(70, Color.red(INK), Color.green(INK), Color.blue(INK))); cornerRadius = dp(16).toFloat()
            }; setOnClickListener {
                store.beginSession(app.packageName, minutes); dialog.dismiss(); launch(app.packageName)
            }
        }, LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginStart = dp(3); marginEnd = dp(3) }) }
        box.addView(choices)
        box.addView(TextView(this).apply {
            text = "CUSTOM TIME"; textSize = 12f; letterSpacing = .08f; gravity = Gravity.CENTER; setTextColor(ACCENT)
            setPadding(0, dp(18), 0, dp(14)); setOnClickListener { dialog.dismiss(); promptCustomTime(app) }
        })
        box.addView(TextView(this).apply {
            text = "CANCEL"; textSize = 11f; letterSpacing = .08f; gravity = Gravity.CENTER; setTextColor(MUTED)
            setPadding(0, dp(8), 0, dp(8)); setOnClickListener { dialog.dismiss() }
        })
        dialog = AlertDialog.Builder(this).setView(box).create()
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply {
                dimAmount = .48f
                if (android.os.Build.VERSION.SDK_INT >= 31) blurBehindRadius = 48
            }
            if (android.os.Build.VERSION.SDK_INT >= 31) addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
    }
    private fun promptCustomTime(app: LaunchableApp) {
        val input = EditText(this).apply {
            hint = "Minutes"; inputType = InputType.TYPE_CLASS_NUMBER; setSelectAllOnFocus(true)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this).setTitle("CUSTOM TIMER").setView(input)
            .setPositiveButton("START", null).setNegativeButton("CANCEL", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val minutes = input.text.toString().toIntOrNull()
                if (minutes != null && minutes > 0) {
                    store.beginSession(app.packageName, minutes); dialog.dismiss(); launch(app.packageName)
                } else input.error = "Enter at least 1 minute"
            }
        }
        dialog.show()
    }
    private fun showAppMenu(app: LaunchableApp, refresh: () -> Unit) {
        val favourite = app.packageName in store.pinned()
        val distracting = app.packageName in store.distracting()
        val actions = arrayOf(
            if (favourite) "Unpin from home" else "Pin to home",
            if (distracting) "Unmark Distracting" else "Mark Distracting",
            "Hide", "App info"
        )
        AppTheme.showMenu(this, app.label, actions) { index ->
            when (actions[index]) {
                "Pin to home" -> { store.setPinned(store.pinned() + app.packageName); store.setHidden(store.hidden() - app.packageName) }
                "Unpin from home" -> store.setPinned(store.pinned() - app.packageName)
                "Mark Distracting" -> store.setDistracting(store.distracting() + app.packageName)
                "Unmark Distracting" -> store.setDistracting(store.distracting() - app.packageName)
                "Hide" -> { store.setHidden(store.hidden() + app.packageName); store.setPinned(store.pinned() - app.packageName) }
                "App info" -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
            }
            if (actions[index] != "App info") { refresh(); refreshHome() }
        }
    }
    private fun refreshHome() {
        if (!::root.isInitialized) return
        invalidateScreen(currentScreen)
        root.removeView(home)
        home = screenView(currentScreen).apply { visibility = if (drawerOpen) View.GONE else View.VISIBLE }
        root.addView(home, 0)
    }
    private fun launch(pkg: String) = packageManager.getLaunchIntentForPackage(pkg)?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it)
    } ?: Unit
    private fun openSettings() {
        settingsThemeWasLight = AppTheme.current.light
        startActivityForResult(Intent(this, SettingsActivity::class.java), 55)
    }
    @Deprecated("Legacy result API keeps this dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 72 && resultCode == RESULT_OK) recreate()
        if (requestCode == 73 && resultCode == RESULT_OK) refreshHome()
        if (requestCode == 55) {
            val newTheme = AppTheme.load(this)
            if (newTheme.light != settingsThemeWasLight) recreate()
            else { AppTheme.apply(this); buildPages(false) }
        }
    }
    private fun hideKeyboard() = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
        .hideSoftInputFromWindow(root.windowToken, 0)
    private fun vertical(l: Int, t: Int, r: Int, b: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(l, t, r, b)
    }
    private fun textButton(label: String, click: () -> Unit) = TextView(this).apply {
        text = label; textSize = 11f; letterSpacing = .08f; gravity = Gravity.CENTER; setTextColor(ACCENT)
        setPadding(dp(14), 0, dp(4), 0); setOnClickListener { click() }
    }
    private fun softField() = GradientDrawable().apply { setColor(LINE); cornerRadius = dp(16).toFloat() }
    private val drawerBackCallback by lazy { OnBackInvokedCallback { showHome() } }
    private fun registerDrawerBack() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !drawerBackRegistered) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, drawerBackCallback)
            drawerBackRegistered = true
        }
    }
    private fun unregisterDrawerBack() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && drawerBackRegistered) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(drawerBackCallback)
            drawerBackRegistered = false
        }
    }
    override fun onDestroy() { unregisterDrawerBack(); unregisterReceiver(packageReceiver); super.onDestroy() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        val BG get() = AppTheme.current.background
        val INK get() = AppTheme.current.foreground
        val MUTED get() = AppTheme.current.muted
        val ACCENT get() = AppTheme.current.accent
        val LINE get() = AppTheme.current.line
    }
}
