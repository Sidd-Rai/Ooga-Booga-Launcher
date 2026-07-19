package com.nolauncher

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class ThemeEditorActivity : Activity() {
    private lateinit var backgroundInput: EditText
    private lateinit var foregroundInput: EditText
    private lateinit var preview: TextView
    private lateinit var mode: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.prepare(this)
        super.onCreate(savedInstanceState)
        AppTheme.apply(this)
        val palette = AppTheme.current
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(28), dp(24), dp(20)); setBackgroundColor(palette.background)
        }
        root.addView(TextView(this).apply {
            text = "APPEARANCE"; textSize = 28f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); setTextColor(palette.foreground)
        })
        root.addView(TextView(this).apply {
            text = "BUILD A QUIET, PERSONAL PALETTE"; textSize = 10f; letterSpacing = .12f
            setTextColor(palette.muted); setPadding(0, dp(6), 0, dp(24))
        })
        mode = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(RadioButton(this@ThemeEditorActivity).apply { id = 1001; text = "Dark"; setTextColor(palette.foreground); isChecked = !palette.light })
            addView(RadioButton(this@ThemeEditorActivity).apply { id = 1002; text = "Light"; setTextColor(palette.foreground); isChecked = palette.light })
        }
        root.addView(mode)
        root.addView(fieldLabel("BACKGROUND", palette))
        backgroundInput = colourField(palette.background, palette)
        root.addView(colourRow(backgroundInput))
        root.addView(fieldLabel("FOREGROUND", palette))
        foregroundInput = colourField(palette.foreground, palette)
        root.addView(colourRow(foregroundInput))
        preview = TextView(this).apply {
            text = "12:48\nYour space, your pace."
            textSize = 22f; gravity = Gravity.CENTER; setPadding(dp(16), dp(28), dp(16), dp(28))
        }
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(24); bottomMargin = dp(20)
        })
        root.addView(Button(this).apply {
            text = "Save theme"; isAllCaps = false
            setOnClickListener {
                val bg = parse(backgroundInput.text.toString()) ?: return@setOnClickListener toast("Invalid background colour")
                val fg = parse(foregroundInput.text.toString()) ?: return@setOnClickListener toast("Invalid foreground colour")
                AppTheme.save(this@ThemeEditorActivity, mode.checkedRadioButtonId == 1002, bg, fg)
                setResult(RESULT_OK)
                finish()
            }
        })
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updatePreview()
            override fun afterTextChanged(s: Editable?) = Unit
        }
        backgroundInput.addTextChangedListener(watcher); foregroundInput.addTextChangedListener(watcher)
        mode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == 1002) {
                backgroundInput.setText("#F6F4EE"); foregroundInput.setText("#1C1D1C")
            } else {
                backgroundInput.setText("#0A0B0B"); foregroundInput.setText("#ECE7DA")
            }
        }
        setContentView(root); updatePreview()
    }

    private fun fieldLabel(label: String, palette: Palette) = TextView(this).apply {
        text = label; textSize = 10f; letterSpacing = .12f; setTextColor(palette.muted); setPadding(0, dp(18), 0, 0)
    }
    private fun colourField(value: Int, palette: Palette) = EditText(this).apply {
        setTextColor(palette.foreground); textSize = 16f; isSingleLine = true
        setText(String.format("#%06X", 0xFFFFFF and value)); filters = arrayOf(InputFilter.LengthFilter(7))
        setPadding(0, dp(14), 0, dp(8))
    }
    private fun colourRow(input: EditText) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(input, LinearLayout.LayoutParams(0, dp(54), 1f))
        addView(TextView(this@ThemeEditorActivity).apply {
            text = "COLOUR WHEEL"; textSize = 10f; letterSpacing = .08f; gravity = Gravity.CENTER
            setTextColor(AppTheme.current.foreground); setPadding(dp(14), 0, 0, 0)
            setOnClickListener { openColourWheel(input) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(54)))
    }
    private fun openColourWheel(target: EditText) {
        val initial = parse(target.text.toString()) ?: Color.WHITE
        val hsv = FloatArray(3); Color.colorToHSV(initial, hsv)
        var hue = hsv[0]; var saturation = hsv[1]; var brightness = hsv[2]
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), 0)
        }
        val colourPreview = TextView(this)
        fun refreshWheelPreview() {
            val colour = Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
            colourPreview.setBackgroundColor(colour)
            colourPreview.setTextColor(if (brightness > .55f) Color.BLACK else Color.WHITE)
        }
        container.addView(colourPreview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(12) })
        val wheel = ColorWheelView(this).apply {
            setColour(initial)
            onColourChanged = { h, s -> hue = h; saturation = s; refreshWheelPreview() }
        }
        container.addView(wheel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280)))
        container.addView(TextView(this).apply { text = "BRIGHTNESS"; textSize = 10f; letterSpacing = .12f; setTextColor(AppTheme.current.muted) })
        container.addView(SeekBar(this).apply {
            max = 100; progress = (brightness * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { brightness = progress / 100f; refreshWheelPreview() }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
        val dialog = AlertDialog.Builder(this).setTitle("CHOOSE COLOUR").setView(container)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("USE COLOUR") { _, _ ->
                val colour = Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
                target.setText(String.format("#%06X", 0xFFFFFF and colour))
            }.create()
        refreshWheelPreview()
        dialog.show()
    }
    private fun updatePreview() {
        val bg = parse(backgroundInput.text.toString()) ?: return
        val fg = parse(foregroundInput.text.toString()) ?: return
        preview.setTextColor(fg)
        preview.background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(18).toFloat() }
    }
    private fun parse(value: String): Int? = try { Color.parseColor(value.trim()) } catch (_: IllegalArgumentException) { null }
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
