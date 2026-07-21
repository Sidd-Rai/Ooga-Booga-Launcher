package com.siddrai.oogaboogalauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

class ResizableWidgetFrame(
    context: Context,
    private val maximumWidth: Int,
    private val maximumHeight: Int,
    private val onEditStart: (ResizableWidgetFrame) -> Unit,
    private val onResize: (width: Int, height: Int, finished: Boolean) -> Unit,
    private val onMove: (offsetX: Float, offsetY: Float) -> Unit,
    private val onMoveProgress: () -> Unit,
    private val onEditEnd: () -> Unit
) : FrameLayout(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handle = dp(7).toFloat(); private val hit = dp(18).toFloat()
    private val handler = Handler(Looper.getMainLooper()); private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var editing = false; private var moving = false
    private var left = false; private var right = false; private var top = false; private var bottom = false
    private var startX = 0f; private var startY = 0f; private var startWidth = 0; private var startHeight = 0
    private var startTranslationX = 0f; private var startTranslationY = 0f
    private var touchX = 0f; private var touchY = 0f
    private var rawTouchX = 0f; private var rawTouchY = 0f
    private val longPress = Runnable { beginResize(); performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }

    init { setWillNotDraw(false) }
    fun isEditing() = editing
    fun containsRaw(x: Float, y: Float): Boolean {
        val location = IntArray(2); getLocationOnScreen(location)
        return x >= location[0] && x <= location[0] + width && y >= location[1] && y <= location[1] + height
    }
    fun beginResize() {
        editing = true; moving = true; startX = rawTouchX; startY = rawTouchY; startWidth = width; startHeight = height
        startTranslationX = translationX; startTranslationY = translationY
        onEditStart(this); parent?.requestDisallowInterceptTouchEvent(true); invalidate()
    }
    fun finishResize() {
        if (!editing) return
        editing = false; moving = false
        parent?.requestDisallowInterceptTouchEvent(false); onResize(width, height, true); onEditEnd(); invalidate()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        rawTouchX = event.rawX; rawTouchY = event.rawY
        if (!editing) when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { touchX = event.x; touchY = event.y; handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong()) }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - touchX) > slop || abs(event.y - touchY) > slop) handler.removeCallbacks(longPress)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent) = editing

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editing) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {                left = event.x <= hit; right = event.x >= width - hit; top = event.y <= hit; bottom = event.y >= height - hit
                moving = !left && !right && !top && !bottom
                if (moving) onEditStart(this)
                startX = event.rawX; startY = event.rawY; startWidth = width; startHeight = height
                startTranslationX = translationX; startTranslationY = translationY
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startX; val dy = event.rawY - startY
                if (moving) {
                    translationX = startTranslationX + dx
                    translationY = startTranslationY + dy
                    onMoveProgress()
                } else {
                    val nextWidth = when { right -> startWidth + dx.toInt(); left -> startWidth - dx.toInt(); else -> startWidth }
                        .coerceIn(dp(120), maximumWidth)
                    val nextHeight = when { bottom -> startHeight + dy.toInt(); top -> startHeight - dy.toInt(); else -> startHeight }
                        .coerceIn(dp(80), maximumHeight)
                    if (left) translationX = startTranslationX + (startWidth - nextWidth)
                    if (top) translationY = startTranslationY + (startHeight - nextHeight)
                    layoutParams = layoutParams.apply { width = nextWidth; height = nextHeight }
                    onResize(nextWidth, nextHeight, false)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (moving) onMove(translationX, translationY)
                else onResize(width, height, true)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); if (!editing) return
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2).toFloat(); paint.color = AppTheme.current.foreground
        canvas.drawRect(1f, 1f, width - 1f, height - 1f, paint)
        val points = listOf(width / 2f to 0f, width / 2f to height.toFloat(), 0f to height / 2f, width.toFloat() to height / 2f)
        paint.style = Paint.Style.FILL; paint.color = AppTheme.current.background
        points.forEach { canvas.drawCircle(it.first, it.second, handle + dp(2), paint) }
        paint.color = AppTheme.current.foreground; points.forEach { canvas.drawCircle(it.first, it.second, handle, paint) }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
