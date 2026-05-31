package io.dmcc.bleptt.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PairedButton(val address: String, val name: String)

/**
 * Persists the list of paired BLE PTT buttons in SharedPreferences. Single-process app, so flat
 * storage with a tiny serialisation format is plenty — no Room or DataStore needed for a PoC.
 */
class PairedRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _paired = MutableStateFlow(load())
    val paired: StateFlow<List<PairedButton>> = _paired.asStateFlow()

    fun add(button: PairedButton) {
        val current = _paired.value
        if (current.any { it.address == button.address }) return
        save(current + button)
    }

    fun remove(address: String) {
        save(_paired.value.filterNot { it.address == address })
    }

    private fun save(list: List<PairedButton>) {
        val raw = list.joinToString(SEP_RECORD) { "${it.address}$SEP_FIELD${it.name}" }
        prefs.edit().putString(KEY_PAIRED, raw).apply()
        _paired.value = list
    }

    private fun load(): List<PairedButton> {
        val raw = prefs.getString(KEY_PAIRED, null).orEmpty()
        if (raw.isEmpty()) return emptyList()
        return raw.split(SEP_RECORD).mapNotNull { entry ->
            val parts = entry.split(SEP_FIELD, limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank()) {
                PairedButton(address = parts[0], name = parts[1])
            } else {
                null
            }
        }
    }

    companion object {
        private const val PREFS = "ble_ptt_prefs"
        private const val KEY_PAIRED = "paired"
        // ASCII Record Separator (0x1E) and Unit Separator (0x1F). Neither can appear in a
        // Bluetooth MAC address; both are vanishingly unlikely in a human-typed device name.
        private const val SEP_RECORD = ""
        private const val SEP_FIELD = ""
    }
}
