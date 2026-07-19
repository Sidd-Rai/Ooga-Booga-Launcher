package com.nolauncher

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

class PrivacyPolicyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.prepare(this); super.onCreate(savedInstanceState); AppTheme.apply(this)
        val p = AppTheme.current
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(38), dp(28), dp(30)); setBackgroundColor(p.background)
        }
        root.addView(TextView(this).apply {
            text = "PRIVACY POLICY\nOOGA BOOGA LAUNCHER"; gravity = Gravity.CENTER; textSize = 25f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); setTextColor(p.foreground); letterSpacing = .08f
        })
        root.addView(TextView(this).apply {
            text = "Bleh, I dont store anything"; gravity = Gravity.CENTER; textSize = 18f; setTextColor(p.foreground)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(TextView(this).apply {
            text = "END OF PRIVACY POLICY\nEFFECTIVE UPON PUBLICATION"; gravity = Gravity.CENTER; textSize = 10f
            letterSpacing = .12f; setTextColor(p.muted)
        })
        setContentView(root)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
