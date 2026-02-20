package com.memory.sotopatrick.ui.presentation.discovery
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.memory.sotopatrick.domain.matching.MatchingStatus
import com.memory.sotopatrick.domain.matching.MatchFailureReason

@Composable
fun ConnectionStatusOverlay(status: MatchingStatus) {
    val backgroundColor by animateColorAsState(
        when (status) {
            is MatchingStatus.Connected -> Color(0xFF4CAF50) // Green
            is MatchingStatus.Failed -> Color(0xFFF44336)    // Red
            is MatchingStatus.Connecting -> Color(0xFF2196F3) // Blue
            else -> Color.Gray.copy(alpha = 0.1f)
        }
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when (status) {
                is MatchingStatus.Idle -> {
                    Text("System Ready", style = MaterialTheme.typography.labelMedium   )
                }
                is MatchingStatus.WaitingForOpponent -> {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Advertising... Waiting for guest to join")
                }
                is MatchingStatus.Connecting -> {
                    LinearProgressIndicator(modifier = Modifier.width(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Negotiating BLE Handshake...")
                }
                is MatchingStatus.Connected -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Linked ", color = Color.White)
                }
                is MatchingStatus.Failed -> {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Error: ${status.reason.toDisplayText()}", color = Color.White)
                }

                MatchingStatus.Disconnected -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Disconnected ", color = Color.White)
                }
                is MatchingStatus.Ready -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Ready ! Players: ${status.players} ", color = Color.White)
                }
            }
        }
    }
}

private fun MatchFailureReason.toDisplayText(): String = when (this) {
    is MatchFailureReason.ConnectionTimeout -> "Connection timed out"
    is MatchFailureReason.BluetoothUnavailable -> "Bluetooth is not available"
    is MatchFailureReason.PermissionDenied -> "Permissions not granted"
    is MatchFailureReason.TransportError -> detail
    is MatchFailureReason.Unknown -> detail
}
