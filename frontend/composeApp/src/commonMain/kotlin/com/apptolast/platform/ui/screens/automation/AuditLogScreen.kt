package com.apptolast.platform.ui.screens.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.apptolast.platform.ui.data.AuditEntryDto
import com.apptolast.platform.ui.data.AuditPageDto
import com.apptolast.platform.ui.data.AutomationClient
import com.apptolast.platform.ui.theme.AppToLastColors

/**
 * Pantalla 6: Audit Log de comandos SafeOps (Wave-E E4).
 *
 * Renderiza GET /api/v1/automation/audit — paginación simple por ahora
 * (siguiente: cursor; backend ya soporta page/size hasta 200). Cada fila
 * lleva su outcome con badge color-coded:
 *   ACCEPTED_OK  → primary (neon green #00E676)
 *   ACCEPTED_FAIL → error
 *   REJECTED     → warning
 *   TIMED_OUT    → tertiary
 *
 * Anti-hallucination: si el endpoint falla, AutomationClient devuelve
 * página vacía y mostramos "Sin entries". NO mostramos un mensaje genérico
 * "Error de red" porque la UI no debe inventar estado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    client: AutomationClient = remember { AutomationClient(baseUrl = "http://localhost:8080") },
) {
    var state by remember { mutableStateOf<AuditLogUiState>(AuditLogUiState.Loading) }
    var page by remember { mutableStateOf(0) }
    val pageSize = 50

    LaunchedEffect(page) {
        state = AuditLogUiState.Loading
        val result: AuditPageDto = client.listAudit(page = page, size = pageSize)
        state = AuditLogUiState.Loaded(result)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Audit log") }) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                AuditLogUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is AuditLogUiState.Loaded -> LoadedView(s.page, onPrev = {
                    if (page > 0) page--
                }, onNext = {
                    if (s.page.items.size >= pageSize) page++
                })
            }
        }
    }
}

private sealed interface AuditLogUiState {
    data object Loading : AuditLogUiState
    data class Loaded(val page: AuditPageDto) : AuditLogUiState
}

@Composable
private fun LoadedView(page: AuditPageDto, onPrev: () -> Unit, onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (page.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Sin entries para la página ${page.page}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(page.items) { entry -> AuditRow(entry) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPrev, enabled = page.page > 0) { Text("← Anterior") }
            Text("Página ${page.page + 1}", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onNext, enabled = page.items.size >= page.size) { Text("Siguiente →") }
        }
    }
}

@Composable
private fun AuditRow(entry: AuditEntryDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutcomeBadge(entry.outcome)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.commandKind, style = MaterialTheme.typography.titleSmall)
                Text(
                    entry.executedAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.exitCode?.let { exit ->
                    val dur = entry.durationMs?.let { " · ${it}ms" }.orEmpty()
                    Text("exit=$exit$dur", style = MaterialTheme.typography.labelSmall)
                }
                entry.rejectionReason?.let { reason ->
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            entry.userId?.let { uid ->
                Text(uid, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OutcomeBadge(outcome: String) {
    val color = when (outcome) {
        "ACCEPTED_OK" -> MaterialTheme.colorScheme.primary
        "ACCEPTED_FAIL" -> MaterialTheme.colorScheme.error
        "REJECTED" -> AppToLastColors.PodPending
        "TIMED_OUT" -> MaterialTheme.colorScheme.tertiary
        else -> Color.Gray
    }
    Box(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            outcome,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black,
        )
    }
}
