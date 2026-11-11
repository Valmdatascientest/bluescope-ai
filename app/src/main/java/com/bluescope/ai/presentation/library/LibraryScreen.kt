package com.bluescope.ai.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluescope.ai.designsystem.components.SectionCard
import com.bluescope.ai.presentation.components.KnownDeviceCard

@Composable
fun LibraryScreen(
    onDevice: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Bibliothèque", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Vos périphériques restent consultables sans nouveau scan.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = filter.text,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nom, constructeur ou catégorie") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
        )
        FilterChip(
            selected = filter.favoritesOnly,
            onClick = viewModel::toggleFavorites,
            label = { Text("Favoris uniquement") },
        )
        Text("${devices.size} résultat(s)", style = MaterialTheme.typography.labelLarge)
        if (devices.isEmpty()) {
            SectionCard("Aucun résultat") {
                Text("Modifiez la recherche ou lancez un scan.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(devices, key = { it.snapshot.id.value }) { device ->
                    KnownDeviceCard(device, onClick = { onDevice(device.snapshot.id.value) })
                }
            }
        }
    }
}
