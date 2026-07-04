package com.magick.gsm2sip.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.magick.gsm2sip.gateway.GatewayState

/**
 * Home screen: big status indicator + start/stop controls + a quick summary of
 * the active SIP account and audio-bridge capability.
 */
@Composable
fun StatusScreen(
    state: GatewayState,
    accountUri: String,
    audioBridgeSupported: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = state !is GatewayState.Stopped

    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = statusColor(state),
                        shape = CircleShape,
                        modifier = Modifier.size(16.dp),
                    ) {}
                    Spacer(Modifier.size(12.dp))
                    Text(state.label, style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    accountUri.ifBlank { "No SIP account configured" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                if (state is GatewayState.Error) {
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Audio bridging", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (audioBridgeSupported) {
                        "Supported — GSM voice capture available (rooted / concurrency unlocked)."
                    } else {
                        "Unavailable on this device. SIP + GSM signalling will work, but audio " +
                            "will NOT be bridged. Requires root + the Magisk concurrency module."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (audioBridgeSupported) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.error,
                )
            }
        }

        if (running) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text("Stop gateway")
            }
        } else {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("Start gateway")
            }
        }
    }
}

@Composable
private fun statusColor(state: GatewayState): Color = when (state) {
    GatewayState.Stopped -> Color(0xFF9E9E9E)
    GatewayState.Starting -> Color(0xFFFFB300)
    GatewayState.Registered -> Color(0xFF2E7D32)
    is GatewayState.Calling -> Color(0xFF1565C0)
    is GatewayState.InCall -> if (state.audioBridged) Color(0xFF2E7D32) else Color(0xFFEF6C00)
    is GatewayState.Error -> Color(0xFFC62828)
}
