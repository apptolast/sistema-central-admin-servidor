package com.apptolast.platform.ui.screens.rag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
 * Pantalla 4: RAG Query UI.
 *
 * Spec: docs/design/specs/04-rag-query-ui.md
 *   - Chat estilo Perplexity con preguntas a la plataforma
 *   - Cada respuesta lleva citation badges clickables que abren el runbook fuente
 *   - Bandeja "no encuentro evidencia documentada" cuando score < 0.6
 *     (regla anti-alucinación de [[feedback_rag_anti_hallucination]])
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagQueryScreen() {
    var query by remember { mutableStateOf("") }
    val conversation = remember {
        mutableStateOf<List<RagMessage>>(
            listOf(
                RagMessage.Assistant(
                    text = "Hola. Pregúntame sobre el cluster, los runbooks o cualquier sistema desplegado.",
                    citations = emptyList(),
                ),
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pregunta a la plataforma") })
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(conversation.value) { msg -> MessageRow(msg) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("¿Por qué falló el último despliegue de inventory?") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    // TODO(phase-3): conectar con rag-query-service
                    if (query.isNotBlank()) {
                        conversation.value = conversation.value + RagMessage.User(query)
                        conversation.value = conversation.value + RagMessage.Assistant(
                            text = "(stub Phase 1) Esta función llega en Phase 3 (Knowledge RAG).",
                            citations = emptyList(),
                        )
                        query = ""
                    }
                }) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Enviar")
                }
            }
        }
    }
}

private sealed interface RagMessage {
    val text: String

    data class User(override val text: String) : RagMessage
    data class Assistant(override val text: String, val citations: List<Citation>) : RagMessage

    data class Citation(val source: String, val section: String, val sha: String)
}

@Composable
private fun MessageRow(msg: RagMessage) {
    when (msg) {
        is RagMessage.User -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Text(
                msg.text,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        is RagMessage.Assistant -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(msg.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (msg.citations.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        msg.citations.forEach { c ->
                            AssistChip(
                                onClick = { /* TODO: open runbook viewer at section */ },
                                label = { Text("${c.source}#${c.section}") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
