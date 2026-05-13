package com.apptolast.platform.ui.screens.cronjobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Pantalla 5: Cronjob Board.
 *
 * Spec: docs/design/specs/05-cronjob-board.md
 *   - Grid de los 18 cluster-ops cronjobs + 3 host cronjobs + workflows n8n activos
 *   - Último estado (Success / Failed / Running)
 *   - Próxima ejecución (cron next-fire calc)
 *   - Botón "trigger ahora" (Phase 5 — necesita endpoint en backend)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronjobBoardScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Cronjobs · ${cronjobs.size}") }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cronjobs) { cj -> CronjobCard(cj) }
        }
    }
}

private data class CronJob(
    val namespace: String,
    val name: String,
    val schedule: String,
    val lastStatus: String,
    val nextRun: String,
)

private val cronjobs = listOf(
    CronJob("cluster-ops", "cluster-self-healing", "*/30 * * * *", "Success", "in 4 min"),
    CronJob("cluster-ops", "cert-checks", "0 */6 * * *", "Success", "in 2h"),
    CronJob("cluster-ops", "host-checks", "*/5 * * * *", "Success", "in 2 min"),
    CronJob("cluster-ops", "longhorn-checks", "*/10 * * * *", "Success", "in 7 min"),
    CronJob("cluster-ops", "log-hygiene", "0 4 * * 0", "Success", "Sun 04:00"),
    CronJob("cluster-ops", "infra-version-watch", "0 9 * * *", "Success", "tomorrow 09:00"),
    CronJob("cluster-ops", "latest-images-rotator", "30 3 * * *", "Success", "tomorrow 03:30"),
    CronJob("cluster-ops", "tier0-traffic-sentinel", "0 * * * *", "Success", "in 12 min"),
    CronJob("n8n", "postgres-backup", "0 2 * * *", "Success", "tomorrow 02:00"),
    CronJob("passbolt", "passbolt-backup", "0 0,12 * * *", "Success", "in 6h"),
)

@Composable
private fun CronjobCard(cj: CronJob) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cj.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { /* TODO Phase 5: trigger ahora */ }) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Ejecutar ahora")
                }
            }
            Text(cj.namespace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(cj.schedule, style = MaterialTheme.typography.bodyMedium)
            Text("Last: ${cj.lastStatus}  ·  Next: ${cj.nextRun}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
