package io.dmcc.bleptt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.dmcc.bleptt.ble.PttBleClient
import io.dmcc.bleptt.data.PairedButton
import io.dmcc.bleptt.ui.theme.VoxColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    paired: List<PairedButton>,
    activeAddress: String?,
    connectionState: PttBleClient.ConnectionState,
    discovered: List<PttBleClient.Discovered>,
    isScanning: Boolean,
    bluetoothEnabled: Boolean,
    overlayPermitted: Boolean,
    onStartPairing: () -> Unit,
    onPickDevice: (PttBleClient.Discovered) -> Unit,
    onCancelPairing: () -> Unit,
    onRemove: (String) -> Unit,
    onRequestEnableBluetooth: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )

        SearchPlaceholder()

        if (!bluetoothEnabled) {
            BluetoothOffBanner(onTurnOn = onRequestEnableBluetooth)
        }

        SectionLabel("HARDWARE")
        // Placeholder for the existing VoxDMR hardware-key option, so the developer can see
        // exactly where BLE PTT slots in next to it. Not wired up in this PoC.
        SettingsRow(
            icon = Icons.Outlined.Keyboard,
            title = "Hardware key",
            subtitle = "Existing VoxDMR option (not implemented in PoC)",
            onClick = null,
            trailing = {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = VoxColors.Muted,
                )
            },
        )
        Divider()
        SettingsRow(
            icon = Icons.Outlined.Bluetooth,
            title = "BLE PTT button",
            subtitle = if (bluetoothEnabled) {
                bleSubtitle(paired = paired, activeAddress = activeAddress, state = connectionState)
            } else {
                "Bluetooth is off · tap to turn on"
            },
            onClick = {
                if (!bluetoothEnabled) {
                    onRequestEnableBluetooth()
                } else {
                    onStartPairing()
                    sheetOpen = true
                }
            },
            trailing = {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = VoxColors.Muted,
                )
            },
        )
        paired.forEach { button ->
            Divider()
            PairedRow(
                button = button,
                isActive = activeAddress == button.address,
                connectionState = connectionState.takeIf { activeAddress == button.address },
                onRemove = { onRemove(button.address) },
            )
        }

        SectionLabel("BACKGROUND")
        SettingsRow(
            icon = Icons.Outlined.Layers,
            title = "Floating PTT overlay",
            subtitle = if (overlayPermitted) {
                "On · shows a TX pill over any app while the button is held"
            } else {
                "Not granted · tap to allow Display over other apps"
            },
            onClick = if (overlayPermitted) null else onRequestOverlayPermission,
            trailing = {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = VoxColors.Muted,
                )
            },
        )

        SectionLabel("ABOUT")
        SettingsRow(
            icon = Icons.Outlined.Info,
            title = "BLE PTT proof of concept",
            subtitle = "HM-10 service 0xFFE0 · notify 0xFFE1",
            onClick = null,
        )
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = {
                onCancelPairing()
                sheetOpen = false
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            PairingSheet(
                discovered = discovered,
                isScanning = isScanning,
                onPick = { device ->
                    onPickDevice(device)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { sheetOpen = false }
                },
            )
        }
    }

    LaunchedEffect(sheetOpen) {
        if (!sheetOpen) onCancelPairing()
    }
}

@Composable
private fun BluetoothOffBanner(onTurnOn: () -> Unit) {
    Surface(
        color = VoxColors.SurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clickable(onClick = onTurnOn),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.BluetoothDisabled,
                contentDescription = null,
                tint = VoxColors.Coral,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bluetooth is off",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Turn it on to pair a button",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoxColors.Muted,
                )
            }
            Text(
                text = "Turn on",
                style = MaterialTheme.typography.labelLarge,
                color = VoxColors.PillBlue,
            )
        }
    }
}

@Composable
private fun SearchPlaceholder() {
    Surface(
        color = VoxColors.SurfaceElevated,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = VoxColors.Muted)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Search settings",
                color = VoxColors.Muted,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = VoxColors.Muted,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 64.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    trailing: @Composable (() -> Unit)? = null,
) {
    val baseModifier = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 20.dp, vertical = 14.dp)

    Row(modifier = baseModifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = VoxColors.Muted,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoxColors.Muted,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun PairedRow(
    button: PairedButton,
    isActive: Boolean,
    connectionState: PttBleClient.ConnectionState?,
    onRemove: () -> Unit,
) {
    val (statusText, dotColor) = describePaired(connectionState, isActive)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.RadioButtonChecked,
                contentDescription = null,
                tint = dotColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = button.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${button.address} · $statusText",
                style = MaterialTheme.typography.bodyMedium,
                color = VoxColors.Muted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(36.dp)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = VoxColors.Muted)
        }
    }
}

private fun bleSubtitle(
    paired: List<PairedButton>,
    activeAddress: String?,
    state: PttBleClient.ConnectionState,
): String = when {
    paired.isEmpty() -> "Not paired · tap to add"
    state is PttBleClient.ConnectionState.Connected && activeAddress != null ->
        "${paired.firstOrNull { it.address == activeAddress }?.name ?: state.address} · connected"
    state is PttBleClient.ConnectionState.Connecting -> "Connecting…"
    state is PttBleClient.ConnectionState.Scanning -> "Scanning…"
    paired.size == 1 -> "${paired.first().name} · waiting for press"
    else -> "${paired.size} buttons paired"
}

private fun describePaired(
    state: PttBleClient.ConnectionState?,
    isActive: Boolean,
): Pair<String, Color> = when (state) {
    is PttBleClient.ConnectionState.Connected -> "Connected" to VoxColors.PillBlue
    is PttBleClient.ConnectionState.Connecting -> "Connecting…" to VoxColors.Muted
    is PttBleClient.ConnectionState.Disconnected -> "Disconnected" to VoxColors.Muted
    is PttBleClient.ConnectionState.Error -> state.message to VoxColors.Coral
    else -> (if (isActive) "Idle" else "Not active") to VoxColors.Muted
}

@Composable
private fun PairingSheet(
    discovered: List<PttBleClient.Discovered>,
    isScanning: Boolean,
    onPick: (PttBleClient.Discovered) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Press your PTT button now",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (isScanning) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = VoxColors.PillBlue,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "These buttons only advertise for a few seconds after a physical press.",
            style = MaterialTheme.typography.bodyMedium,
            color = VoxColors.Muted,
        )
        Spacer(Modifier.height(16.dp))

        if (discovered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isScanning) "Scanning…" else "Nothing found yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoxColors.Muted,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(discovered, key = { it.device.address }) { device ->
                    DiscoveredRow(device = device, onClick = { onPick(device) })
                }
            }
        }
    }
}

@Composable
private fun DiscoveredRow(device: PttBleClient.Discovered, onClick: () -> Unit) {
    Surface(
        color = VoxColors.SurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown PTT",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "${device.device.address} · ${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoxColors.Muted,
                )
            }
            Text(
                text = "Pair",
                style = MaterialTheme.typography.labelLarge,
                color = VoxColors.PillBlue,
            )
        }
    }
}
