package com.apptolast.ragquery.application.port.inbound

import com.apptolast.ragquery.domain.QueryAnswer

interface QueryKnowledgeUseCase {
    fun query(question: String, topK: Int? = null): QueryAnswer
}
