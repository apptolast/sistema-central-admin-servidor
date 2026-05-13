package com.apptolast.ragingestor.embed

import com.apptolast.ragingestor.config.RagIngestorProperties
import com.apptolast.ragingestor.git.DocIngester
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Chunkea markdown por secciones (## headers), computa embeddings y upserts
 * a pgvector. Cada chunk se persiste con (path, section, sha) para citation.
 *
 * Phase 1: implementación simplificada — chunking por section + truncate a
 * `chunkTokens`. Phase 2 podemos añadir overlap inteligente.
 */
@Component
class MarkdownDocIngester(
    private val properties: RagIngestorProperties,
    // private val embedder: EmbeddingClient,  // TODO: Spring AI EmbeddingClient
    // private val vectorStore: VectorStore,  // TODO: pgvector adapter
) : DocIngester {

    private val log = LoggerFactory.getLogger(MarkdownDocIngester::class.java)

    override fun ingest(relativePath: String, sha: String) {
        val absolute = Paths.get(properties.workdir, relativePath)
        if (!Files.exists(absolute)) {
            log.debug("file removed since fetch, skipping: {}", relativePath)
            // TODO: soft-delete chunks for this path
            return
        }

        val content = Files.readString(absolute)
        val chunks = chunkMarkdown(content, relativePath, sha)
        log.info("ingested {} chunks from {} @ {}", chunks.size, relativePath, sha)

        // TODO Phase 3:
        //   val embeddings = embedder.embed(chunks.map(Chunk::body))
        //   vectorStore.upsert(chunks.zip(embeddings))
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
        s.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "untitled" }
}
