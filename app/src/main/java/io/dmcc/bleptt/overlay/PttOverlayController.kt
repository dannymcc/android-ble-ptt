package io.dmcc.bleptt.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import io.dmcc.bleptt.data.OverlayColour
import io.dmcc.bleptt.data.OverlayPosition
import io.dmcc.bleptt.data.OverlaySettings
import io.dmcc.bleptt.data.OverlayStyle

/**
 * Floating PTT indicator shown while the button is held — Zello-style.
 *
 * Layered cues so a press is always observable, regardless of which permissions/state the
 * device is in:
 *   1. Haptic — short vibration on press, slightly longer on release.
 *   2. Wake lock — turns the screen on if it was off.
 *   3. Overlay window — coral pill / banner / border / dot, configurable in Settings.
 */
class PttOverlayController(private val appContext: Context) {

    private val windowManager: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val powerManager: PowerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var view: View? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun isPermitted(): Boolean = Settings.canDrawOverlays(appContext)

    fun show(settings: OverlaySettings) {
        vibrate(PRESS_VIBRATION_MS)
        acquireWakeLock()
        addView(settings)
    }

    fun hide() {
        vibrate(RELEASE_VIBRATION_MS)
        removeView()
        releaseWakeLock()
    }

    private fun addView(settings: OverlaySettings) {
        if (view != null) return
        if (!isPermitted()) return

        val (newView, params) = buildOverlay(settings)
        runCatching { windowManager.addView(newView, params) }
            .onSuccess { view = newView }
    }

    private fun removeView() {
        val current = view ?: return
        runCatching { windowManager.removeViewImmediate(current) }
        view = null
    }

    private fun buildOverlay(settings: OverlaySettings): Pair<View, WindowManager.LayoutParams> {
        val view = when (settings.style) {
            OverlayStyle.Pill -> buildPill(settings.colour)
            OverlayStyle.Banner -> buildBanner(settings.colour)
            OverlayStyle.Border -> buildBorder(settings.colour)
            OverlayStyle.Dot -> buildDot(settings.colour)
        }
        val params = buildParams(settings)
        return view to params
    }

    private fun buildParams(settings: OverlaySettings): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        val (width, height) = when (settings.style) {
            OverlayStyle.Pill, OverlayStyle.Dot ->
                WindowManager.LayoutParams.WRAP_CONTENT to WindowManager.LayoutParams.WRAP_CONTENT
            OverlayStyle.Banner ->
                WindowManager.LayoutParams.MATCH_PARENT to WindowManager.LayoutParams.WRAP_CONTENT
            OverlayStyle.Border ->
                WindowManager.LayoutParams.MATCH_PARENT to WindowManager.LayoutParams.MATCH_PARENT
        }

        val gravity = when (settings.style) {
            OverlayStyle.Border ->
                Gravity.TOP or Gravity.START
            OverlayStyle.Dot ->
                verticalGravity(settings.position) or Gravity.END
            OverlayStyle.Pill, OverlayStyle.Banner ->
                verticalGravity(settings.position) or Gravity.CENTER_HORIZONTAL
        }

        val verticalInset = when (settings.style) {
            OverlayStyle.Pill -> dp(36)
            OverlayStyle.Banner -> dp(0)
            OverlayStyle.Dot -> dp(28)
            OverlayStyle.Border -> 0
        }

        return WindowManager.LayoutParams(width, height, type, flags, PixelFormat.TRANSLUCENT).apply {
            this.gravity = gravity
            this.y = verticalInset
            this.x = if (settings.style == OverlayStyle.Dot) dp(16) else 0
        }
    }

    private fun verticalGravity(position: OverlayPosition): Int = when (position) {
        OverlayPosition.Top -> Gravity.TOP
        OverlayPosition.Bottom -> Gravity.BOTTOM
    }

    private fun buildPill(colour: OverlayColour): View {
        val text = TextView(appContext).apply {
            text = "● TX"
            setTextColor(textColourFor(colour))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(18), dp(8), dp(18), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28).toFloat()
                setColor(colour.argb)
            }
        }
        return wrap(text)
    }

    private fun buildBanner(colour: OverlayColour): View {
        val text = TextView(appContext).apply {
            text = "● TX"
            setTextColor(textColourFor(colour))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(14))
            setBackgroundColor(colour.argb)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        return FrameLayout(appContext).apply { addView(text) }
    }

    private fun buildBorder(colour: OverlayColour): View {
        val borderDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(dp(6), colour.argb)
            // Solid transparent fill so the rest of the screen stays interactive looking.
            setColor(0x00000000)
        }
        return FrameLayout(appContext).apply {
            background = borderDrawable
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
    }

    private fun buildDot(colour: OverlayColour): View {
        val dot = View(appContext).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colour.argb)
            }
        }
        val size = dp(18)
        return FrameLayout(appContext).apply {
            addView(dot, FrameLayout.LayoutParams(size, size))
        }
    }

    private fun wrap(child: View): View = FrameLayout(appContext).apply {
        addView(
            child,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun textColourFor(colour: OverlayColour): Int =
        // White-on-coloured for most palettes; for the white preset, flip to a dark label so it
        // stays legible.
        if (colour == OverlayColour.White) Color.BLACK else Color.WHITE

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val lock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "BlePtt:press",
        )
        runCatching { lock.acquire(WAKE_LOCK_TIMEOUT_MS) }
        wakeLock = lock
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        runCatching { if (lock.isHeld) lock.release() }
        wakeLock = null
    }

    private fun vibrate(ms: Long) {
        val vibrator = vibrator() ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun dp(value: Int): Int {
        val density = appContext.resources.displayMetrics.density
        return (value * density).toInt()
    }

    private companion object {
        const val PRESS_VIBRATION_MS = 30L
        const val RELEASE_VIBRATION_MS = 15L
        const val WAKE_LOCK_TIMEOUT_MS = 30_000L
    }
}
