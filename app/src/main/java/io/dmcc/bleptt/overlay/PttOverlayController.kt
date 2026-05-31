package io.dmcc.bleptt.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Floating "TX" pill that appears at the top of the screen when the BLE button is held down,
 * even if our app is in the background or the device is locked. Lifts the Zello pattern so the
 * rider gets immediate confirmation that the press was registered without unlocking the phone.
 *
 * Requires SYSTEM_ALERT_WINDOW; the controller checks the runtime grant via
 * [Settings.canDrawOverlays] before attempting to add the view.
 */
class PttOverlayController(private val appContext: Context) {

    private val windowManager: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var pill: View? = null

    fun isPermitted(): Boolean = Settings.canDrawOverlays(appContext)

    fun show() {
        if (pill != null) return
        if (!isPermitted()) return

        val view = buildPill()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(36)
        }

        runCatching { windowManager.addView(view, params) }
            .onSuccess { pill = view }
    }

    fun hide() {
        val view = pill ?: return
        runCatching { windowManager.removeViewImmediate(view) }
        pill = null
    }

    private fun buildPill(): View {
        val coral = 0xFFEF5A4D.toInt()
        val text = TextView(appContext).apply {
            text = "● TX"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(18), dp(8), dp(18), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28).toFloat()
                setColor(coral)
            }
        }
        return FrameLayout(appContext).apply {
            addView(
                text,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun dp(value: Int): Int {
        val density = appContext.resources.displayMetrics.density
        return (value * density).toInt()
    }
}
