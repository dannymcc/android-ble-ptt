package io.dmcc.bleptt.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.dmcc.bleptt.PttApp
import io.dmcc.bleptt.ble.PttBleClient
import io.dmcc.bleptt.data.PairedButton
import io.dmcc.bleptt.data.PairedRepository
import io.dmcc.bleptt.service.PttForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PttViewModel(application: Application) : AndroidViewModel(application) {

    private val app: PttApp = application as PttApp
    private val client: PttBleClient = app.bleClient
    private val repo: PairedRepository = app.pairedRepository

    val connectionState: StateFlow<PttBleClient.ConnectionState> = client.state
    val isTransmitting: StateFlow<Boolean> = client.pressed
    val discoveredDevices: StateFlow<List<PttBleClient.Discovered>> = client.devices
    val paired: StateFlow<List<PairedButton>> = repo.paired

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val activePairedAddress: StateFlow<String?> = connectionState
        .map { state ->
            when (state) {
                is PttBleClient.ConnectionState.Connected -> state.address
                is PttBleClient.ConnectionState.Connecting -> state.address
                else -> null
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // On launch, auto-connect to the first paired button (if any) and bring up the
        // foreground service so the BLE link survives screen-off.
        repo.paired.value.firstOrNull()?.let { connectToPaired(it.address) }
    }

    fun startScan() {
        _isScanning.value = true
        client.startScan()
    }

    fun stopScan() {
        _isScanning.value = false
        client.stopScan()
    }

    fun pair(device: PttBleClient.Discovered) {
        repo.add(PairedButton(address = device.device.address, name = device.name ?: "PTT button"))
        stopScan()
        connectToPaired(device.device.address)
    }

    fun unpair(address: String) {
        repo.remove(address)
        if (activePairedAddress.value == address) {
            client.disconnect()
        }
        if (repo.paired.value.isEmpty()) {
            PttForegroundService.stop(getApplication())
        }
    }

    fun connectToPaired(address: String) {
        PttForegroundService.start(getApplication())
        client.connect(address)
    }

    fun stopService() {
        client.disconnect()
        PttForegroundService.stop(getApplication())
    }
}
