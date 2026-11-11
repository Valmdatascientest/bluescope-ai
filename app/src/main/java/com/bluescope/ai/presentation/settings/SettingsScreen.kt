package com.bluescope.ai.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluescope.ai.designsystem.components.SectionCard
import com.bluescope.ai.domain.model.ThemeMode
import java.time.LocalDate

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val exportJson by viewModel.exportJson.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val latestExport by rememberUpdatedState(exportJson)
    var confirmDelete by remember { mutableStateOf(false) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = latestExport
        if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
            }
        }
        viewModel.exportConsumed()
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readUtf8Bounded(5 * 1024 * 1024 + 1)
            }
        }.getOrNull()?.let(viewModel::importJson)
    }
    LaunchedEffect(exportJson) {
        if (exportJson != null) {
            createDocument.launch("bluescope-${LocalDate.now()}.json")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Paramètres", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Confidentialité locale et comportement du scanner.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message?.let { value ->
            item {
                SectionCard("Information") {
                    Text(value)
                    TextButton(onClick = viewModel::clearMessage) { Text("Fermer") }
                }
            }
        }
        item {
            SectionCard("Apparence") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { theme ->
                        FilterChip(
                            selected = preferences.themeMode == theme,
                            onClick = { viewModel.setTheme(theme) },
                            label = {
                                Text(
                                    when (theme) {
                                        ThemeMode.SYSTEM -> "Système"
                                        ThemeMode.LIGHT -> "Clair"
                                        ThemeMode.DARK -> "Sombre"
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
        item {
            SectionCard("Scanner") {
                SettingSwitch(
                    title = "Inclure Bluetooth Classic",
                    subtitle = "La découverte Classic est exécutée après le scan BLE.",
                    checked = preferences.includeClassic,
                    onCheckedChange = viewModel::setClassic,
                )
                SettingSwitch(
                    title = "Afficher les appareils appairés",
                    subtitle = "Ils sont marqués comme données en cache, pas comme proches.",
                    checked = preferences.includeBonded,
                    onCheckedChange = viewModel::setBonded,
                )
                Text("Durée : ${preferences.scanDuration.seconds} secondes")
                Slider(
                    value = preferences.scanDuration.seconds.toFloat(),
                    onValueChangeFinished = {},
                    onValueChange = { viewModel.setScanDuration(it.toInt()) },
                    valueRange = 5f..60f,
                    steps = 10,
                )
            }
        }
        item {
            SectionCard("Intelligence artificielle") {
                SettingSwitch(
                    title = "Moteur local",
                    subtitle = "Toujours actif. Aucune donnée ne quitte l’appareil.",
                    checked = true,
                    enabled = false,
                    onCheckedChange = {},
                )
                SettingSwitch(
                    title = "Service IA distant",
                    subtitle = "Non configuré. Une prévisualisation et un consentement seront requis.",
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                )
            }
        }
        item {
            SectionCard("Données") {
                Text(
                    "L’export masque les adresses et n’inclut aucun payload brut.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = viewModel::prepareExport, modifier = Modifier.fillMaxWidth()) {
                    Text("Exporter en JSON")
                }
                OutlinedButton(
                    onClick = { openDocument.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Importer un JSON contrôlé") }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Supprimer toutes les données", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            SectionCard("Confidentialité") {
                Text(
                    "Les identifiants réutilisables sont chiffrés par Android Keystore. " +
                        "Les sauvegardes système sont désactivées et aucune clé API n’est embarquée.",
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Effacer toute la bibliothèque ?") },
            text = { Text("Cette action supprime les périphériques, observations et analyses locales.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    confirmDelete = false
                }) { Text("Tout supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

private fun java.io.InputStream.readUtf8Bounded(maxBytes: Int): String {
    val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
    val buffer = ByteArray(8 * 1024)
    var remaining = maxBytes
    while (remaining > 0) {
        val count = read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        output.write(buffer, 0, count)
        remaining -= count
    }
    return output.toByteArray().toString(Charsets.UTF_8)
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
