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

/**
 * Floating "TX" pill that appears at the top of the screen when the BLE button is held down,
 * even if our app is in the background or the device is locked. Lifts the Zello pattern so the
 * rider gets immediate confirmation that the press was registered without unlocking the phone.
 *
 * Three layered cues so a press is always observable, regardless of which permissions/state
 * the device is in:
 *   1. Haptic — short vibration on press, slightly longer on release. Works with no extra grants.
 *   2. Wake lock — turns the screen on if it was off (SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP).
 *   3. Overlay window — the coral TX pill, drawn over whatever's on screen.
 */
class PttOverlayController(private val appContext: Context) {

    private val windowManager: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val powerManager: PowerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var pill: View? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun isPermitted(): Boolean = Settings.canDrawOverlays(appContext)

    fun show() {
        vibrate(PRESS_VIBRATION_MS)
        acquireWakeLock()
        addPill()
    }

    fun hide() {
        vibrate(RELEASE_VIBRATION_MS)
        removePill()
        releaseWakeLock()
    }

    private fun addPill() {
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

    private fun removePill() {
        val view = pill ?: return
        runCatching { windowManager.removeViewImmediate(view) }
        pill = null
    }

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

    private companion object {
        const val PRESS_VIBRATION_MS = 30L
        const val RELEASE_VIBRATION_MS = 15L
        // Cap the wake lock so a stuck-on connection (or a stuck-pressed button) can never burn
        // the screen indefinitely. 30s is comfortably longer than any realistic transmission.
        const val WAKE_LOCK_TIMEOUT_MS = 30_000L
    }
}
