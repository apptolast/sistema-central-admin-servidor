package com.apptolast.platform.knowledge.domain.model

/**
 * Respuesta de una query RAG.
 *
 * Garantía de tipo: si [body] tiene texto, [citations] no está vacío. Caso
 * vacío reservado a [NoEvidence].
 */
sealed interface Answer {
    val query: String

    data class Cited(
        override val query: String,
        val body: String,
        val citations: List<Citation>,
        val confidence: Double,
    ) : Answer {
        init {
            require(body.isNotBlank()) { "body must not be blank — use NoEvidence" }
            require(citations.isNotEmpty()) { "Cited answer must have at least 1 citation" }
            require(confidence in 0.0..1.0)
        }
    }

    data class NoEvidence(
        override val query: String,
        val searchedSources: Int,
        val maxScoreFound: Double,
    ) : Answer {
        val message: String = "No encuentro evidencia documentada para responder. " +
            "Considera documentar este caso en docs/ y reintentar."
    }
}
