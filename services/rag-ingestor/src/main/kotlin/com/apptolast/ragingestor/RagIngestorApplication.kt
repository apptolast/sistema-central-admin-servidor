package com.apptolast.ragingestor

import com.apptolast.ragingestor.config.RagIngestorProperties
import com.apptolast.ragingestor.git.GitRepoPoller
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import kotlin.system.exitProcess

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
@EnableConfigurationProperties(RagIngestorProperties::class)
class RagIngestorApplication {

    @Bean
    fun runOnce(
        properties: RagIngestorProperties,
        poller: GitRepoPoller,
        context: ConfigurableApplicationContext,
    ): ApplicationRunner = ApplicationRunner { _: ApplicationArguments ->
        if (!properties.runOnce) return@ApplicationRunner

        val log = LoggerFactory.getLogger(RagIngestorApplication::class.java)
        val exitCode = runCatching {
            poller.pollAndIngest()
        }.fold(
            onSuccess = { 0 },
            onFailure = { error ->
                log.error("RAG run-once ingest failed: {}", error.message, error)
                1
            },
        )
        exitProcess(SpringApplication.exit(context, ExitCodeGenerator { exitCode }))
    }
}

fun main(args: Array<String>) {
    runApplication<RagIngestorApplication>(*args)
}
