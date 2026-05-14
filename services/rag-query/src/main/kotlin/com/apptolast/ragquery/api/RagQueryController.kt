package com.apptolast.ragquery.api

import com.apptolast.ragquery.application.port.inbound.QueryKnowledgeUseCase
import com.apptolast.ragquery.domain.QueryAnswer
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rag")
class RagQueryController(
    private val useCase: QueryKnowledgeUseCase,
) {

    @PostMapping("/query")
    fun query(@RequestBody body: QueryRequest): QueryResponse {
        val answer = useCase.query(body.question, body.topK)
        return QueryResponse.from(answer)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(e: IllegalArgumentException): Map<String, String> =
        mapOf("error" to (e.message ?: "invalid request"))
}

data class QueryRequest(
    @field:NotBlank val question: String,
    val topK: Int? = null,
)

data class CitationDto(val path: String, val section: String, val sha: String, val cite: String)

data class LegacyCitationDto(val sourcePath: String, val section: String, val sha: String)

data class ChunkDto(val content: String, val score: Double, val citation: CitationDto)

data class QueryResponse(
    val question: String,
    val confidence: String,
    val status: String,
    val body: String?,
    val chunks: List<ChunkDto>,
    val citations: List<LegacyCitationDto>,
    val warning: String?,
) {
    companion object {
        fun from(answer: QueryAnswer): QueryResponse {
            val warning = when (answer.confidence) {
                QueryAnswer.Confidence.LOW_NO_EVIDENCE ->
                    "No encuentro evidencia documentada suficiente. Revisa runbooks o pide ayuda humana antes de actuar."
                QueryAnswer.Confidence.HIGH -> null
            }
            return QueryResponse(
                question = answer.question,
                confidence = answer.confidence.name,
                status = when (answer.confidence) {
                    QueryAnswer.Confidence.HIGH -> "CITED"
                    QueryAnswer.Confidence.LOW_NO_EVIDENCE -> "LOW_NO_EVIDENCE"
                },
                body = answer.evidenceBody(),
                chunks = answer.chunks.map {
                    ChunkDto(
                        content = it.content,
                        score = it.score,
                        citation = CitationDto(
                            path = it.citation.path,
                            section = it.citation.section,
                            sha = it.citation.sha,
                            cite = it.citation.toString(),
                        ),
                    )
                },
                citations = answer.chunks.map {
                    LegacyCitationDto(
                        sourcePath = it.citation.path,
                        section = it.citation.section,
                        sha = it.citation.sha,
                    )
                },
                warning = warning,
            )
        }

        private fun QueryAnswer.evidenceBody(): String? =
            when (confidence) {
                QueryAnswer.Confidence.HIGH ->
                    chunks
                        .map { it.content.trim() }
                        .filter { it.isNotBlank() }
                        .joinToString(separator = "\n\n")
                        .takeIf { it.isNotBlank() }
                QueryAnswer.Confidence.LOW_NO_EVIDENCE -> null
            }
    }
}
