package com.apptolast.ragingestor.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "rag-ingestor")
data class RagIngestorProperties(
    /** Repo a indexar. URL HTTPS o ssh. */
    val repoUrl: String = "https://github.com/apptolast/sistema-central-admin-servidor.git",

    /** Branch a seguir. */
    val branch: String = "main",

    /** Token HTTPS opcional para repos privados. Nunca se registra en logs. */
    val repoToken: String? = null,

    /** Directorio local donde se clona el repo (dentro del pod). */
    val workdir: String = "/var/lib/rag-ingestor/repo",

    /** Glob patterns a indexar. */
    val includePaths: List<String> = listOf("docs/**/*.md", "cluster-ops/audit/RUNBOOKS/**/*.md", "*.md"),

    /** Excluye estos paths del índice. */
    val excludePaths: List<String> = listOf("docs/design/**", "node_modules/**", "build/**"),

    /** Periodo de poll. */
    val pollInterval: Duration = Duration.ofMinutes(5),

    /** Tamaño del chunk en tokens. */
    val chunkTokens: Int = 512,

    /** Solapamiento entre chunks. */
    val chunkOverlapTokens: Int = 64,

    /** Modelo de embedding. */
    val embeddingModel: String = "text-embedding-3-large",

    /** Ejecuta un ciclo y termina el proceso. Pensado para Kubernetes CronJob. */
    val runOnce: Boolean = false,

    /** Activa el scheduler interno para modo servicio long-running. */
    val schedulingEnabled: Boolean = true,
)
