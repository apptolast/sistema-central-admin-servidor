package com.apptolast.ragingestor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * RAG Ingestor — microservicio que mantiene el índice vectorial actualizado.
 *
 * Loop principal (cada 5 min, configurable):
 *  1. `git fetch` + diff vs último ingestSha
 *  2. Para cada doc nuevo/modificado: chunk, embed, upsert en pgvector
 *  3. Para cada doc eliminado: soft delete (mantiene history para citation@sha)
 *  4. Commit `ingestSha` actual en tabla `rag_ingest_state`
 *
 * Anti-alucinación: el chunk se persiste con (path, section, sha) origen, de
 * modo que el rag-query-service pueda devolver citations verificables.
 */
@SpringBootApplication
@EnableScheduling
class RagIngestorApplication

fun main(args: Array<String>) {
    runApplication<RagIngestorApplication>(*args)
}
