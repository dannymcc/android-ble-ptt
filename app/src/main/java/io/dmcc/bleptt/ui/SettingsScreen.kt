package io.dmcc.bleptt.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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
    onStartPairing: () -> Unit,
    onPickDevice: (PttBleClient.Discovered) -> Unit,
    onCancelPairing: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        SectionLabel(text = "PAIRED BUTTONS")
        Spacer(Modifier.height(8.dp))

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (paired.isEmpty()) {
                EmptyPairedState()
            } else {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    paired.forEachIndexed { index, button ->
                        PairedRow(
                            button = button,
                            isActive = activeAddress == button.address,
                            connectionState = connectionState.takeIf { activeAddress == button.address },
                            onRemove = { onRemove(button.address) },
                        )
                        if (index < paired.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outline),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                onStartPairing()
                sheetOpen = true
            },
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VoxColors.PillBlue,
                contentColor = VoxColors.Background,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(text = "Add a BLE PTT button", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel(text = "ABOUT")
        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = "BLE PTT proof of concept",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Listens to HM-10 / TI CC254x style PTT buttons (service 0xFFE0, " +
                        "characteristic 0xFFE1). Notify 0x01 = press, 0x00 = release.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoxColors.Muted,
                )
            }
        }
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
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = VoxColors.Muted,
        modifier = Modifier.padding(start = 6.dp),
    )
}

@Composable
private fun EmptyPairedState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 26.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No buttons paired yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap “Add a BLE PTT button” and press your button to wake it.",
            style = MaterialTheme.typography.bodyMedium,
            color = VoxColors.Muted,
        )
    }
}

@Composable
private fun PairedRow(
    button: PairedButton,
    isActive: Boolean,
    connectionState: PttBleClient.ConnectionState?,
    onRemove: () -> Unit,
) {
    val dotColor = when (connectionState) {
        is PttBleClient.ConnectionState.Connected -> VoxColors.PillBlue
        is PttBleClient.ConnectionState.Connecting -> VoxColors.Muted
        is PttBleClient.ConnectionState.Disconnected -> VoxColors.Muted
        is PttBleClient.ConnectionState.Error -> VoxColors.Coral
        else -> VoxColors.Muted
    }
    val statusText = when (connectionState) {
        is PttBleClient.ConnectionState.Connected -> "Connected"
        is PttBleClient.ConnectionState.Connecting -> "Connecting…"
        is PttBleClient.ConnectionState.Disconnected -> "Disconnected"
        is PttBleClient.ConnectionState.Error -> connectionState.message
        else -> if (isActive) "Idle" else "Not active"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = button.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${button.address} • $statusText",
                style = MaterialTheme.typography.bodySmall,
                color = VoxColors.Muted,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Remove",
                tint = VoxColors.Muted,
            )
        }
    }
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
        color = MaterialTheme.colorScheme.surfaceVariant,
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
                    text = "${device.device.address} • ${device.rssi} dBm",
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
