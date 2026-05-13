package com.apptolast.ragquery.domain

data class Citation(
    val path: String,
    val section: String,
    val sha: String,
) {
    override fun toString(): String = "[source: $path#$section@$sha]"
}

data class RetrievedChunk(
    val content: String,
    val citation: Citation,
    val score: Double,
)

data class QueryAnswer(
    val question: String,
    val chunks: List<RetrievedChunk>,
    val confidence: Confidence,
) {
    enum class Confidence { HIGH, LOW_NO_EVIDENCE }
}
