package com.bluescope.ai.presentation.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluescope.ai.designsystem.components.ConfidencePill
import com.bluescope.ai.designsystem.components.SectionCard
import com.bluescope.ai.designsystem.components.SignalIndicator
import com.bluescope.ai.designsystem.components.StatusPill
import com.bluescope.ai.domain.model.ClaimStatus

@Composable
fun DeviceDetailScreen(
    onBack: () -> Unit,
    onGatt: (String) -> Unit,
    onAnalysis: (String) -> Unit,
    viewModel: DeviceDetailViewModel = hiltViewModel(),
) {
    val known by viewModel.device.collectAsStateWithLifecycle()
    val device = known
    var note by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(device?.note) { note = device?.note.orEmpty() }

    if (device == null) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        return
    }

    val snapshot = device.snapshot
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                }
                Column(Modifier.weight(1f)) {
                    Text(snapshot.displayName, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        snapshot.address?.redacted() ?: "Identifiant non exposé",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        if (device.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (device.isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item {
            SectionCard("Identité et statut") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(snapshot.transport.name)
                        StatusPill(if (snapshot.freshness.name == "LIVE") "Vérifié maintenant" else "Données en cache")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        SignalIndicator(snapshot.rssi)
                        Text(snapshot.rssi?.let { "$it dBm" } ?: "RSSI inconnu")
                    }
                }
                Text("Appairage : ${snapshot.bondState.name}")
                Text("Services annoncés : ${snapshot.serviceUuids.size}")
            }
        }
        item {
            SectionCard("Analyse locale") {
                val analysis = device.latestAnalysis
                if (analysis == null) {
                    Text("Aucune analyse enregistrée.")
                    Button(onClick = viewModel::refreshAnalysis) { Text("Analyser hors ligne") }
                } else {
                    Text(analysis.summary)
                    ConfidencePill(analysis.confidence.value, analysis.confirmedClaims.isNotEmpty())
                    Text(
                        "${analysis.confirmedClaims.size} confirmé(s) · ${analysis.inferredClaims.size} hypothèse(s)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onAnalysis(snapshot.id.value) }) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Text(" Voir les preuves")
                    }
                }
            }
        }
        item {
            SectionCard("Exploration active") {
                Text(
                    "Une confirmation distincte est demandée avant toute connexion GATT. Aucune écriture n’est effectuée.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { onGatt(snapshot.id.value) }) {
                    Icon(Icons.Outlined.Hub, contentDescription = null)
                    Text(" Explorer les services")
                }
            }
        }
        item {
            SectionCard("Note personnelle") {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(4_000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note") },
                    minLines = 2,
                )
                Button(onClick = { viewModel.setNote(note) }) { Text("Enregistrer") }
            }
        }
        item {
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Text(" Supprimer de la bibliothèque")
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce périphérique ?") },
            text = { Text("Ses observations, analyses et services enregistrés seront supprimés.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete()
                    confirmDelete = false
                    onBack()
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}
