package io.dmcc.bleptt.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.dmcc.bleptt.ble.PttBleClient
import io.dmcc.bleptt.ui.theme.VoxColors

@Composable
fun PttScreen(
    state: PttBleClient.ConnectionState,
    isTransmitting: Boolean,
    pairedCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        StatusCard(state = state, isTransmitting = isTransmitting, pairedCount = pairedCount)
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TxButton(isTransmitting = isTransmitting)
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (isTransmitting) "Transmitting" else "Hold the BLE button to transmit",
                style = MaterialTheme.typography.bodyMedium,
                color = VoxColors.Muted,
            )
        }
    }
}

@Composable
private fun StatusCard(
    state: PttBleClient.ConnectionState,
    isTransmitting: Boolean,
    pairedCount: Int,
) {
    val (headline, subline, dotColor) = describe(state, isTransmitting, pairedCount)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(dotColor, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (subline.isNotEmpty()) {
                    Text(
                        text = subline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VoxColors.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun TxButton(isTransmitting: Boolean) {
    val color by animateColorAsState(
        targetValue = if (isTransmitting) VoxColors.Coral else VoxColors.SurfaceElevated,
        label = "tx-color",
    )
    val scale by animateFloatAsState(
        targetValue = if (isTransmitting) 1.02f else 1.0f,
        label = "tx-scale",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .background(color = color, shape = RoundedCornerShape(28.dp))
            .border(
                width = 1.dp,
                color = if (isTransmitting) VoxColors.CoralPressed else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(28.dp),
            )
            .padding(vertical = 38.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "TX",
            color = if (isTransmitting) Color.White else VoxColors.Muted,
            fontWeight = FontWeight.Bold,
            fontSize = 38.sp,
        )
    }
}

private data class StatusDescription(
    val headline: String,
    val subline: String,
    val dotColor: Color,
)

private fun describe(
    state: PttBleClient.ConnectionState,
    isTransmitting: Boolean,
    pairedCount: Int,
): StatusDescription = when (state) {
    is PttBleClient.ConnectionState.Connected -> StatusDescription(
        headline = if (isTransmitting) "Transmitting" else "Idle",
        subline = "Connected to ${state.deviceName ?: state.address}",
        dotColor = if (isTransmitting) VoxColors.Coral else VoxColors.PillBlue,
    )
    is PttBleClient.ConnectionState.Connecting -> StatusDescription(
        headline = "Connecting…",
        subline = state.address,
        dotColor = VoxColors.PillBlue,
    )
    is PttBleClient.ConnectionState.Scanning -> StatusDescription(
        headline = "Scanning…",
        subline = "Looking for BLE PTT buttons",
        dotColor = VoxColors.PillBlue,
    )
    is PttBleClient.ConnectionState.Disconnected -> StatusDescription(
        headline = "Disconnected",
        subline = "Press the button to wake it up",
        dotColor = VoxColors.Muted,
    )
    is PttBleClient.ConnectionState.Error -> StatusDescription(
        headline = "Error",
        subline = state.message,
        dotColor = VoxColors.Coral,
    )
    PttBleClient.ConnectionState.Idle -> StatusDescription(
        headline = if (pairedCount == 0) "No button paired" else "Idle",
        subline = if (pairedCount == 0) "Add one in Settings" else "Waiting for connection",
        dotColor = VoxColors.Muted,
    )
}
