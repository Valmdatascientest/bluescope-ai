package com.bluescope.ai.presentation.analysis

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluescope.ai.designsystem.components.ConfidencePill
import com.bluescope.ai.designsystem.components.SectionCard
import com.bluescope.ai.domain.model.ClaimStatus
import com.bluescope.ai.presentation.device.DeviceDetailViewModel

@Composable
fun AnalysisScreen(
    onBack: () -> Unit,
    viewModel: DeviceDetailViewModel = hiltViewModel(),
) {
    val device by viewModel.device.collectAsStateWithLifecycle()
    val analysis = device?.latestAnalysis
    if (device == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }
        return
    }
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
                Column {
                    Text("Analyse IA locale", style = MaterialTheme.typography.headlineMedium)
                    Text(device!!.snapshot.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (analysis == null) {
            item {
                SectionCard("Analyse indisponible") {
                    Text("Lancez l’analyse depuis la fiche du périphérique.")
                }
            }
        } else {
            item {
                SectionCard("Résumé") {
                    Text(analysis.summary)
                    ConfidencePill(analysis.confidence.value, analysis.confirmedClaims.isNotEmpty())
                    Text("Moteur : ${analysis.engineVersion}", style = MaterialTheme.typography.bodySmall)
                }
            }
            items(analysis.claims, key = { "${it.status}-${it.title}" }) { claim ->
                SectionCard(claim.title) {
                    ConfidencePill(claim.confidence.value, claim.status == ClaimStatus.CONFIRMED)
                    Text(claim.explanation)
                    Text("Preuves techniques", style = MaterialTheme.typography.titleSmall)
                    claim.evidence.forEach { evidence ->
                        Text(
                            "• ${evidence.type.name} — ${evidence.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
