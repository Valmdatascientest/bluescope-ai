package com.bluescope.ai.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluescope.ai.designsystem.components.Metric
import com.bluescope.ai.designsystem.components.SectionCard
import com.bluescope.ai.presentation.components.KnownDeviceCard

@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onDevice: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val favorites = devices.count { it.isFavorite }
    val confirmed = devices.count { it.latestAnalysis?.confirmedClaims?.isNotEmpty() == true }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("Bonjour", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Votre environnement Bluetooth", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Analyse locale, preuves techniques et historique privé.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.BluetoothSearching, contentDescription = null)
                Text("  Lancer un scan intelligent")
            }
        }
        item {
            SectionCard("Vue d’ensemble") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric(devices.size.toString(), "connus", Modifier.weight(1f))
                    Metric(favorites.toString(), "favoris", Modifier.weight(1f))
                    Metric(confirmed.toString(), "analysés", Modifier.weight(1f))
                }
            }
        }
        item {
            Text("Découvertes récentes", style = MaterialTheme.typography.titleLarge)
        }
        if (devices.isEmpty()) {
            item {
                SectionCard("Aucun périphérique enregistré") {
                    Text(
                        "Lancez votre premier scan. Les résultats resteront disponibles hors ligne.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(devices.take(6), key = { it.snapshot.id.value }) { device ->
                KnownDeviceCard(device, onClick = { onDevice(device.snapshot.id.value) })
            }
        }
    }
}
