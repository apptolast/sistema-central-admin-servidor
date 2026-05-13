package com.apptolast.platform.ui.screens.runbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Pantalla 3: Runbook Viewer.
 *
 * Spec: docs/design/specs/03-runbook-viewer.md
 *   - 3 columnas: tree de los 27 runbooks · markdown render · panel "afecta a"
 *   - Citation links → abren la pantalla 4 (RAG) o navegan dentro del propio runbook
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunbookViewerScreen() {
    var selected by remember { mutableStateOf(runbooks.first()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Runbooks · ${runbooks.size}") }) }) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.width(320.dp).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(runbooks) { rb ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (rb == selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                        onClick = { selected = rb },
                    ) {
                        Text(rb, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.width(1.dp),
                color = MaterialTheme.colorScheme.outline,
            )
            Column(modifier = Modifier.weight(1f).padding(24.dp)) {
                Text(selected, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Stub Phase 1 — el render markdown llega cuando integremos el módulo " +
                        "knowledge (Phase 3). Los 27 runbooks viven en cluster-ops/audit/RUNBOOKS/.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val runbooks = listOf(
    "RB-01 HOST_DISK_HIGH",
    "RB-02 HOST_RAM_HIGH",
    "RB-03 SWAP_USE",
    "RB-04 LOAD_HIGH",
    "RB-06 KUBEADM_CERT_EXPIRY",
    "RB-10 PG_CONNECTIONS_HIGH",
    "RB-13 PG_TXID_WRAPAROUND",
    "RB-16 EMQX_CLIENTS_DROP",
    "RB-17 LONGHORN_DEGRADED",
    "RB-18 CERT_EXPIRY",
    "RB-19 TIER0_CRASHLOOP",
    "RB-20 OOMKILLED_REPEAT",
    "RB-21 PVC_GROWTH_ANOMALY",
    "RB-22 WIREGUARD_HANDSHAKE_STALE",
    "RB-23 TIER0_UNAUTH_ACCESS",
    "RB-24 DASHBOARD_OUTAGE",
    "RB-25 ROUTINE_FAILED",
    "RB-26 TEAM_COORDINATION_STUCK",
    "RB-27 LOG_HYGIENE_FAILED",
    "EMQX_PVC_EXPANSION_PLAN",
    "EMQX_PVC_HIGH",
    "LONGHORN_BACKUP_TARGET",
    "TIER0_AUTO_UPDATERS",
    "TIMESCALEDB_INVERNADEROS_RETENTION_1Y",
)
