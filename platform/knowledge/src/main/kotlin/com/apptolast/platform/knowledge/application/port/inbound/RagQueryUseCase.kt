package com.apptolast.platform.knowledge.application.port.inbound

import com.apptolast.platform.knowledge.domain.model.Answer

interface RagQueryUseCase {
    fun ask(query: String, options: QueryOptions = QueryOptions()): Answer
}

data class QueryOptions(
    /** Mínimo score de retrieval para no devolver NoEvidence. Default 0.6. */
    val minScore: Double = 0.6,

    /** Máximo de chunks a recuperar. */
    val topK: Int = 8,

    /** Si true, restringe búsqueda a estos paths. Vacío = todo el corpus. */
    val pathPrefixes: List<String> = emptyList(),
)
