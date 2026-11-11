package com.bluescope.ai.presentation.scanner

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluescope.ai.bluetooth.permission.BluetoothPermissionPolicy
import com.bluescope.ai.presentation.components.DeviceCard

@Composable
fun ScannerScreen(
    onDevice: (String) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(hasScanPermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionsGranted = result.values.all { it }
        if (permissionsGranted) viewModel.startScan()
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.clearError()
        viewModel.startScan()
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.stopScan() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Scanner", style = MaterialTheme.typography.headlineMedium)
        Text(
            "BLE puis Bluetooth Classic, pendant une durée limitée.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScanRadar(active = state.isScanning, modifier = Modifier.fillMaxWidth().height(120.dp))
        Text(
            text = when {
                state.isScanning && state.phase != null -> "Recherche ${state.phase!!.name} en cours…"
                state.isScanning -> "Initialisation du scan…"
                else -> "Prêt à rechercher à proximité"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.isScanning) {
            TextButton(onClick = viewModel::stopScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Close, contentDescription = null)
                Text(" Arrêter")
            }
        } else {
            Button(
                onClick = {
                    if (hasScanPermissions(context)) {
                        permissionsGranted = true
                        viewModel.startScan()
                    } else {
                        permissionLauncher.launch(BluetoothPermissionPolicy.scanPermissions().toTypedArray())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (permissionsGranted) "Lancer le scan" else "Autoriser et scanner")
            }
        }
        state.error?.let { error ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    when {
                        error.contains("autorisation", ignoreCase = true) -> {
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        "package:${context.packageName}".toUri(),
                                    ),
                                )
                            }) { Text("Ouvrir les réglages de l’application") }
                        }
                        error.contains("désactivé", ignoreCase = true) -> {
                            TextButton(onClick = {
                                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }) { Text("Activer le Bluetooth") }
                        }
                    }
                    TextButton(onClick = viewModel::clearError) { Text("Fermer") }
                }
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Rechercher dans les résultats") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
        )
        Text("${state.filteredDevices.size} périphérique(s)", style = MaterialTheme.typography.labelLarge)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            items(state.filteredDevices, key = { it.id.value }) { device ->
                DeviceCard(device, onClick = { onDevice(device.id.value) })
            }
        }
    }
}

@Composable
private fun ScanRadar(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "radar")
    val progress by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_800), RepeatMode.Restart),
        label = "onde de scan",
    )
    val color = MaterialTheme.colorScheme.primary
    Box(modifier.semantics { contentDescription = if (active) "Scan en cours" else "Scanner inactif" }) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f
            repeat(3) { index ->
                val phase = if (active) (progress + index / 3f) % 1f else (index + 1) / 4f
                drawCircle(
                    color = color.copy(alpha = (1f - phase) * 0.55f),
                    radius = maxRadius * phase,
                    center = center,
                    style = Stroke(width = 4f),
                )
            }
            drawCircle(color = color, radius = 8.dp.toPx(), center = center)
        }
    }
}

private fun hasScanPermissions(context: android.content.Context): Boolean =
    BluetoothPermissionPolicy.scanPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
