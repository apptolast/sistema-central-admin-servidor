package com.apptolast.ragquery.application.port.outbound

import com.apptolast.ragquery.domain.RetrievedChunk

interface VectorSearchPort {
    fun search(question: String, topK: Int): List<RetrievedChunk>
}
