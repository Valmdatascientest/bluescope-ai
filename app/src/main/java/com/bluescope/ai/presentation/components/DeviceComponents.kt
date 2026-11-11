package com.bluescope.ai.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bluescope.ai.designsystem.components.SignalIndicator
import com.bluescope.ai.designsystem.components.StatusPill
import com.bluescope.ai.domain.model.BluetoothDeviceSnapshot
import com.bluescope.ai.domain.model.KnownDevice
import java.time.Duration
import java.time.Instant

@Composable
fun DeviceCard(
    snapshot: BluetoothDeviceSnapshot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    category: String? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = snapshot.displayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (favorite) {
                        Icon(
                            Icons.Outlined.Star,
                            contentDescription = "Favori",
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                Text(
                    text = listOfNotNull(
                        category,
                        snapshot.transport.name,
                        snapshot.address?.redacted(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Vu ${snapshot.lastSeenAt.relativeLabel()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SignalIndicator(snapshot.rssi)
                snapshot.rssi?.let {
                    Text("$it dBm", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun KnownDeviceCard(device: KnownDevice, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DeviceCard(
        snapshot = device.snapshot,
        onClick = onClick,
        modifier = modifier,
        favorite = device.isFavorite,
        category = device.latestAnalysis?.category?.name
            ?: device.snapshot.transport.name,
    )
}

internal fun Instant.relativeLabel(now: Instant = Instant.now()): String {
    val duration = Duration.between(this, now).coerceAtLeast(Duration.ZERO)
    return when {
        duration.seconds < 10 -> "à l’instant"
        duration.toMinutes() < 1 -> "il y a ${duration.seconds} s"
        duration.toHours() < 1 -> "il y a ${duration.toMinutes()} min"
        duration.toDays() < 1 -> "il y a ${duration.toHours()} h"
        else -> "il y a ${duration.toDays()} j"
    }
}
