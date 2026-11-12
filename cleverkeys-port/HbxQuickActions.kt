// SPDX-License-Identifier: GPL-3.0-only
package tribixbite.cleverkeys

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Opt-in HBX actions placed above CleverKeys' native suggestion/clipboard UI.
 * No text is captured, saved or transmitted until the user touches the AI key.
 * Hidden on password fields. Never modifies swipe, prediction or gesture handlers.
 */
internal object HbxQuickActions {
    private const val TAG = "hbx-actions-v1"

    fun attach(
        container: LinearLayout?,
        context: Context,
        enabled: Boolean,
        openAi: () -> Unit,
        openSettings: () -> Unit
    ) {
        if (container == null) return
        val old = container.findViewWithTag<View>(TAG)
        if (old != null) container.removeView(old)
        if (!enabled) return

        val px = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * px + 0.5f).toInt()
        fun bg(fill: Int) = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(15).toFloat()
        }

        val row = LinearLayout(context).apply {
            tag = TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = bg(Color.rgb(29, 32, 40))
        }
        fun add(text: String, weight: Float, action: () -> Unit) {
            val button = TextView(context).apply {
                this.text = text
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = bg(Color.rgb(42, 46, 56))
                setOnClickListener { action() }
                isClickable = true
                isFocusable = true
                contentDescription = when (text) {
                    "✦ HBX IA" -> "Abrir assistente de escrita HBX"
                    else -> "Configurações do assistente HBX"
                }
            }
            val layout = LinearLayout.LayoutParams(0, dp(34), weight).apply {
                marginEnd = dp(5)
            }
            row.addView(button, layout)
        }
        add("✦ HBX IA", 3f, openAi)
        add("⚙", 1f, openSettings)
        container.addView(row, 0, LinearLayout.LayoutParams(-1, dp(40)))
    }
}
