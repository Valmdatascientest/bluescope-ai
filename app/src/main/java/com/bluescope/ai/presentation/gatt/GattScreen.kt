package com.bluescope.ai.presentation.gatt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluescope.ai.designsystem.components.SectionCard
import com.bluescope.ai.designsystem.components.StatusPill
import com.bluescope.ai.domain.model.GattServiceInfo

@Composable
fun GattScreen(
    onBack: () -> Unit,
    viewModel: GattViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        onDispose { viewModel.cancel() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                }
                Column(Modifier.weight(1f)) {
                    Text("Explorateur GATT", style = MaterialTheme.typography.headlineMedium)
                    Text(state.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.isExploring) CircularProgressIndicator()
            }
        }
        state.error?.let { error ->
            item {
                SectionCard("Exploration interrompue") {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (!state.needsConsent && !state.isExploring && state.services.isEmpty() && state.error == null) {
            item { Text("Aucun service accessible n’a été découvert.") }
        }
        items(state.services, key = { it.uuid.toString() }) { service ->
            GattServiceCard(service)
        }
        if (state.isExploring) {
            item {
                TextButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Annuler et déconnecter")
                }
            }
        }
    }

    if (state.needsConsent) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Autoriser l’exploration ?") },
            text = {
                Text(
                    "BlueScope va se connecter brièvement et découvrir les services BLE. " +
                        "Aucune écriture, commande ou souscription ne sera effectuée.",
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    Button(onClick = { viewModel.explore(readValues = false) }) {
                        Text("Découvrir seulement")
                    }
                    TextButton(onClick = { viewModel.explore(readValues = true) }) {
                        Text("Découvrir et lire les valeurs autorisées")
                    }
                }
            },
            dismissButton = { TextButton(onClick = onBack) { Text("Annuler") } },
        )
    }
}

@Composable
private fun GattServiceCard(service: GattServiceInfo) {
    var expanded by remember(service.uuid) { mutableStateOf(false) }
    SectionCard(
        title = service.uuid.toString().uppercase(),
        trailing = {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Réduire" else "Développer",
                )
            }
        },
    ) {
        Text("${service.type.name} · ${service.characteristics.size} caractéristique(s)")
        if (expanded) {
            service.characteristics.forEach { characteristic ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(characteristic.uuid.toString().uppercase(), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        characteristic.properties.take(4).forEach { StatusPill(it.name) }
                    }
                    Text(
                        "Lecture : ${characteristic.readStatus.name} · ${characteristic.descriptors.size} descripteur(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
