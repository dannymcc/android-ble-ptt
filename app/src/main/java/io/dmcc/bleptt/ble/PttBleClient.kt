package io.dmcc.bleptt.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Talks to a "Zello-style" BLE PTT button using the HM-10 / TI CC254x transparent-UART profile
 * (service 0xFFE0, characteristic 0xFFE1). On notify, byte 0 is 0x01 (pressed) or 0x00 (released).
 *
 * Verified against Focket PTT-Z01. See README for the captured GATT table.
 */
class PttBleClient(private val appContext: Context) {

    sealed interface ConnectionState {
        data object Idle : ConnectionState
        data object Scanning : ConnectionState
        data class Connecting(val address: String) : ConnectionState
        data class Connected(val address: String, val deviceName: String?) : ConnectionState
        data class Disconnected(val reason: String) : ConnectionState
        data class Error(val message: String) : ConnectionState
    }

    data class Discovered(val device: BluetoothDevice, val name: String?, val rssi: Int)

    sealed interface PttEvent {
        val timestampMs: Long
        data class Pressed(override val timestampMs: Long) : PttEvent
        data class Released(override val timestampMs: Long) : PttEvent
        data class Raw(override val timestampMs: Long, val bytesHex: String) : PttEvent
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<Discovered>>(emptyList())
    val devices: StateFlow<List<Discovered>> = _devices.asStateFlow()

    private val _pressed = MutableStateFlow(false)
    val pressed: StateFlow<Boolean> = _pressed.asStateFlow()

    private val _events = MutableSharedFlow<PttEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<PttEvent> = _events.asSharedFlow()

    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var gatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private var autoReconnectEnabled: Boolean = true
    private var targetAddress: String? = null
    private var scanStartedElapsedNs: Long = 0L

    private val _bluetoothEnabled = MutableStateFlow(adapter?.isEnabled == true)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val state = intent.getIntExtra(
                BluetoothAdapter.EXTRA_STATE,
                BluetoothAdapter.STATE_OFF,
            )
            val enabled = state == BluetoothAdapter.STATE_ON
            _bluetoothEnabled.value = enabled
            if (!enabled) {
                // Adapter turned off — stop any in-flight scan and surface the disconnection.
                stopScan()
                _pressed.value = false
                if (this@PttBleClient.state.value !is ConnectionState.Idle) {
                    _state.value = ConnectionState.Disconnected("Bluetooth turned off")
                }
            }
        }
    }

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(adapterStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(adapterStateReceiver, filter)
        }
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    /**
     * The address this client is currently bonded to in software — i.e., the address it will
     * auto-reconnect to. May be set even when [state] is Disconnected (autoConnect=true is
     * silently waiting for the peripheral to re-advertise). Callers need this to know whether
     * removing a paired button should also tear down the BLE link.
     */
    fun currentTarget(): String? = targetAddress

    fun forgetDevice(address: String) {
        _devices.update { current -> current.filterNot { it.device.address == address } }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (adapter?.isEnabled != true) {
            _state.value = ConnectionState.Error("Bluetooth is off")
            return
        }
        val scanner = adapter?.bluetoothLeScanner ?: run {
            _state.value = ConnectionState.Error("Bluetooth scanner unavailable")
            return
        }
        stopScan()
        _devices.value = emptyList()
        scanStartedElapsedNs = SystemClock.elapsedRealtimeNanos()
        _state.value = ConnectionState.Scanning

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::handleScanResult)
            }

            override fun onScanFailed(errorCode: Int) {
                _state.value = ConnectionState.Error("Scan failed: code=$errorCode")
            }
        }
        scanCallback = cb
        // Unfiltered scan: some PTT button modules don't put the service UUID in the
        // advertisement, only the device name beginning "PTT". handleScanResult filters in code.
        scanner.startScan(null, settings, cb)
    }

    private fun handleScanResult(result: ScanResult) {
        // Android's BLE scanner replays cached advertisements when a new scan begins. Drop any
        // result whose advertisement was received before this scan session started — otherwise
        // buttons that aren't currently advertising still show in the pair sheet.
        if (scanStartedElapsedNs > 0 && result.timestampNanos < scanStartedElapsedNs) return

        val name = scanName(result)
        val matchesService = result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE_PTT } == true
        val matchesName = name?.startsWith("PTT", ignoreCase = true) == true
        if (!matchesService && !matchesName) return

        _devices.update { current ->
            if (current.any { it.device.address == result.device.address }) current
            else current + Discovered(result.device, name, result.rssi)
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanName(result: ScanResult): String? =
        result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull()

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        scanCallback?.let { scanner.stopScan(it) }
        scanCallback = null
        scanStartedElapsedNs = 0L
        _devices.value = emptyList()
    }

    /**
     * Initial connect — the user just paired and the button is currently advertising. Uses
     * autoConnect=false for a fast first connection.
     */
    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        connectInternal(address, autoConnect = false)
    }

    @SuppressLint("MissingPermission")
    private fun connectInternal(address: String, autoConnect: Boolean) {
        val device = adapter?.getRemoteDevice(address) ?: run {
            _state.value = ConnectionState.Error("Invalid device address $address")
            return
        }
        stopScan()
        autoReconnectEnabled = true
        targetAddress = address
        _state.value = ConnectionState.Connecting(address)
        gatt?.close()
        gatt = device.connectGatt(
            appContext,
            autoConnect,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE,
        )
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        autoReconnectEnabled = false
        targetAddress = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.value = ConnectionState.Idle
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected, discovering services")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected status=$status")
                    _pressed.value = false
                    _state.value = ConnectionState.Disconnected("status=$status")
                    g.close()
                    gatt = null
                    // Re-arm with autoConnect=true so the OS handles wake-on-advertise for us —
                    // these buttons sleep and only advertise briefly after a press, which is
                    // exactly the case autoConnect was designed for. Much more battery-friendly
                    // and reliable in the background than an app-side polling loop.
                    val addr = targetAddress
                    if (autoReconnectEnabled && addr != null) {
                        connectInternal(addr, autoConnect = true)
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = ConnectionState.Error("Service discovery failed status=$status")
                return
            }
            val service = g.getService(SERVICE_PTT) ?: run {
                _state.value = ConnectionState.Error("PTT service 0xFFE0 not found")
                return
            }
            val char = service.getCharacteristic(CHAR_PTT) ?: run {
                _state.value = ConnectionState.Error("PTT characteristic 0xFFE1 not found")
                return
            }
            if (!g.setCharacteristicNotification(char, true)) {
                _state.value = ConnectionState.Error("setCharacteristicNotification returned false")
                return
            }
            val descriptor = char.getDescriptor(CCCD) ?: run {
                _state.value = ConnectionState.Error("CCCD 0x2902 missing on 0xFFE1")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid == CCCD) {
                val deviceName = runCatching {
                    @SuppressLint("MissingPermission")
                    g.device.name
                }.getOrNull()
                _state.value = ConnectionState.Connected(g.device.address, deviceName)
                Log.i(TAG, "Notifications enabled on 0xFFE1")
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = ConnectionState.Error("Descriptor write failed status=$status")
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotify(value)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = characteristic.value ?: return
            handleNotify(value)
        }
    }

    private fun handleNotify(value: ByteArray) {
        val now = System.currentTimeMillis()
        val first = value.firstOrNull()
        val hex = value.joinToString(" ") { "%02X".format(it) }
        scope.launch { _events.emit(PttEvent.Raw(now, hex)) }
        when (first) {
            0x01.toByte() -> {
                _pressed.value = true
                scope.launch { _events.emit(PttEvent.Pressed(now)) }
            }
            0x00.toByte() -> {
                _pressed.value = false
                scope.launch { _events.emit(PttEvent.Released(now)) }
            }
            else -> Log.d(TAG, "Unhandled notify bytes: $hex")
        }
    }

    companion object {
        private const val TAG = "PttBleClient"

        val SERVICE_PTT: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val CHAR_PTT: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
