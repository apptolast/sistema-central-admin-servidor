package com.apptolast.platform.knowledge.infrastructure

import com.apptolast.platform.knowledge.application.port.inbound.QueryKnowledgePort
import com.apptolast.platform.knowledge.domain.model.Citation
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Implementación de [QueryKnowledgePort] que delega en el microservicio
 * `rag-query` (POST `/api/v1/rag/query`).
 *
 * **Anti-hallucination contract**: si la llamada falla por *cualquier*
 * motivo (timeout, 4xx, 5xx, parse error), devuelve `emptyList()`. Nunca
 * propaga la excepción al caller. Para el caller (inventory, automation,
 * etc.) la diferencia entre "no encontré nada" y "el servicio está caído"
 * es irrelevante: en ambos casos el módulo de negocio sigue funcionando
 * sin enriquecer la respuesta con runbooks.
 */
class RestKnowledgeClient(
    private val restClient: RestClient,
    private val properties: KnowledgeProperties,
) : QueryKnowledgePort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun query(question: String, topK: Int): List<Citation> {
        if (question.isBlank()) return emptyList()
        return try {
            val response = restClient.post()
                .uri("/api/v1/rag/query")
                .contentType(MediaType.APPLICATION_JSON)
                .body(QueryRequest(question = question, topK = topK, minScore = properties.minScore))
                .retrieve()
                .body(QueryResponse::class.java)

            response?.toCitations() ?: emptyList()
        } catch (ex: RestClientException) {
            log.warn("rag-query unreachable, returning empty citations (anti-hallucination policy): {}", ex.message)
            emptyList()
        } catch (ex: IllegalArgumentException) {
            // Citation.init rechazó un payload malformado del servidor.
            log.warn("rag-query returned malformed citations, returning empty: {}", ex.message)
            emptyList()
        }
    }

    internal data class QueryRequest(
        val question: String,
        val topK: Int,
        val minScore: Double,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class QueryResponse(
        val status: String? = null,
        val citations: List<CitationDto> = emptyList(),
    ) {
        fun toCitations(): List<Citation> =
            if (status == "LOW_NO_EVIDENCE") emptyList()
            else citations.mapNotNull { it.toDomainOrNull() }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class CitationDto(
        val sourcePath: String?,
        val section: String?,
        val sha: String?,
    ) {
        fun toDomainOrNull(): Citation? = try {
            Citation(
                sourcePath = sourcePath ?: return null,
                section = section ?: return null,
                sha = sha ?: return null,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
