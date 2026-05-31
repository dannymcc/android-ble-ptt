package io.dmcc.bleptt

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.dmcc.bleptt.ui.PttScreen
import io.dmcc.bleptt.ui.PttViewModel
import io.dmcc.bleptt.ui.SettingsScreen
import io.dmcc.bleptt.ui.theme.VoxColors
import io.dmcc.bleptt.ui.theme.VoxTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PttViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            VoxTheme {
                App(viewModel = viewModel)
            }
        }
    }
}

private enum class Tab(val label: String) { Ptt("PTT"), Settings("Settings") }

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun App(viewModel: PttViewModel) {
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    val transmitting by viewModel.isTransmitting.collectAsStateWithLifecycle()
    val discovered by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val paired by viewModel.paired.collectAsStateWithLifecycle()
    val activeAddress by viewModel.activePairedAddress.collectAsStateWithLifecycle()
    val scanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val bluetoothEnabled by viewModel.bluetoothEnabled.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Ptt) }

    val permissions = rememberMultiplePermissionsState(blePermissions())
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { /* The bluetoothEnabled flow reacts to the adapter state change broadcast, so nothing to do here. */ }

    val requestEnableBluetooth: () -> Unit = {
        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayPermitted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayPermitted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        overlayPermitted = Settings.canDrawOverlays(context)
    }
    val requestOverlayPermission: () -> Unit = {
        overlayPermissionLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopBar(
                isTransmitting = transmitting,
                pairedCount = paired.size,
            )
        },
        bottomBar = { BottomNav(current = tab, onSelect = { tab = it }) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tab) {
                Tab.Ptt -> PttScreen(
                    state = state,
                    isTransmitting = transmitting,
                    pairedCount = paired.size,
                )
                Tab.Settings -> SettingsScreen(
                    paired = paired,
                    activeAddress = activeAddress,
                    connectionState = state,
                    discovered = discovered,
                    isScanning = scanning,
                    bluetoothEnabled = bluetoothEnabled,
                    overlayPermitted = overlayPermitted,
                    onStartPairing = {
                        when {
                            !permissions.allPermissionsGranted ->
                                permissions.launchMultiplePermissionRequest()
                            !bluetoothEnabled -> requestEnableBluetooth()
                            else -> viewModel.startScan()
                        }
                    },
                    onPickDevice = { viewModel.pair(it) },
                    onCancelPairing = { viewModel.stopScan() },
                    onRemove = { viewModel.unpair(it) },
                    onRequestEnableBluetooth = requestEnableBluetooth,
                    onRequestOverlayPermission = requestOverlayPermission,
                )
            }
        }
    }
}

@Composable
private fun TopBar(isTransmitting: Boolean, pairedCount: Int) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "BLE PTT",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            StatusPill(isTransmitting = isTransmitting, pairedCount = pairedCount)
        }
    }
}

@Composable
private fun StatusPill(isTransmitting: Boolean, pairedCount: Int) {
    val (text, fg, bg) = when {
        isTransmitting -> Triple("TX", Color.White, VoxColors.Coral)
        pairedCount == 0 -> Triple("No buttons", VoxColors.Muted, VoxColors.SurfaceElevated)
        else -> Triple("Ready", VoxColors.Background, VoxColors.PillBlue)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceAround,
            ) {
                Tab.values().forEach { tab ->
                    NavItem(
                        tab = tab,
                        selected = tab == current,
                        onClick = { onSelect(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.onBackground else VoxColors.Muted
    val background = if (selected) VoxColors.SurfaceElevated else Color.Transparent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = when (tab) {
                Tab.Ptt -> Icons.Outlined.Mic
                Tab.Settings -> Icons.Outlined.Settings
            },
            contentDescription = tab.label,
            tint = color,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = tab.label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun blePermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        buildList {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    } else {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
        )
    }
