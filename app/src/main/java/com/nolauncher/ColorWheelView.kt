package com.nolauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

class ColorWheelView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    private var wheel: Bitmap? = null
    private var hue = 0f
    private var saturation = 0f
    var onColourChanged: ((Float, Float) -> Unit)? = null

    fun setColour(colour: Int) {
        val hsv = FloatArray(3); Color.colorToHSV(colour, hsv)
        hue = hsv[0]; saturation = hsv[1]; invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val side = min(w, h)
        if (side <= 0) return
        val pixels = IntArray(side * side)
        val radius = side / 2f
        for (y in 0 until side) for (x in 0 until side) {
            val dx = x - radius; val dy = y - radius
            val distance = sqrt(dx * dx + dy * dy)
            if (distance <= radius) {
                val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0
                pixels[y * side + x] = Color.HSVToColor(floatArrayOf(angle.toFloat(), distance / radius, 1f))
            }
        }
        wheel = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = wheel ?: return
        val left = (width - bitmap.width) / 2f; val top = (height - bitmap.height) / 2f
        canvas.drawBitmap(bitmap, left, top, null)
        val radius = bitmap.width / 2f
        val angle = Math.toRadians(hue.toDouble())
        val x = width / 2f + (Math.cos(angle) * radius * saturation).toFloat()
        val y = height / 2f + (Math.sin(angle) * radius * saturation).toFloat()
        marker.color = Color.BLACK; marker.strokeWidth = 7f; canvas.drawCircle(x, y, 10f, marker)
        marker.color = Color.WHITE; marker.strokeWidth = 3f; canvas.drawCircle(x, y, 10f, marker)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN && event.actionMasked != MotionEvent.ACTION_MOVE) return true
        val radius = min(width, height) / 2f
        val dx = event.x - width / 2f; val dy = event.y - height / 2f
        val distance = sqrt(dx * dx + dy * dy)
        saturation = (distance / radius).coerceIn(0f, 1f)
        hue = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat()
        onColourChanged?.invoke(hue, saturation); invalidate(); return true
    }
}
