package io.dmcc.bleptt.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OverlayStyle { Pill, Banner, Border, Dot }

enum class OverlayPosition { Top, Bottom }

enum class OverlayColour(val argb: Int) {
    Coral(0xFFEF5A4D.toInt()),
    Blue(0xFF2563EB.toInt()),
    Green(0xFF22C55E.toInt()),
    White(0xFFFFFFFF.toInt()),
}

data class OverlaySettings(
    val style: OverlayStyle = OverlayStyle.Pill,
    val position: OverlayPosition = OverlayPosition.Top,
    val colour: OverlayColour = OverlayColour.Coral,
)

class OverlayPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<OverlaySettings> = _settings.asStateFlow()

    fun setStyle(style: OverlayStyle) = update { it.copy(style = style) }
    fun setPosition(position: OverlayPosition) = update { it.copy(position = position) }
    fun setColour(colour: OverlayColour) = update { it.copy(colour = colour) }

    private fun update(transform: (OverlaySettings) -> OverlaySettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_STYLE, next.style.name)
            .putString(KEY_POSITION, next.position.name)
            .putString(KEY_COLOUR, next.colour.name)
            .apply()
        _settings.value = next
    }

    private fun load(): OverlaySettings = OverlaySettings(
        style = readEnum(KEY_STYLE, OverlayStyle.Pill),
        position = readEnum(KEY_POSITION, OverlayPosition.Top),
        colour = readEnum(KEY_COLOUR, OverlayColour.Coral),
    )

    private inline fun <reified E : Enum<E>> readEnum(key: String, default: E): E {
        val raw = prefs.getString(key, null) ?: return default
        return runCatching { enumValueOf<E>(raw) }.getOrDefault(default)
    }

    private companion object {
        const val PREFS = "ble_ptt_overlay"
        const val KEY_STYLE = "style"
        const val KEY_POSITION = "position"
        const val KEY_COLOUR = "colour"
    }
}
