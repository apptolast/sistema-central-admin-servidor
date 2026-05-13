package com.apptolast.ragingestor.embed

import com.apptolast.ragingestor.config.RagIngestorProperties
import com.apptolast.ragingestor.git.DocIngester
import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Chunkea markdown por secciones (## headers), embeddea cada chunk con
 * Spring AI EmbeddingModel (autoconfigurado vía OpenAI starter) y upserts
 * a pgvector como Document(content, metadata={path,section,sha}).
 *
 * Si VectorStore no está disponible (no OPENAI_API_KEY o no DataSource),
 * el ingester sólo chunkea y loguea — preserva la app en CI sin DB ni
 * clave OpenAI.
 */
@Component
class MarkdownDocIngester(
    private val properties: RagIngestorProperties,
    @Autowired(required = false) private val vectorStore: VectorStore? = null,
) : DocIngester {

    private val log = LoggerFactory.getLogger(MarkdownDocIngester::class.java)

    init {
        if (vectorStore == null) {
            log.warn(
                "VectorStore NOT configured — embeddings disabled. " +
                    "Set OPENAI_API_KEY + DB_URL/DB_USER/DB_PASSWORD to enable indexing.",
            )
        } else {
            log.info("VectorStore configured: {}", vectorStore::class.java.simpleName)
        }
    }

    override fun ingest(relativePath: String, sha: String) {
        val absolute = Paths.get(properties.workdir, relativePath)
        if (!Files.exists(absolute)) {
            log.debug("file removed since fetch, soft-deleting chunks: {}", relativePath)
            softDeleteChunks(relativePath)
            return
        }

        val content = Files.readString(absolute)
        val chunks = chunkMarkdown(content, relativePath, sha)
        if (chunks.isEmpty()) {
            log.debug("no chunks produced for {}", relativePath)
            return
        }

        val store = vectorStore
        if (store == null) {
            log.info("dry-run ingested {} chunks from {} @ {} (no VectorStore)", chunks.size, relativePath, sha)
            return
        }

        val documents = chunks.map { chunk ->
            Document.builder()
                .text(chunk.body)
                .metadata(
                    mapOf(
                        "path" to chunk.path,
                        "section" to chunk.section,
                        "sha" to chunk.sha,
                        "model" to properties.embeddingModel,
                    ),
                )
                .build()
        }

        runCatching { store.add(documents) }
            .onSuccess {
                log.info("indexed {} chunks from {} @ {}", documents.size, relativePath, sha)
            }
            .onFailure { e ->
                log.error("failed to index {} @ {}: {}", relativePath, sha, e.message, e)
            }
    }

    /**
     * Borra todos los chunks indexados que vinieran del file [relativePath].
     * Usado cuando git pull detecta que el archivo fue eliminado o renombrado.
     *
     * pgvector se filtra por `metadata.path = relativePath`. Si VectorStore
     * no está configured (dev sin DB), es no-op con log.
     */
    private fun softDeleteChunks(relativePath: String) {
        val store = vectorStore ?: run {
            log.debug("no VectorStore, skipping soft-delete for {}", relativePath)
            return
        }
        runCatching {
            val filter = FilterExpressionBuilder().eq("path", relativePath).build()
            store.delete(filter)
        }.onSuccess {
            log.info("soft-deleted chunks for {} via metadata.path filter", relativePath)
        }.onFailure { e ->
            log.warn("soft-delete failed for {}: {}", relativePath, e.message)
        }
    }

    internal data class Chunk(
        val path: String,
        val section: String,
        val sha: String,
        val body: String,
    )

    internal fun chunkMarkdown(content: String, path: String, sha: String): List<Chunk> {
        val lines = content.lines()
        val chunks = mutableListOf<Chunk>()
        var currentSection = "intro"
        var buffer = StringBuilder()

        fun flush() {
            val body = buffer.toString().trim()
            if (body.isNotBlank()) {
                chunks += Chunk(path, slugify(currentSection), sha, body)
            }
            buffer = StringBuilder()
        }

        for (line in lines) {
            // H2/H3 inician nueva sección
            if (line.startsWith("## ")) {
                flush()
                currentSection = line.removePrefix("## ").trim()
            } else if (line.startsWith("### ")) {
                flush()
                currentSection = line.removePrefix("### ").trim()
            } else {
                buffer.appendLine(line)
            }
        }
        flush()

        return chunks
    }

    private fun slugify(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "untitled" }
}
