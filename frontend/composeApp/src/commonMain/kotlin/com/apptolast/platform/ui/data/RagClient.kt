package com.apptolast.platform.ui.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cliente directo al servicio rag-query (puerto 8082 internamente, expuesto
 * por Traefik en /api/v1/rag/[recurso] del dominio idp.apptolast.com).
 *
 * Anti-hallucination: si la llamada falla, devuelve [RagAnswer.NoEvidence]
 * con el mensaje canonical "no encuentro evidencia documentada". NUNCA
 * inventa una respuesta.
 */
class RagClient(private val baseUrl: String) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    suspend fun ask(question: String, topK: Int = 5): RagAnswer {
        if (question.isBlank()) return RagAnswer.NoEvidence
        return try {
            val response: RagQueryResponse = client.post("$baseUrl/api/v1/rag/query") {
                contentType(ContentType.Application.Json)
                setBody(RagQueryRequest(question = question, topK = topK))
            }.body()
            when (response.status) {
                "LOW_NO_EVIDENCE" -> RagAnswer.NoEvidence
                "CITED" -> RagAnswer.Cited(
                    body = response.body ?: "(respuesta sin cuerpo)",
                    citations = response.citations.mapNotNull { it.toDomain() },
                )
                else -> RagAnswer.NoEvidence
            }
        } catch (_: Throwable) {
            RagAnswer.NoEvidence
        }
    }
}

@Serializable
private data class RagQueryRequest(
    val question: String,
    val topK: Int,
    val minScore: Double = 0.6,
)

@Serializable
private data class RagQueryResponse(
    val status: String? = null,
    val body: String? = null,
    val citations: List<CitationPayload> = emptyList(),
)

@Serializable
private data class CitationPayload(
    val sourcePath: String? = null,
    val section: String? = null,
    val sha: String? = null,
) {
    fun toDomain(): RagCitation? {
        val p = sourcePath ?: return null
        val s = section ?: return null
        val h = sha ?: return null
        return RagCitation(p, s, h)
    }
}

sealed interface RagAnswer {
    data class Cited(val body: String, val citations: List<RagCitation>) : RagAnswer
    data object NoEvidence : RagAnswer {
        const val MESSAGE = "No encuentro evidencia documentada para responder."
    }
}

data class RagCitation(
    val sourcePath: String,
    val section: String,
    val sha: String,
) {
    fun toMarkdown(): String = "[source: $sourcePath#$section@$sha]"
}
